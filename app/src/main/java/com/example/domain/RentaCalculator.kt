package com.example.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Motor tributario de mayor valor para enajenación de acciones/ETF en Chile (proyecto "Rinde").
 *
 * Hace lo que casi nadie hace DURANTE el año (de ahí el diferenciador): empareja cada venta con
 * sus lotes de compra por **FIFO**, **reajusta el costo por IPC** (mes anterior a la compra → mes
 * anterior a la venta) y clasifica el resultado:
 *  - **Art. 107 LIR (Ley 21.420):** impuesto único [RentaParams.art107Rate] sobre el mayor valor positivo.
 *  - **Régimen general:** bajo 10 UTA = ingreso no renta (se informa, no se grava); sobre = afecto a IGC.
 *
 * Objeto PURO y testeable: cero dependencias de Android/Room (igual que `InvestmentCalculator`).
 * Solo `kotlin.*` y `java.math.BigDecimal`/`RoundingMode` (válidos en JVM y Android sin desugaring,
 * ya en uso en util/FormatUtils.kt). Todas las cifras vienen por parámetro (seed); nunca se
 * hardcodean. Protegido contra división por cero, cantidades no positivas, IPC faltante y entradas
 * no finitas (NaN/Infinity), siguiendo la convención de `FinanceCalculator.ratio`/`Clp.sanitize`:
 * ante un valor inseguro se sanea a un fallback, jamás se propaga.
 *
 * FIXES de auditoría incorporados:
 *  - [reajusteFactor] con **piso 1.0** (la corrección monetaria LIR no deflacta) y guards isFinite.
 *  - [computeYear]: **estado FIFO global** entre ventas (un lote no se consume dos veces) y
 *    evaluación del **umbral 10 UTA ANUAL** sobre el neto del régimen general.
 *  - **Pérdidas** clasificadas como [RentaRegime.PERDIDA] (no como "ingreso no renta") y neteadas
 *    solo dentro de su propio pool (Art.107 vs régimen general).
 *  - **Cobertura parcial:** proceeds prorrateado a la cantidad calzada (no inflar impuesto) y
 *    [RentaResult.uncoveredQuantity] poblado.
 *  - **Redondeo a peso entero** ([Clp.round]) solo en los campos monetarios FINALES de salida.
 *
 * NO inventa un costo cero cuando faltan lotes (sesgaría el impuesto al alza): marca
 * [RentaRegime.SIN_COSTO].
 */
object RentaCalculator {

    /** Disclaimer reexportado para conveniencia de los consumidores. */
    const val DISCLAIMER: String = RentaDisclaimer.TEXT

    /** Épsilon de cantidad para cortes FIFO con cantidades fraccionarias de ETF. */
    private const val EPS: Double = 1e-9

    // -----------------------------------------------------------------------------------------
    // Reajuste IPC
    // -----------------------------------------------------------------------------------------

    /**
     * Factor de reajuste IPC entre el mes anterior a [buy] y el mes anterior a [sale]:
     * `IPC(mes previo a venta) / IPC(mes previo a compra)`. [ipcIndex] mapea "YYYY-MM" → índice
     * IPC (INE).
     *
     * FIX auditoría: **piso 1.0** (la corrección monetaria de la LIR no deflacta el costo) y guards
     * `isFinite`. Devuelve 1.0 si falta un índice, la base es ≤ 0 o el cociente no es finito.
     *
     * @param roundTo3 si true (default), redondea el factor a 3 decimales (réplica de la tabla del
     *   SII) **antes** de aplicar el piso 1.0.
     * @return factor garantizado finito y >= 1.0.
     */
    fun reajusteFactor(
        ipcIndex: Map<String, Double>,
        buy: YearMonth,
        sale: YearMonth,
        roundTo3: Boolean = true,
    ): Double {
        val from = ipcIndex[buy.prev().key]
        val to = ipcIndex[sale.prev().key]
        if (from == null || to == null || from <= 0.0 || to <= 0.0 ||
            !from.isFinite() || !to.isFinite()
        ) {
            return 1.0
        }
        var factor = to / from
        if (!factor.isFinite()) return 1.0
        if (roundTo3) {
            factor = BigDecimal(factor).setScale(3, RoundingMode.HALF_UP).toDouble()
        }
        return maxOf(1.0, factor)
    }

