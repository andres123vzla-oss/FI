package com.example

import com.example.domain.FinancialHealth
import com.example.domain.FinancialHealthAnalyzer
import com.example.domain.QuarterlyAverageCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de los fixes de finanzas de la re-auditoría (FIN2-03, FIN2-09, FIN2-10):
 * promedio trimestral extraído a función pura y salud financiera con estado SIN_DATOS.
 */
class Fin2FixesTest {

    // --- QuarterlyAverageCalculator (CALC-03 / FIN2-03 / FIN2-09) ---

    private val window = listOf(3 to 2026, 4 to 2026, 5 to 2026)

    @Test
    fun `promedio con datos en los 3 meses divide por 3`() {
        val txs = listOf(
            "2026-03-10" to 90_000.0,
            "2026-04-12" to 90_000.0,
            "2026-05-15" to 90_000.0,
        )
        assertEquals(90_000.0, QuarterlyAverageCalculator.average(txs, window), 0.001)
    }

    @Test
    fun `promedio con datos en 1 mes divide por 1 no por 3`() {
        // CALC-03: antes dividía siempre por 3, subestimando ~3x.
        val txs = listOf("2026-04-12" to 90_000.0)
        assertEquals(90_000.0, QuarterlyAverageCalculator.average(txs, window), 0.001)
    }

    @Test
    fun `promedio con datos en 2 meses divide por 2`() {
        val txs = listOf(
            "2026-03-10" to 60_000.0,
            "2026-03-25" to 30_000.0, // mismo mes: cuenta una sola vez en el divisor
            "2026-05-15" to 90_000.0,
        )
        assertEquals(90_000.0, QuarterlyAverageCalculator.average(txs, window), 0.001)
    }

    @Test
    fun `sin datos en la ventana devuelve 0 sin dividir por cero`() {
        val txs = listOf("2026-01-10" to 50_000.0) // fuera de la ventana
        assertEquals(0.0, QuarterlyAverageCalculator.average(txs, window), 0.001)
    }

    @Test
    fun `transacciones fuera de la ventana no contaminan numerador ni denominador`() {
        val txs = listOf(
            "2026-01-10" to 999_999.0, // fuera
            "2026-05-15" to 90_000.0,  // dentro
        )
        assertEquals(90_000.0, QuarterlyAverageCalculator.average(txs, window), 0.001)
    }

    @Test
    fun `montos no finitos se ignoran en el numerador`() {
        val txs = listOf(
            "2026-05-15" to Double.POSITIVE_INFINITY,
            "2026-05-16" to 90_000.0,
        )
        // FIN2-02: la suma protegida ignora el no-finito; el mes sigue contando como con-datos.
        assertEquals(90_000.0, QuarterlyAverageCalculator.average(txs, window), 0.001)
    }

    @Test
    fun `fechas malformadas se ignoran`() {
        val txs = listOf(
            "sin-fecha" to 50_000.0,
            "2026-05-15" to 90_000.0,
        )
        assertEquals(90_000.0, QuarterlyAverageCalculator.average(txs, window), 0.001)
    }

    // --- FinancialHealthAnalyzer.SIN_DATOS (FIN2-10) ---

    @Test
    fun `mes sin ingresos ni gastos es SIN_DATOS no Ajustado`() {
        val health = FinancialHealthAnalyzer.evaluate(
            savingsRate = 0.0,
            balance = 0.0,
            hasData = false,
        )
        assertEquals(FinancialHealth.SIN_DATOS, health)
    }

    @Test
    fun `mes con ingresos iguales a gastos sigue siendo AJUSTADO`() {
        // hasData = income != 0 || expense != 0 (NO balance == 0, que enmascararía este caso).
        val health = FinancialHealthAnalyzer.evaluate(
            savingsRate = 0.0,
            balance = 0.0,
            hasData = true,
        )
        assertEquals(FinancialHealth.AJUSTADO, health)
    }

    @Test
    fun `tasa de ahorro no finita es SIN_DATOS`() {
        val health = FinancialHealthAnalyzer.evaluate(
            savingsRate = Double.NaN,
            balance = 0.0,
            hasData = true,
        )
        assertEquals(FinancialHealth.SIN_DATOS, health)
    }

    @Test
    fun `clasificacion normal no cambia`() {
        assertEquals(FinancialHealth.EXCELENTE, FinancialHealthAnalyzer.evaluate(0.25, 100.0, true))
        assertEquals(FinancialHealth.BUENO, FinancialHealthAnalyzer.evaluate(0.12, 100.0, true))
        assertEquals(FinancialHealth.AJUSTADO, FinancialHealthAnalyzer.evaluate(0.05, 100.0, true))
        assertEquals(FinancialHealth.CRITICO, FinancialHealthAnalyzer.evaluate(0.1, -1.0, true))
    }
}
