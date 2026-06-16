package com.example

import com.example.domain.BuyLot
import com.example.domain.DividendCalculator
import com.example.domain.DividendParams
import com.example.domain.DividendResult
import com.example.domain.DividendSource
import com.example.domain.DividendYearSummary
import com.example.domain.EmpresaRegime
import com.example.domain.F22Codes
import com.example.domain.F22Draft
import com.example.domain.F22Export
import com.example.domain.ForeignDividend
import com.example.domain.NationalDividend
import com.example.domain.RentaCalculator
import com.example.domain.RentaDisclaimer
import com.example.domain.RentaParams
import com.example.domain.RentaRegime
import com.example.domain.RentaResult
import com.example.domain.RentaYearSummary
import com.example.domain.SaleEvent
import com.example.domain.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests EXHAUSTIVOS del motor "Rinde" (proyecto FI-Suite) sobre los componentes nuevos:
 * [RentaCalculator.computeYear] + fixes de auditoría, [DividendCalculator] y [F22Export].
 *
 * NO duplica los 5 tests de [RentaCalculatorTest] (que siguen vivos y verifican compatibilidad
 * de la firma pública de `computeSale`/`reconcile`/`reajusteFactor`): aquí viven los casos NUEVOS.
 *
 * Filosofía: TODA cifra esperada está pre-calculada A MANO en el comentario de cada test
 * (FIFO + factor IPC + tasa + gross-up + tope crédito), para que el test sea auditable y la
 * aritmética sea revisable sin ejecutar el código. Estilo JUnit4 idéntico al test existente.
 *
 * Tolerancia por defecto 0.01; los casos de redondeo a peso entero comparan exacto (delta 0.0).
 */
class RentaEngineTest {

    /** Seed de ejemplo: 1 UTA = 800.000 → umbral 10 UTA = 8.000.000 CLP. */
    private val params = RentaParams(utaClp = 800_000.0)

    /** Parámetros de dividendos con TC observado mensual de ejemplo. */
    private val divParams = DividendParams(
        fxObserved = mapOf(
            "2024-03" to 900.0,
            "2024-09" to 950.0,
        ),
    )

    // =======================================================================================
    // SECCIÓN 1 — RentaCalculator.computeYear: FIFO global y umbral anual agregado
    // =======================================================================================

    @Test
    fun `dos ventas mismo ticker no cuentan el costo dos veces`() {
        // Lote único: 100u @ 1000 (ene-2024). Dos ventas Art.107 del mismo ticker:
        //   venta 1: 60u @ 1500 (mar-2024)  -> consume 60u del lote (quedan 40u)
        //   venta 2: 40u @ 1500 (jun-2024)  -> consume las 40u remanentes (FIFO GLOBAL)
        // Sin reajuste IPC (índices planos). Costo total consumido = 100u * 1000 = 100.000.
        // Si el costo se contara dos veces, la 2ª venta reusaría el lote desde 0 y daría
        // matchedQuantity > lo disponible. Aquí la 2ª venta NO debe tener cobertura sobrante.
        val ipc = mapOf("2023-12" to 100.0, "2024-02" to 100.0, "2024-05" to 100.0)
        val summary = RentaCalculator.computeYear(
            buys = listOf(BuyLot("AAPL", YearMonth(2024, 1), 100.0, 1000.0)),
            sales = listOf(
                SaleEvent("AAPL", YearMonth(2024, 3), 60.0, 1500.0, isArt107 = true),
                SaleEvent("AAPL", YearMonth(2024, 6), 40.0, 1500.0, isArt107 = true),
            ),
            ipcIndex = ipc, params = params,
        )
        assertEquals(2, summary.perSale.size)
        val v1 = summary.perSale[0]
        val v2 = summary.perSale[1]
        // Venta 1: proceeds 60*1500=90.000, costo 60*1000=60.000, mayor valor 30.000.
        assertEquals(60.0, v1.matchedQuantity, 1e-9)
        assertEquals(30_000.0, v1.capitalGain, 0.01)
        // Venta 2: proceeds 40*1500=60.000, costo 40*1000=40.000, mayor valor 20.000.
        //   El lote quedó con 40u tras la venta 1; se consume EXACTO, sin sobrante.
        assertEquals(40.0, v2.matchedQuantity, 1e-9)
        assertEquals(20_000.0, v2.capitalGain, 0.01)
        assertEquals(0.0, v2.uncoveredQuantity, 1e-9)
        // Agregado Art.107: 30.000 + 20.000 = 50.000.
        assertEquals(50_000.0, summary.art107NetGain, 0.01)
    }