    // -----------------------------------------------------------------------------------------
    // Cálculo de UNA venta (compatible con la firma histórica)
    // -----------------------------------------------------------------------------------------

    /**
     * Calcula el mayor valor de UNA venta contra los lotes dados (FIFO). COMPATIBLE con la firma
     * histórica: los [buys] son los lotes disponibles para ESTA venta (en [computeYear] se pasan los
     * lotes con su cantidad remanente, de modo que un lote no se cuente dos veces).
     *
     * FIXES: guards isFinite en proceeds/costo/ganancia; pérdida → [RentaRegime.PERDIDA]; redondeo a
     * peso en los campos finales; cobertura parcial → proceeds y comisión de venta **prorrateados a
     * la cantidad calzada** (no se infla el impuesto sobre la porción descubierta).
     */
    fun computeSale(
        buys: List<BuyLot>,
        sale: SaleEvent,
        ipcIndex: Map<String, Double>,
        params: RentaParams,
    ): RentaResult {
        // Cantidad de venta saneada (no negativa, finita).
        val saleQty = Clp.sanitize(sale.quantity).let { if (it < 0.0) 0.0 else it }
        val saleUnit = Clp.sanitize(sale.unitPrice)
        val saleComm = Clp.sanitize(sale.commission)

        // --- Calce FIFO sobre los lotes del mismo ticker, del más antiguo al más nuevo ---
        var remaining = saleQty
        var adjustedCostRaw = 0.0
        var matched = 0.0
        val lots = buys
            .withIndex()
            .filter { it.value.ticker == sale.ticker && it.value.quantity > EPS }
            .sortedWith(
                compareBy({ it.value.date.year }, { it.value.date.month }, { it.index })
            ) // FIFO con desempate estable por orden de inserción (reproducibilidad intra-mes)

        for ((_, lot) in lots) {
            if (remaining <= EPS) break
            val take = minOf(remaining, lot.quantity)
            if (take <= EPS) continue
            val commissionPortion =
                if (lot.quantity > EPS) Clp.sanitize(lot.commission) * (take / lot.quantity) else 0.0
            val rawCost = take * Clp.sanitize(lot.unitPrice) + commissionPortion
            adjustedCostRaw += rawCost * reajusteFactor(ipcIndex, lot.date, sale.date)
            remaining -= take
            matched += take
        }

        // --- Sin base de costo: NO inventamos costo cero (subiría el impuesto). Se marca. ---
        if (matched <= EPS) {
            val proceedsFull = Clp.round(saleQty * saleUnit - saleComm)
            return RentaResult(
                ticker = sale.ticker,
                proceeds = proceedsFull,
                adjustedCost = 0.0,
                capitalGain = 0.0,
                regime = RentaRegime.SIN_COSTO,
                taxEstimate = null,
                belowThreshold = false,
                matchedQuantity = 0.0,
                uncoveredQuantity = Clp.sanitize(saleQty),
                note = "Sin lotes de compra para emparejar: registra la compra para calcular el costo tributario.",
            )
        }

        // --- Proceeds prorrateado a la cantidad calzada (cobertura parcial no infla el impuesto) ---
        val matchedFraction = if (saleQty > EPS) (matched / saleQty) else 1.0
        val proceedsEffectiveRaw = (matched * saleUnit) - (saleComm * matchedFraction)
        val proceeds = Clp.sanitize(proceedsEffectiveRaw)
        val adjustedCost = Clp.sanitize(adjustedCostRaw)
        val gain = proceeds - adjustedCost

        val uncovered = if (remaining > EPS) remaining else 0.0
        val partialNote = if (uncovered > EPS)
            "Venta parcialmente cubierta: faltan lotes para %.4f unidades (impuesto estimado solo sobre lo calzado). ".format(uncovered)
        else ""

        // Campos monetarios finales redondeados a peso entero.
        val proceedsR = Clp.round(proceeds)
        val adjustedCostR = Clp.round(adjustedCost)
        val gainR = Clp.round(gain)

        return when {
            // --- Pérdida: NO es ingreso no renta. Netea dentro de su pool en computeYear. ---
            gain < 0.0 -> RentaResult(
                ticker = sale.ticker,
                proceeds = proceedsR,
                adjustedCost = adjustedCostR,
                capitalGain = gainR,
                regime = RentaRegime.PERDIDA,
                taxEstimate = 0.0,
                belowThreshold = false,
                matchedQuantity = matched,
                uncoveredQuantity = uncovered,
                note = partialNote + "Mayor valor negativo (pérdida): netea contra ganancias del mismo régimen en el año.",
            )

            sale.isArt107 -> RentaResult(
                ticker = sale.ticker,
                proceeds = proceedsR,
                adjustedCost = adjustedCostR,
                capitalGain = gainR,
                regime = RentaRegime.ART_107,
                taxEstimate = Clp.round(maxOf(0.0, gain) * params.art107Rate),
                belowThreshold = false,
                matchedQuantity = matched,
                uncoveredQuantity = uncovered,
                note = partialNote + "Impuesto único Art. 107 (${(params.art107Rate * 100).toInt()}%) sobre el mayor valor positivo.",
            )

            // OJO: el umbral 10 UTA es ANUAL (ver computeYear). En la venta individual se mantiene la
            // clasificación histórica como referencia, pero la decisión vinculante es la agregada.
            gain <= params.noRentaThresholdClp -> RentaResult(
                ticker = sale.ticker,
                proceeds = proceedsR,
                adjustedCost = adjustedCostR,
                capitalGain = gainR,
                regime = RentaRegime.INGRESO_NO_RENTA,
                taxEstimate = 0.0,
                belowThreshold = true,
                matchedQuantity = matched,
                uncoveredQuantity = uncovered,
                note = partialNote + "Bajo el umbral de 10 UTA (referencia individual; el umbral se evalúa sobre el neto ANUAL del régimen general).",
            )

            else -> RentaResult(
                ticker = sale.ticker,
                proceeds = proceedsR,
                adjustedCost = adjustedCostR,
                capitalGain = gainR,
                regime = RentaRegime.REGIMEN_GENERAL,
                taxEstimate = null,
                belowThreshold = false,
                matchedQuantity = matched,
                uncoveredQuantity = uncovered,
                note = partialNote + "Sobre 10 UTA: afecto a Impuesto Global Complementario (la estimación requiere tu tramo).",
            )
        }
    }

