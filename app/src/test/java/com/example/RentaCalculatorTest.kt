package com.example

import com.example.domain.BuyLot
import com.example.domain.RentaCalculator
import com.example.domain.RentaParams
import com.example.domain.RentaRegime
import com.example.domain.SaleEvent
import com.example.domain.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica el motor tributario de "Rinde" (mayor valor de acciones/ETF).
 * Cifras pre-calculadas y verificadas a mano (FIFO + reajuste IPC + Art.107 + umbral 10 UTA).
 */
class RentaCalculatorTest {

    private val params = RentaParams(utaClp = 800_000.0) // seed de ejemplo; reemplazar por UTA oficial

    @Test
    fun `Art107 con reajuste IPC aplica impuesto unico 10 por ciento`() {
        // Compra 100 @ 1000 + comisión 5000 (ene-2024); venta 100 @ 1500 - comisión 7000 (mar-2025).
        // IPC: factor = 105/100 = 1.05. Costo reajustado = 105.000 * 1.05 = 110.250.
        // Mayor valor = 143.000 - 110.250 = 32.750. Impuesto único 10% = 3.275.
        val ipc = mapOf("2023-12" to 100.0, "2025-02" to 105.0)
        val r = RentaCalculator.computeSale(
            buys = listOf(BuyLot("SQM", YearMonth(2024, 1), 100.0, 1000.0, 5000.0)),
            sale = SaleEvent("SQM", YearMonth(2025, 3), 100.0, 1500.0, 7000.0, isArt107 = true),
            ipcIndex = ipc, params = params,
        )
        assertEquals(110_250.0, r.adjustedCost, 0.01)
        assertEquals(32_750.0, r.capitalGain, 0.01)
        assertEquals(RentaRegime.ART_107, r.regime)
        assertEquals(3_275.0, r.taxEstimate!!, 0.01)
    }

    @Test
    fun `bajo 10 UTA es ingreso no renta`() {
        // Mayor valor 5.000 << umbral 10 UTA (8.000.000) -> ingreso no renta, impuesto 0.
        val ipc = mapOf("2023-12" to 100.0, "2024-12" to 100.0)
        val r = RentaCalculator.computeSale(
            buys = listOf(BuyLot("CHILE", YearMonth(2024, 1), 10.0, 1000.0)),
            sale = SaleEvent("CHILE", YearMonth(2025, 1), 10.0, 1500.0, isArt107 = false),
            ipcIndex = ipc, params = params,
        )
        assertEquals(5_000.0, r.capitalGain, 0.01)
        assertEquals(RentaRegime.INGRESO_NO_RENTA, r.regime)
        assertEquals(0.0, r.taxEstimate!!, 0.0)
        assertTrue(r.belowThreshold)
    }

    @Test
    fun `FIFO empareja venta contra dos lotes en orden`() {
        // Venta 80 contra lote1 (50 @ 1000) + lote2 (30 @ 1200) = costo 86.000.
        // Mayor valor = 120.000 - 86.000 = 34.000. Art.107 10% = 3.400.
        val ipc = mapOf("2023-12" to 100.0, "2024-05" to 100.0, "2024-12" to 100.0)
        val r = RentaCalculator.computeSale(
            buys = listOf(
                BuyLot("NVDA", YearMonth(2024, 1), 50.0, 1000.0),
                BuyLot("NVDA", YearMonth(2024, 6), 50.0, 1200.0),
            ),
            sale = SaleEvent("NVDA", YearMonth(2025, 1), 80.0, 1500.0, isArt107 = true),
            ipcIndex = ipc, params = params,
        )
        assertEquals(86_000.0, r.adjustedCost, 0.01)
        assertEquals(34_000.0, r.capitalGain, 0.01)
        assertEquals(3_400.0, r.taxEstimate!!, 0.01)
    }

    @Test
    fun `sin lotes de compra marca SIN_COSTO sin inventar costo cero`() {
        val r = RentaCalculator.computeSale(
            buys = emptyList(),
            sale = SaleEvent("X", YearMonth(2025, 1), 10.0, 1000.0, isArt107 = true),
            ipcIndex = emptyMap(), params = params,
        )
        assertEquals(RentaRegime.SIN_COSTO, r.regime)
        assertEquals(0.0, r.matchedQuantity, 0.0)
    }

    @Test
    fun `conciliacion detecta diferencia con tercero`() {
        val items = RentaCalculator.reconcile(
            ours = mapOf("SQM" to 32_750.0, "CHILE" to 5_000.0),
            reported = mapOf("SQM" to 40_000.0, "CHILE" to 5_000.0),
        )
        val sqm = items.first { it.ticker == "SQM" }
        assertEquals(-7_250.0, sqm.difference, 0.01)
        assertFalse(sqm.matches)
        assertTrue(items.first { it.ticker == "CHILE" }.matches)
    }
}
