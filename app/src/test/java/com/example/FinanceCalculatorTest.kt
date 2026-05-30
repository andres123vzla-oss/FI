package com.example

import com.example.util.FinanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Valida los cálculos financieros contra los valores de referencia iniciales:
 * Ingresos CLP 1.090.094, Gastos CLP 748.825, Balance CLP 341.269.
 * Incluye casos límite de división por cero / valores no finitos.
 */
class FinanceCalculatorTest {

    private val seedIncomes = listOf(421_000.0, 327_094.0, 270_000.0, 40_000.0, 32_000.0)
    private val seedExpenses =
        listOf(458_000.0, 20_000.0, 127_405.0, 2_700.0, 105_000.0, 5_560.0, 5_160.0, 25_000.0)

    @Test
    fun `ingresos iniciales suman 1090094`() {
        assertEquals(1_090_094.0, FinanceCalculator.sum(seedIncomes), 0.0001)
    }

    @Test
    fun `gastos iniciales suman 748825`() {
        assertEquals(748_825.0, FinanceCalculator.sum(seedExpenses), 0.0001)
    }

    @Test
    fun `balance inicial es 341269`() {
        val income = FinanceCalculator.sum(seedIncomes)
        val expense = FinanceCalculator.sum(seedExpenses)
        assertEquals(341_269.0, FinanceCalculator.balance(income, expense), 0.0001)
    }

    @Test
    fun `ratio protege division por cero`() {
        assertEquals(0.0, FinanceCalculator.ratio(5_000.0, 0.0), 0.0)
        assertEquals(0.0, FinanceCalculator.ratio(5_000.0, -10.0), 0.0)
    }

    @Test
    fun `ratio normal devuelve proporcion`() {
        assertEquals(0.5, FinanceCalculator.ratio(50.0, 100.0), 0.0001)
    }

    @Test
    fun `ratio descarta valores no finitos`() {
        assertEquals(0.0, FinanceCalculator.ratio(Double.NaN, 100.0), 0.0)
        assertEquals(0.0, FinanceCalculator.ratio(10.0, Double.POSITIVE_INFINITY), 0.0)
    }

    @Test
    fun `sum ignora NaN e Infinity`() {
        assertEquals(30.0, FinanceCalculator.sum(listOf(10.0, Double.NaN, 20.0, Double.POSITIVE_INFINITY)), 0.0001)
    }

    @Test
    fun `rendimiento de inversion`() {
        val invested = 1_000.0
        val current = 1_100.0
        val gl = FinanceCalculator.gainLoss(current, invested)
        assertEquals(100.0, gl, 0.0001)
        assertEquals(0.10, FinanceCalculator.yieldPercent(gl, invested), 0.0001)
    }

    @Test
    fun `rendimiento con costo cero no revienta`() {
        assertEquals(0.0, FinanceCalculator.yieldPercent(100.0, 0.0), 0.0)
    }
}