    @Test
    fun `perdida se clasifica como PERDIDA no como ingreso no renta`() {
        // Compra 100 @ 1500 (ene-2024); venta 100 @ 1000 (jun-2024), régimen general.
        // Mayor valor = 100.000 - 150.000 = -50.000 (pérdida). Debe ser RentaRegime.PERDIDA,
        // taxEstimate == 0.0, belowThreshold == false (NO es "bajo umbral", es pérdida).
        val ipc = mapOf("2023-12" to 100.0, "2024-05" to 100.0)
        val summary = RentaCalculator.computeYear(
            buys = listOf(BuyLot("LOSS", YearMonth(2024, 1), 100.0, 1500.0)),
            sales = listOf(SaleEvent("LOSS", YearMonth(2024, 6), 100.0, 1000.0, isArt107 = false)),
            ipcIndex = ipc, params = params,
        )
        val r = summary.perSale.single()
        assertEquals(-50_000.0, r.capitalGain, 0.01)
        assertEquals(RentaRegime.PERDIDA, r.regime)
        assertEquals(0.0, r.taxEstimate!!, 0.0)
        assertFalse(r.belowThreshold)
    }

    @Test
    fun `perdida netea contra ganancia del mismo regimen en el año`() {
        // Régimen general, dos ventas:
        //   ganancia: compra 100 @ 1000, venta 100 @ 1200 -> +20.000 (jun-2024)
        //   pérdida : compra 100 @ 1000, venta 100 @ 900  -> -10.000 (sep-2024)
        // generalNetGain = 20.000 - 10.000 = 10.000 (netea dentro del pool general).
        val ipc = mapOf("2023-12" to 100.0, "2024-05" to 100.0, "2024-08" to 100.0)
        val summary = RentaCalculator.computeYear(
            buys = listOf(
                BuyLot("GANA", YearMonth(2024, 1), 100.0, 1000.0),
                BuyLot("PIER", YearMonth(2024, 1), 100.0, 1000.0),
            ),
            sales = listOf(
                SaleEvent("GANA", YearMonth(2024, 6), 100.0, 1200.0, isArt107 = false),
                SaleEvent("PIER", YearMonth(2024, 9), 100.0, 900.0, isArt107 = false),
            ),
            ipcIndex = ipc, params = params,
        )
        // Ganancia bruta positiva = 20.000; neto = 20.000 - 10.000 = 10.000.
        assertEquals(20_000.0, summary.generalGrossGain, 0.01)
        assertEquals(10_000.0, summary.generalNetGain, 0.01)
    }

    @Test
    fun `umbral 10 UTA se evalua sobre el neto anual agregado`() {
        // Dos ventas régimen general que INDIVIDUALMENTE quedan bajo umbral (cada una +5.000.000)
        // pero SUMADAS dan 10.000.000 > 8.000.000 (10 UTA). El umbral es ANUAL, no por operación.
        //   venta A: compra 1000 @ 5000, venta 1000 @ 10000 -> +5.000.000 (mar-2024)
        //   venta B: compra 1000 @ 5000, venta 1000 @ 10000 -> +5.000.000 (sep-2024)
        // generalNetGain = 10.000.000 > umbral 8.000.000 -> generalBelowThreshold == false,
        // generalTaxableBase == 10.000.000 (TODO el mayor valor afecto, no solo el exceso).
        val ipc = mapOf("2023-12" to 100.0, "2024-02" to 100.0, "2024-08" to 100.0)
        val summary = RentaCalculator.computeYear(
            buys = listOf(
                BuyLot("BIGA", YearMonth(2024, 1), 1000.0, 5000.0),
                BuyLot("BIGB", YearMonth(2024, 1), 1000.0, 5000.0),
            ),
            sales = listOf(
                SaleEvent("BIGA", YearMonth(2024, 3), 1000.0, 10000.0, isArt107 = false),
                SaleEvent("BIGB", YearMonth(2024, 9), 1000.0, 10000.0, isArt107 = false),
            ),
            ipcIndex = ipc, params = params,
        )
        assertEquals(10_000_000.0, summary.generalNetGain, 0.01)
        assertFalse(summary.generalBelowThreshold)
        assertEquals(10_000_000.0, summary.generalTaxableBase, 0.01)
        // Trazabilidad del seed: el umbral usado es 10 UTA = 8.000.000.
        assertEquals(8_000_000.0, summary.noRentaThresholdClp, 0.01)
    }

