package com.example.domain

/**
 * Motor tributario de mayor valor para enajenación de acciones/ETF en Chile (proyecto "Rinde").
 *
 * Hace lo que casi nadie hace DURANTE el año (de ahí el diferenciador): empareja cada venta con
 * sus lotes de compra por **FIFO**, **reajusta el costo por IPC** (mes anterior a la compra → mes
 * anterior a la venta) y clasifica el resultado:
 *  - **Art. 107 LIR (Ley 21.420):** impuesto único [RentaParams.art107Rate] sobre el mayor valor positivo.
 *  - **Régimen general:** bajo 10 UTA = ingreso no renta (se informa, no se grava); sobre = afecto a IGC.
 *
 * Objeto PURO y testeable: cero dependencias de Android/Room (igual que [InvestmentCalculator]).
 * Todas las cifras vienen por parámetro. Protegido contra división por cero, cantidades no
 * positivas e IPC faltante. NO inventa un costo cero cuando faltan lotes (sesgaría el impuesto
 * al alza): marca [RentaRegime.SIN_COSTO].
 */
object RentaCalculator {

    /**
     * Factor de reajuste IPC entre el mes anterior a [buy] y el mes anterior a [sale].
     * [ipcIndex] mapea "YYYY-MM" → índice IPC (INE). Si falta un mes o el índice base es 0,
     * el factor es 1.0 (sin reajuste) en vez de fallar.
     */
    fun reajusteFactor(ipcIndex: Map<String, Double>, buy: YearMonth, sale: YearMonth): Double {
        val from = ipcIndex[buy.prev().key]
        val to = ipcIndex[sale.prev().key]
        if (from == null || to == null || from <= 0.0) return 1.0
        return to / from
    }

    /** Calcula el mayor valor de una venta contra sus lotes de compra (FIFO). */
    fun computeSale(
        buys: List<BuyLot>,
        sale: SaleEvent,
        ipcIndex: Map<String, Double>,
        params: RentaParams,
    ): RentaResult {
        val proceeds = sale.quantity * sale.unitPrice - sale.commission

        var remaining = sale.quantity
        var adjustedCost = 0.0
        var matched = 0.0
        val lots = buys
            .filter { it.ticker == sale.ticker && it.quantity > 0.0 }
            .sortedWith(compareBy({ it.date.year }, { it.date.month })) // FIFO: el más antiguo primero

        for (lot in lots) {
            if (remaining <= 0.0) break
            val take = minOf(remaining, lot.quantity)
            val commissionPortion = if (lot.quantity > 0.0) lot.commission * (take / lot.quantity) else 0.0
            val rawCost = take * lot.unitPrice + commissionPortion
            adjustedCost += rawCost * reajusteFactor(ipcIndex, lot.date, sale.date)
            remaining -= take
            matched += take
        }

        // Sin base de costo: NO inventamos costo cero (subiría el impuesto). Se marca para que el
        // usuario registre la compra que falta.
        if (matched <= 0.0) {
            return RentaResult(
                ticker = sale.ticker, proceeds = proceeds, adjustedCost = 0.0, capitalGain = 0.0,
                regime = RentaRegime.SIN_COSTO, taxEstimate = null, belowThreshold = false,
                matchedQuantity = 0.0,
                note = "Sin lotes de compra para emparejar: registra la compra para calcular el costo tributario.",
            )
        }

        val gain = proceeds - adjustedCost
        val partialNote = if (remaining > 0.0)
            "Venta parcialmente cubierta: faltan lotes para %.2f unidades. ".format(remaining) else ""

        return when {
            sale.isArt107 -> RentaResult(
                ticker = sale.ticker, proceeds = proceeds, adjustedCost = adjustedCost, capitalGain = gain,
                regime = RentaRegime.ART_107,
                taxEstimate = maxOf(0.0, gain) * params.art107Rate,
                belowThreshold = false, matchedQuantity = matched,
                note = partialNote + "Impuesto único Art. 107 (${(params.art107Rate * 100).toInt()}%) sobre el mayor valor positivo.",
            )
            gain <= params.noRentaThresholdClp -> RentaResult(
                ticker = sale.ticker, proceeds = proceeds, adjustedCost = adjustedCost, capitalGain = gain,
                regime = RentaRegime.INGRESO_NO_RENTA,
                taxEstimate = 0.0, belowThreshold = true, matchedQuantity = matched,
                note = partialNote + "Bajo el umbral de 10 UTA: ingreso no renta (se informa, no se grava).",
            )
            else -> RentaResult(
                ticker = sale.ticker, proceeds = proceeds, adjustedCost = adjustedCost, capitalGain = gain,
                regime = RentaRegime.REGIMEN_GENERAL,
                taxEstimate = null, belowThreshold = false, matchedQuantity = matched,
                note = partialNote + "Sobre 10 UTA: afecto a Impuesto Global Complementario (la estimación requiere tu tramo).",
            )
        }
    }

    /** Conciliación local contra lo informado por un tercero (corredora / propuesta del SII). */
    fun reconcile(ours: Map<String, Double>, reported: Map<String, Double>): List<ReconItem> {
        val tickers = (ours.keys + reported.keys).toSortedSet()
        return tickers.map { ReconItem(it, ours[it] ?: 0.0, reported[it] ?: 0.0) }
    }
}