    // -----------------------------------------------------------------------------------------
    // DRIVER ANUAL: FIFO global con estado + umbral 10 UTA agregado
    // -----------------------------------------------------------------------------------------

    /**
     * Procesa TODAS las ventas del año con **estado FIFO global**: ordena las ventas por fecha y
     * consume los lotes una sola vez (un lote NO se cuenta dos veces entre ventas). Netea pérdidas
     * dentro de cada régimen (pools Art.107 y régimen general separados) y evalúa el umbral de
     * **10 UTA sobre el neto ANUAL del régimen general** (no operación por operación).
     *
     * Reglas del agregado:
     *  - `art107NetGain` = Σ mayores valores Art.107 (las pérdidas Art.107 netean SOLO aquí).
     *  - `art107Tax` = `max(0, art107NetGain) * art107Rate`.
     *  - `generalNetGain` = Σ ganancias − Σ pérdidas del régimen general.
     *  - Si `generalNetGain <= umbral` → `generalTaxableBase = 0` y cada venta general queda
     *    `belowThreshold = true`. Si `>` → `generalTaxableBase = generalNetGain` (TODO afecto a IGC,
     *    no solo el exceso) y `belowThreshold = false`.
     *
     * @return resumen anual con el detalle por venta (ya con FIFO global aplicado) más los agregados.
     */
    fun computeYear(
        buys: List<BuyLot>,
        sales: List<SaleEvent>,
        ipcIndex: Map<String, Double>,
        params: RentaParams,
    ): RentaYearSummary {
        // Estado FIFO global: cantidad y comisión remanentes por lote (índice estable de inserción).
        data class LotState(
            val ref: BuyLot,
            val order: Int,
            var qty: Double,
            var commPerUnit: Double,
        )

        val state: List<LotState> = buys.withIndex().map { (i, lot) ->
            val q = Clp.sanitize(lot.quantity)
            val cpu = if (q > EPS) Clp.sanitize(lot.commission) / q else 0.0
            LotState(ref = lot, order = i, qty = if (q < 0.0) 0.0 else q, commPerUnit = cpu)
        }

        val orderedSales = sales.sortedWith(
            compareBy({ it.date.year }, { it.date.month })
        )

        // Cada resultado se conserva junto a su venta original para enrutar pérdidas al pool
        // correcto (Art.107 vs régimen general) sin depender del texto de la nota.
        val perSaleWithEvent = ArrayList<Pair<RentaResult, SaleEvent>>(orderedSales.size)

        for (sale in orderedSales) {
            // Lotes disponibles para ESTA venta, reconstruidos desde el remanente vivo.
            val available = state
                .filter { it.ref.ticker == sale.ticker && it.qty > EPS }
                .sortedWith(compareBy({ it.ref.date.year }, { it.ref.date.month }, { it.order }))
                .map {
                    BuyLot(
                        ticker = it.ref.ticker,
                        date = it.ref.date,
                        quantity = it.qty,
                        unitPrice = it.ref.unitPrice,
                        commission = it.commPerUnit * it.qty, // comisión prorrateada al remanente
                    )
                }

            val result = computeSale(available, sale, ipcIndex, params)
            perSaleWithEvent.add(result to sale)

            // Descontar del estado global lo efectivamente calzado (mismo orden FIFO).
            var toConsume = result.matchedQuantity
            for (ls in state.filter { it.ref.ticker == sale.ticker && it.qty > EPS }
                .sortedWith(compareBy({ it.ref.date.year }, { it.ref.date.month }, { it.order }))) {
                if (toConsume <= EPS) break
                val take = minOf(toConsume, ls.qty)
                ls.qty -= take
                toConsume -= take
            }
        }

        // --- Agregación por pools (pérdidas netean SOLO dentro de su propio régimen) ---
        var art107Net = 0.0
        var generalGross = 0.0
        var generalNet = 0.0
        for ((r, ev) in perSaleWithEvent) {
            val gain = Clp.sanitize(r.capitalGain)
            when (r.regime) {
                RentaRegime.SIN_COSTO -> { /* no aporta a base; requiere registrar costo */ }
                RentaRegime.ART_107 -> art107Net += gain
                RentaRegime.PERDIDA -> {
                    // Enruta por la naturaleza de la venta original, no por el texto de la nota.
                    if (ev.isArt107) art107Net += gain else generalNet += gain
                }
                RentaRegime.INGRESO_NO_RENTA, RentaRegime.REGIMEN_GENERAL -> {
                    if (gain > 0.0) generalGross += gain
                    generalNet += gain
                }
            }
        }
        val perSale = perSaleWithEvent.map { it.first }

        val art107Tax = Clp.round(maxOf(0.0, art107Net) * params.art107Rate)
        val threshold = params.noRentaThresholdClp
        val belowThreshold = generalNet <= threshold
        val taxableBase = if (belowThreshold) 0.0 else generalNet

        // Repintar belowThreshold de cada venta del régimen general según la decisión ANUAL.
        val reconciledPerSale = perSale.map { r ->
            if (r.regime == RentaRegime.REGIMEN_GENERAL || r.regime == RentaRegime.INGRESO_NO_RENTA) {
                r.copy(belowThreshold = belowThreshold)
            } else r
        }

        return RentaYearSummary(
            perSale = reconciledPerSale,
            art107NetGain = Clp.round(art107Net),
            art107Tax = art107Tax,
            generalGrossGain = Clp.round(generalGross),
            generalNetGain = Clp.round(generalNet),
            generalBelowThreshold = belowThreshold,
            generalTaxableBase = Clp.round(taxableBase),
            noRentaThresholdClp = threshold,
        )
    }

    // -----------------------------------------------------------------------------------------
    // Conciliación
    // -----------------------------------------------------------------------------------------

    /** Conciliación local contra lo informado por un tercero (corredora / propuesta del SII). */
    fun reconcile(ours: Map<String, Double>, reported: Map<String, Double>): List<ReconItem> {
        val tickers = (ours.keys + reported.keys).toSortedSet()
        return tickers.map { ReconItem(it, ours[it] ?: 0.0, reported[it] ?: 0.0) }
    }
}