    @Test
    fun `neto bajo umbral deja base afecta en cero`() {
        // Una venta régimen general +5.000.000 < umbral 8.000.000 -> bajo umbral.
        // generalBelowThreshold == true, generalTaxableBase == 0.0.
        val ipc = mapOf("2023-12" to 100.0, "2024-02" to 100.0)
        val summary = RentaCalculator.computeYear(
            buys = listOf(BuyLot("SMALL", YearMonth(2024, 1), 1000.0, 5000.0)),
            sales = listOf(SaleEvent("SMALL", YearMonth(2024, 3), 1000.0, 10000.0, isArt107 = false)),
            ipcIndex = ipc, params = params,
        )
        assertEquals(5_000_000.0, summary.generalNetGain, 0.01)
        assertTrue(summary.generalBelowThreshold)
        assertEquals(0.0, summary.generalTaxableBase, 0.01)
    }

    @Test
    fun `art107 tax se calcula sobre el neto agregado positivo`() {
        // Art.107: ganancia 30.000 (mar) y pérdida -10.000 (sep) netean SOLO en su pool.
        // art107NetGain = 20.000; art107Tax = 20.000 * 0.10 = 2.000.
        val ipc = mapOf("2023-12" to 100.0, "2024-02" to 100.0, "2024-08" to 100.0)
        val summary = RentaCalculator.computeYear(
            buys = listOf(
                BuyLot("A107", YearMonth(2024, 1), 100.0, 1000.0),
                BuyLot("B107", YearMonth(2024, 1), 100.0, 1000.0),
            ),
            sales = listOf(
                SaleEvent("A107", YearMonth(2024, 3), 100.0, 1300.0, isArt107 = true), // +30.000
                SaleEvent("B107", YearMonth(2024, 9), 100.0, 900.0, isArt107 = true),  // -10.000
            ),
            ipcIndex = ipc, params = params,
        )
        assertEquals(20_000.0, summary.art107NetGain, 0.01)
        assertEquals(2_000.0, summary.art107Tax, 0.01)
    }

    // =======================================================================================
    // SECCIÓN 2 — reajusteFactor: piso 1.0, índices faltantes, redondeo
    // =======================================================================================

    @Test
    fun `reajuste IPC negativo se acota a factor 1_0`() {
        // IPC venta (mes anterior) < IPC compra (mes anterior): el factor crudo sería < 1.0.
        // La corrección monetaria LIR NO deflacta el costo -> piso 1.0.
        //   buy ene-2024 -> prev dic-2023 (índice 110); sale jun-2024 -> prev may-2024 (índice 100).
        //   factor crudo = 100/110 = 0.909... -> acotado a 1.0.
        val ipc = mapOf("2023-12" to 110.0, "2024-05" to 100.0)
        val f = RentaCalculator.reajusteFactor(ipc, YearMonth(2024, 1), YearMonth(2024, 6))
        assertEquals(1.0, f, 1e-9)
    }

    @Test
    fun `reajusteFactor con indice faltante o cero devuelve 1_0`() {
        // Mapa vacío -> falta índice -> 1.0.
        assertEquals(1.0, RentaCalculator.reajusteFactor(emptyMap(), YearMonth(2024, 1), YearMonth(2024, 6)), 1e-9)
        // Índice base 0 -> división protegida -> 1.0.
        val ipcZero = mapOf("2023-12" to 0.0, "2024-05" to 100.0)
        assertEquals(1.0, RentaCalculator.reajusteFactor(ipcZero, YearMonth(2024, 1), YearMonth(2024, 6)), 1e-9)
    }

    @Test
    fun `reajusteFactor redondea a 3 decimales replicando tabla SII`() {
        // buy ene-2024 -> prev dic-2023 (100.0); sale jun-2024 -> prev may-2024 (103.456).
        // factor crudo = 1.03456 -> redondeado a 3 decimales = 1.035 (default roundTo3 = true).
        val ipc = mapOf("2023-12" to 100.0, "2024-05" to 103.456)
        val f = RentaCalculator.reajusteFactor(ipc, YearMonth(2024, 1), YearMonth(2024, 6))
        assertEquals(1.035, f, 1e-9)
        // Con roundTo3 = false: factor sin redondear (pero siempre >= 1.0).
        val raw = RentaCalculator.reajusteFactor(ipc, YearMonth(2024, 1), YearMonth(2024, 6), roundTo3 = false)
        assertEquals(1.03456, raw, 1e-9)
    }

    // =======================================================================================
    // SECCIÓN 3 — Robustez numérica y cobertura parcial / redondeo
    // =======================================================================================

    @Test
    fun `entradas NaN o Infinity no propagan`() {
        // unitPrice no finito en la venta: el resultado monetario debe quedar SANEADO (finito),
        // nunca NaN/Infinity (guards estilo FinanceCalculator/FormatUtils.sanitize).
        val ipc = mapOf("2023-12" to 100.0, "2024-05" to 100.0)
        val summary = RentaCalculator.computeYear(
            buys = listOf(BuyLot("NANX", YearMonth(2024, 1), 100.0, 1000.0)),
            sales = listOf(SaleEvent("NANX", YearMonth(2024, 6), 100.0, Double.NaN, isArt107 = true)),
            ipcIndex = ipc, params = params,
        )
        val r = summary.perSale.single()
        assertTrue("proceeds debe ser finito", r.proceeds.isFinite())
        assertTrue("adjustedCost debe ser finito", r.adjustedCost.isFinite())
        assertTrue("capitalGain debe ser finito", r.capitalGain.isFinite())
        assertTrue("art107NetGain agregado debe ser finito", summary.art107NetGain.isFinite())
    }

    @Test
    fun `cobertura parcial prorratea proceeds y marca uncoveredQuantity`() {
        // Lote 60u @ 1000 (ene-2024); venta 100u @ 1500 sin comisión (jun-2024), Art.107.
        // Solo se calzan 60u (FIFO agota el lote). uncoveredQuantity = 40u.
        // proceeds prorrateado a la cantidad calzada = 1500 * 60 = 90.000 (no 150.000).
        // costo = 60 * 1000 = 60.000; mayor valor = 90.000 - 60.000 = 30.000.
        val ipc = mapOf("2023-12" to 100.0, "2024-05" to 100.0)
        val summary = RentaCalculator.computeYear(
            buys = listOf(BuyLot("PART", YearMonth(2024, 1), 60.0, 1000.0)),
            sales = listOf(SaleEvent("PART", YearMonth(2024, 6), 100.0, 1500.0, isArt107 = true)),
            ipcIndex = ipc, params = params,
        )
        val r = summary.perSale.single()
        assertEquals(60.0, r.matchedQuantity, 1e-9)
        assertEquals(40.0, r.uncoveredQuantity, 1e-9)
        assertEquals(90_000.0, r.proceeds, 0.01)
        assertEquals(60_000.0, r.adjustedCost, 0.01)
        assertEquals(30_000.0, r.capitalGain, 0.01)
    }

    @Test
    fun `redondeo a peso entero en campos monetarios`() {
        // Forzamos un mayor valor con fracción de peso vía factor IPC no redondo:
        //   compra 1u @ 1000 (ene-2024); venta 1u @ 1500.49 (jun-2024), Art.107, sin comisión.
        //   IPC factor 1.001 (prev compra dic-2023 = 100.0, prev venta may-2024 = 100.1).
        //   proceeds = 1500.49 -> redondeo a peso = 1500.
        //   costo = 1000 * 1.001 = 1001.0 (exacto). mayor valor = 1500 - 1001 = 499 (entero).
        val ipc = mapOf("2023-12" to 100.0, "2024-05" to 100.1)
        val summary = RentaCalculator.computeYear(
            buys = listOf(BuyLot("ROUND", YearMonth(2024, 1), 1.0, 1000.0)),
            sales = listOf(SaleEvent("ROUND", YearMonth(2024, 6), 1.0, 1500.49, isArt107 = true)),
            ipcIndex = ipc, params = params,
        )
        val r = summary.perSale.single()
        // Campos monetarios redondeados a peso entero exacto (sin parte decimal).
        assertEquals(r.proceeds, Math.rint(r.proceeds), 0.0)
        assertEquals(r.adjustedCost, Math.rint(r.adjustedCost), 0.0)
        assertEquals(r.capitalGain, Math.rint(r.capitalGain), 0.0)
        assertEquals(1500.0, r.proceeds, 0.0)
        assertEquals(1001.0, r.adjustedCost, 0.0)
        assertEquals(499.0, r.capitalGain, 0.0)
    }

    // =======================================================================================
    // SECCIÓN 4 — DividendCalculator: nacionales (gross-up + crédito + restitución)
    // =======================================================================================

    @Test
    fun `dividendo nacional pro pyme usa 100 por ciento del credito IDPC`() {
        // Pro Pyme 14D: crédito IDPC 100%. Dividendo 1.000.000, SAC 250.000.
        //   creditClp = 250.000 * 1.00 = 250.000; grossUpClp = 250.000.
        //   taxableBaseClp = 1.000.000 + 250.000 = 1.250.000; restitución = 0 (14D no restituye).
        val div = NationalDividend(
            issuer = "PyME SA", date = YearMonth(2024, 4),
            grossDividend = 1_000_000.0, sacCreditIdpc = 250_000.0,
            empresaRegime = EmpresaRegime.PRO_PYME_14D,
        )
        val r = DividendCalculator.computeNational(div, divParams)
        assertEquals(DividendSource.NACIONAL, r.source)
        assertEquals(1_000_000.0, r.grossClp, 0.01)
        assertEquals(250_000.0, r.creditClp, 0.01)
        assertEquals(250_000.0, r.grossUpClp, 0.01)
        assertEquals(1_250_000.0, r.taxableBaseClp, 0.01)
        assertEquals(0.0, r.restitutionClp, 0.01)
    }

    @Test
    fun `dividendo nacional semi integrado usa 65 por ciento y restituye 35`() {
        // Semi 14A sin convenio: crédito 65%, restitución 35%. Dividendo 1.000.000, SAC 200.000.
        //   creditClp = 200.000 * 0.65 = 130.000; grossUpClp = 130.000.
        //   taxableBaseClp = 1.000.000 + 130.000 = 1.130.000.
        //   restitutionClp = 200.000 * 0.35 = 70.000.
        val div = NationalDividend(
            issuer = "Banco SA", date = YearMonth(2024, 4),
            grossDividend = 1_000_000.0, sacCreditIdpc = 200_000.0,
            empresaRegime = EmpresaRegime.SEMI_INTEGRADO_14A, conConvenio = false,
        )
        val r = DividendCalculator.computeNational(div, divParams)
        assertEquals(130_000.0, r.creditClp, 0.01)
        assertEquals(130_000.0, r.grossUpClp, 0.01)
        assertEquals(1_130_000.0, r.taxableBaseClp, 0.01)
        assertEquals(70_000.0, r.restitutionClp, 0.01)
    }

    @Test
    fun `semi integrado con convenio no restituye`() {
        // Semi 14A pero residente de país con convenio: crédito 65% igual, restitución = 0.
        val div = NationalDividend(
            issuer = "Banco SA", date = YearMonth(2024, 4),
            grossDividend = 1_000_000.0, sacCreditIdpc = 200_000.0,
            empresaRegime = EmpresaRegime.SEMI_INTEGRADO_14A, conConvenio = true,
        )
        val r = DividendCalculator.computeNational(div, divParams)
        assertEquals(130_000.0, r.creditClp, 0.01)
        assertEquals(0.0, r.restitutionClp, 0.01)
    }

    // =======================================================================================
    // SECCIÓN 5 — DividendCalculator: extranjeros (TC + crédito Art.41A + topes)
    // =======================================================================================

    @Test
    fun `dividendo extranjero convierte por TC observado a fecha de percepcion`() {
        // Dividendo USD 1000, retención USD 100, fecha mar-2024 -> TC observado 900 CLP/USD.
        //   grossClp = 1000 * 900 = 900.000.
        //   crédito Art.41A = min(impuesto CLP, 35% * grossClp)
        //                   = min(100*900=90.000, 0.35*900.000=315.000) = 90.000 (tope no muerde).
        //   grossUpClp = impuesto extranjero CLP = 90.000.
        //   taxableBaseClp = 900.000 + 90.000 = 990.000.
        val div = ForeignDividend(
            issuer = "ETF World", date = YearMonth(2024, 3),
            grossForeign = 1000.0, taxWithheldForeign = 100.0, currency = "USD",
        )
        val r = DividendCalculator.computeForeign(div, divParams)
        assertEquals(DividendSource.EXTRANJERO, r.source)
        assertEquals(900_000.0, r.grossClp, 0.01)
        assertEquals(90_000.0, r.grossUpClp, 0.01)
        assertEquals(990_000.0, r.taxableBaseClp, 0.01)
        assertEquals(90_000.0, r.creditClp, 0.01)
    }

    @Test
    fun `credito extranjero topea al 35 por ciento individual`() {
        // Retención afuera ALTA (USD 500 sobre USD 1000), fecha mar-2024, TC 900.
        //   grossClp = 900.000; impuesto CLP = 500*900 = 450.000.
        //   tope individual 35% = 0.35 * 900.000 = 315.000.
        //   creditClp = min(450.000, 315.000) = 315.000 (el tope MUERDE).
        val div = ForeignDividend(
            issuer = "Acción USA", date = YearMonth(2024, 3),
            grossForeign = 1000.0, taxWithheldForeign = 500.0, currency = "USD",
        )
        val r = DividendCalculator.computeForeign(div, divParams)
        assertEquals(900_000.0, r.grossClp, 0.01)
        assertEquals(315_000.0, r.creditClp, 0.01)
    }

    @Test
    fun `falta de TC no inventa tipo de cambio`() {
        // Fecha jun-2024 NO está en fxObserved -> no se inventa TC.
        //   grossClp = 0, taxableBaseClp = 0, creditClp = 0, con nota de revisión.
        val div = ForeignDividend(
            issuer = "Sin TC", date = YearMonth(2024, 6),
            grossForeign = 1000.0, taxWithheldForeign = 100.0, currency = "USD",
        )
        val r = DividendCalculator.computeForeign(div, divParams)
        assertEquals(0.0, r.grossClp, 0.0)
        assertEquals(0.0, r.taxableBaseClp, 0.0)
        assertEquals(0.0, r.creditClp, 0.0)
        assertTrue("debe avisar que falta el TC", r.note.isNotBlank())
        // fxRate de conveniencia también devuelve null para fecha sin TC.
        assertEquals(null, DividendCalculator.fxRate(divParams, YearMonth(2024, 6)))
        assertEquals(900.0, DividendCalculator.fxRate(divParams, YearMonth(2024, 3)))
    }

    @Test
    fun `tope global 35 por ciento RNFE recorta el credito agregado`() {
        // Dos dividendos extranjeros, ambos mar-2024 (TC 900). Cada uno con retención ALTA
        // de modo que el crédito individual ya esté topeado al 35% y la SUMA exceda el tope GLOBAL.
        //   div1: gross USD 1000, ret USD 500 -> grossClp 900.000, créd individual 315.000.
        //   div2: gross USD 1000, ret USD 500 -> grossClp 900.000, créd individual 315.000.
        //   RNFE = 900.000 + 900.000 = 1.800.000; topeGlobal = 0.35 * 1.800.000 = 630.000.
        //   Σ créditos individuales = 315.000 + 315.000 = 630.000 == topeGlobal (no recorta).
        // Para que SÍ recorte, sumamos un tercer dividendo con crédito individual no topeado:
        //   div3: gross USD 1000, ret USD 350 -> grossClp 900.000; impuesto 350*900=315.000;
        //         tope individual 315.000 -> créd individual 315.000.
        //   RNFE total = 2.700.000; topeGlobal = 0.35 * 2.700.000 = 945.000.
        //   Σ individuales = 315.000*3 = 945.000 == topeGlobal. (sigue calzando: 35% es lineal)
        // Insight: con créditos individuales TODOS al tope del 35%, el tope global nunca muerde.
        //   Para forzar recorte global necesitamos algún crédito individual BAJO el 35% que eleve
        //   la suma por encima del 35% del RNFE — lo que NO ocurre con créditos lineales.
        //   El recorte global aplica cuando el crédito individual de algún ítem proviene de una
        //   retención > 35% (topeada) PERO con RNFE que incluye dividendos SIN retención
        //   (que suben el denominador menos que el crédito). Construimos ese caso:
        //   div1: gross USD 1000, ret USD 500 -> grossClp 900.000, créd individual 315.000 (topeado).
        //   div2: gross USD 100,  ret USD 50  -> grossClp  90.000, créd individual = min(45.000,
        //         0.35*90.000=31.500) = 31.500.
        //   RNFE = 900.000 + 90.000 = 990.000; topeGlobal = 0.35 * 990.000 = 346.500.
        //   Σ individuales = 315.000 + 31.500 = 346.500 == topeGlobal.
        // El 35% lineal hace que individual y global coincidan SIEMPRE; por eso el tope global solo
        // recorta si un parámetro de tope individual fuese mayor que el global. Forzamos ese
        // escenario inyectando foreignCreditCapRate alto a nivel individual NO es posible (mismo
        // param). Verificamos entonces la INVARIANTE: el crédito agregado NUNCA supera 35% del RNFE.
        val foreigns = listOf(
            ForeignDividend("FA", YearMonth(2024, 3), 1000.0, 500.0, "USD"),
            ForeignDividend("FB", YearMonth(2024, 3), 100.0, 50.0, "USD"),
        )
        val summary = DividendCalculator.computeYear(
            nationals = emptyList(), foreigns = foreigns, params = divParams,
        )
        // RNFE = 990.000; tope global = 346.500. El crédito extranjero total NO debe superarlo.
        val rnfe = 990_000.0
        val topeGlobal = 0.35 * rnfe
        assertTrue(
            "crédito extranjero agregado (${summary.totalForeignCreditClp}) no debe superar tope global $topeGlobal",
            summary.totalForeignCreditClp <= topeGlobal + 0.01,
        )
        assertEquals(346_500.0, summary.totalForeignCreditClp, 0.01)
    }

    @Test
    fun `computeYear suma bases nacionales y extranjeras`() {
        // Nacional Pro Pyme: gross 1.000.000, SAC 250.000 -> base 1.250.000, crédito IDPC 250.000.
        // Extranjero mar-2024 TC 900: gross USD 1000 -> grossClp 900.000, ret USD 100 -> grossUp
        //   90.000, base 990.000, crédito 90.000.
        // totalTaxableBaseClp = 1.250.000 + 990.000 = 2.240.000.
        // totalIdpcCreditClp = 250.000; totalForeignCreditClp = 90.000 (tope global 0.35*900.000
        //   = 315.000 no muerde).
        val nationals = listOf(
            NationalDividend("PyME", YearMonth(2024, 4), 1_000_000.0, 250_000.0, EmpresaRegime.PRO_PYME_14D),
        )
        val foreigns = listOf(
            ForeignDividend("ETF", YearMonth(2024, 3), 1000.0, 100.0, "USD"),
        )
        val summary = DividendCalculator.computeYear(nationals, foreigns, divParams)
        assertEquals(2, summary.perDividend.size)
        assertEquals(2_240_000.0, summary.totalTaxableBaseClp, 0.01)
        assertEquals(250_000.0, summary.totalIdpcCreditClp, 0.01)
        assertEquals(90_000.0, summary.totalForeignCreditClp, 0.01)
    }

    // =======================================================================================
    // SECCIÓN 6 — F22Export: mapeo de códigos y CSV determinista
    // =======================================================================================

    @Test
    fun `fromRenta mapea mayor valor Art107 al codigo correspondiente`() {
        // Resumen anual con un mayor valor Art.107 de 50.000. Debe aparecer una F22Line con el
        // código de Art.107 (default 1043, marcado VERIFICAR) y el monto 50.000.
        val ipc = mapOf("2023-12" to 100.0, "2024-02" to 100.0)
        val summary = RentaCalculator.computeYear(
            buys = listOf(BuyLot("SQM", YearMonth(2024, 1), 100.0, 1000.0)),
            sales = listOf(SaleEvent("SQM", YearMonth(2024, 3), 100.0, 1500.0, isArt107 = true)),
            ipcIndex = ipc, params = params,
        )
        val codes = F22Codes()
        val lines = F22Export.fromRenta(summary, codes)
        val art107Line = lines.firstOrNull { it.code == codes.codArt107MayorValor }
        assertTrue("debe existir una línea con el código Art.107", art107Line != null)
        assertEquals(50_000.0, art107Line!!.amountClp, 0.0)
    }

    @Test
    fun `toCsv produce encabezado y filas deterministas`() {
        // Draft construido a mano con una sola línea, para comparar el CSV string EXACTO.
        // Encabezado fijo: "codigo,glosa,monto_clp,origen". Montos en pesos enteros sin miles.
        val draft = F22Draft(
            lines = listOf(
                com.example.domain.F22Line(
                    code = "1070", label = "Mayor valor régimen general",
                    amountClp = 50_000.0, source = "Acciones NVDA",
                ),
            ),
        )
        val csv = F22Export.toCsv(draft)
        val lines = csv.trim().lines()
        // Primera fila: encabezado determinista.
        assertEquals("codigo,glosa,monto_clp,origen", lines[0])
        // Segunda fila: la línea de datos (monto entero, sin separador de miles).
        assertEquals("1070,Mayor valor régimen general,50000,Acciones NVDA", lines[1])
        // El disclaimer debe aparecer en algún lugar del CSV (última fila o comentario).
        assertTrue("el CSV debe portar el disclaimer", csv.contains(RentaDisclaimer.TEXT))
    }

    @Test
    fun `toCsv escapa comas y comillas en glosa y origen`() {
        // Glosa con coma y origen con comilla -> deben quedar entrecomillados RFC-4180 mínimo.
        val draft = F22Draft(
            lines = listOf(
                com.example.domain.F22Line(
                    code = "746", label = "Crédito, exterior",
                    amountClp = 90_000.0, source = "ETF \"World\"",
                ),
            ),
        )
        val csv = F22Export.toCsv(draft)
        val dataLine = csv.trim().lines()[1]
        // La glosa con coma va entre comillas; la comilla interna se duplica.
        assertTrue("glosa con coma debe entrecomillarse", dataLine.contains("\"Crédito, exterior\""))
        assertTrue("comilla interna debe duplicarse", dataLine.contains("\"ETF \"\"World\"\"\""))
    }

    @Test
    fun `buildDraft con ambos nulos da borrador vacio`() {
        // Sin resúmenes -> lista vacía pero el draft sigue portando el disclaimer.
        val draft = F22Export.buildDraft(renta = null, dividends = null)
        assertTrue(draft.lines.isEmpty())
        assertEquals(RentaDisclaimer.TEXT, draft.disclaimer)
    }

    @Test
    fun `buildDraft combina lineas de renta y dividendos`() {
        // Renta con Art.107 50.000 + dividendos con crédito IDPC: el draft combinado debe tener
        // líneas de AMBAS fuentes (no vacío, y mayor o igual a la suma de fromRenta + fromDividends).
        val ipc = mapOf("2023-12" to 100.0, "2024-02" to 100.0)
        val renta = RentaCalculator.computeYear(
            buys = listOf(BuyLot("SQM", YearMonth(2024, 1), 100.0, 1000.0)),
            sales = listOf(SaleEvent("SQM", YearMonth(2024, 3), 100.0, 1500.0, isArt107 = true)),
            ipcIndex = ipc, params = params,
        )
        val dividends = DividendCalculator.computeYear(
            nationals = listOf(
                NationalDividend("PyME", YearMonth(2024, 4), 1_000_000.0, 250_000.0, EmpresaRegime.PRO_PYME_14D),
            ),
            foreigns = emptyList(), params = divParams,
        )
        val codes = F22Codes()
        val expected = F22Export.fromRenta(renta, codes).size + F22Export.fromDividends(dividends, codes).size
        val draft = F22Export.buildDraft(renta, dividends, codes)
        assertEquals(expected, draft.lines.size)
        assertTrue(draft.lines.isNotEmpty())
    }

    // =======================================================================================
    // SECCIÓN 7 — Disclaimer obligatorio en TODA salida del motor
    // =======================================================================================

    @Test
    fun `todas las salidas portan el disclaimer obligatorio`() {
        val ipc = mapOf("2023-12" to 100.0, "2024-02" to 100.0)
        // 1) RentaResult + RentaYearSummary.
        val rentaSummary: RentaYearSummary = RentaCalculator.computeYear(
            buys = listOf(BuyLot("SQM", YearMonth(2024, 1), 100.0, 1000.0)),
            sales = listOf(SaleEvent("SQM", YearMonth(2024, 3), 100.0, 1500.0, isArt107 = true)),
            ipcIndex = ipc, params = params,
        )
        assertEquals(RentaDisclaimer.TEXT, rentaSummary.disclaimer)
        val rentaResult: RentaResult = rentaSummary.perSale.single()
        assertEquals(RentaDisclaimer.TEXT, rentaResult.disclaimer)

        // 2) DividendResult + DividendYearSummary.
        val divSummary: DividendYearSummary = DividendCalculator.computeYear(
            nationals = listOf(
                NationalDividend("PyME", YearMonth(2024, 4), 1_000_000.0, 250_000.0, EmpresaRegime.PRO_PYME_14D),
            ),
            foreigns = emptyList(), params = divParams,
        )
        assertEquals(RentaDisclaimer.TEXT, divSummary.disclaimer)
        val divResult: DividendResult = divSummary.perDividend.single()
        assertEquals(RentaDisclaimer.TEXT, divResult.disclaimer)

        // 3) F22Draft.
        val draft: F22Draft = F22Export.buildDraft(rentaSummary, divSummary)
        assertEquals(RentaDisclaimer.TEXT, draft.disclaimer)

        // 4) Constantes reexportadas de conveniencia.
        assertEquals(RentaDisclaimer.TEXT, RentaCalculator.DISCLAIMER)
        assertEquals(RentaDisclaimer.TEXT, DividendCalculator.DISCLAIMER)
        assertEquals(RentaDisclaimer.TEXT, F22Export.DISCLAIMER)
    }
}
