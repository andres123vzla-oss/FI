package com.example

import com.example.data.entity.InvestmentEntity
import com.example.domain.BudgetAnalyzer
import com.example.domain.BudgetLine
import com.example.domain.BudgetRecommendation
import com.example.domain.BudgetStatus
import com.example.domain.CashFlowAnalyzer
import com.example.domain.CategorySpendingAnalyzer
import com.example.domain.FinancialHealth
import com.example.domain.FinancialHealthAnalyzer
import com.example.domain.InvestmentCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Valida la capa de dominio (análisis financiero) con los datos de referencia y casos límite:
 * tasa de ahorro, presupuesto, categorías, inversión, salud financiera y protección frente a
 * división por cero / valores no finitos.
 */
class DomainAnalyzersTest {

    // --- CashFlowAnalyzer ---

    @Test
    fun `tasa de ahorro con datos de referencia`() {
        // (1.090.094 - 748.825) / 1.090.094 = 341.269 / 1.090.094
        val rate = CashFlowAnalyzer.savingsRate(1_090_094.0, 748_825.0)
        assertEquals(0.31307, rate, 0.0001)
    }

    @Test
    fun `tasa de ahorro sin ingresos es cero`() {
        assertEquals(0.0, CashFlowAnalyzer.savingsRate(0.0, 50_000.0), 0.0)
    }

    @Test
    fun `gasto diario promedio protege division por cero`() {
        assertEquals(0.0, CashFlowAnalyzer.dailyAverageSpending(100_000.0, 0), 0.0)
        assertEquals(10_000.0, CashFlowAnalyzer.dailyAverageSpending(100_000.0, 10), 0.0001)
    }

    @Test
    fun `proyeccion fin de mes extrapola el ritmo diario`() {
        // 100.000 en 10 días -> 10.000/día * 30 = 300.000
        assertEquals(300_000.0, CashFlowAnalyzer.projectedMonthEndSpending(100_000.0, 10, 30), 0.0001)
    }

    @Test
    fun `variacion es nula sin base de comparacion`() {
        assertNull(CashFlowAnalyzer.variation(100.0, null))
        assertNull(CashFlowAnalyzer.variation(100.0, 0.0))
    }

    @Test
    fun `variacion calcula proporcion respecto al mes anterior`() {
        assertEquals(0.25, CashFlowAnalyzer.variation(125.0, 100.0)!!, 0.0001)
        assertEquals(-0.5, CashFlowAnalyzer.variation(50.0, 100.0)!!, 0.0001)
    }

    // --- BudgetAnalyzer ---

    @Test
    fun `estado de presupuesto segun uso`() {
        assertEquals(BudgetStatus.BAJO_CONTROL, BudgetAnalyzer.status(0.5))
        assertEquals(BudgetStatus.CERCA_DEL_LIMITE, BudgetAnalyzer.status(0.85))
        assertEquals(BudgetStatus.SOBREPASADO, BudgetAnalyzer.status(1.2))
        // Valor no finito (p. ej. división por cero) no debe romper la clasificación.
        assertEquals(BudgetStatus.BAJO_CONTROL, BudgetAnalyzer.status(Double.NaN))
    }

    @Test
    fun `resumen de presupuesto cuenta sobrepasadas y cercanas`() {
        val lines = listOf(
            BudgetLine("Vivienda", 300_000.0, 320_000.0), // sobrepasada
            BudgetLine("Alimentación", 280_000.0, 240_000.0), // cerca (0.857)
            BudgetLine("Transporte", 80_000.0, 20_000.0), // bajo control
            BudgetLine("SinPresupuesto", 0.0, 10_000.0), // ignorada (presupuesto 0)
        )
        val s = BudgetAnalyzer.summarize(lines)
        assertEquals(660_000.0, s.totalBudget, 0.0001)
        assertEquals(580_000.0, s.totalSpent, 0.0001)
        assertEquals(80_000.0, s.remaining, 0.0001)
        assertEquals(1, s.overCount)
        assertEquals(1, s.nearCount)
    }

    @Test
    fun `recomendaciones priorizan categorias sobrepasadas`() {
        val lines = listOf(
            BudgetLine("Vivienda", 300_000.0, 360_000.0),
            BudgetLine("Transporte", 80_000.0, 10_000.0),
        )
        val recs = BudgetAnalyzer.recommendations(lines)
        assertTrue(recs.first() is BudgetRecommendation.Over)
        val over = recs.first() as BudgetRecommendation.Over
        assertEquals("Vivienda", over.category)
        assertEquals(60_000.0, over.overBy, 0.0001)
    }

    // --- CategorySpendingAnalyzer ---

    @Test
    fun `categoria con mayor gasto y su porcentaje`() {
        val spent = mapOf(
            "Vivienda" to 458_000.0,
            "Uber" to 127_405.0,
            "Regalos" to 105_000.0,
        )
        val top = CategorySpendingAnalyzer.topCategory(spent)
        assertEquals("Vivienda", top?.categoryName)
        // 458.000 / 690.405 ≈ 0.6634
        assertEquals(0.6634, top!!.percentOfTotal, 0.001)
    }

    @Test
    fun `categoria top de mapa vacio es nula`() {
        assertNull(CategorySpendingAnalyzer.topCategory(emptyMap()))
    }

    @Test
    fun `alerta de categoria sin presupuesto con gasto relevante`() {
        val spent = mapOf("Ocio" to 200_000.0, "Salud" to 50_000.0)
        val budget = mapOf("Salud" to 60_000.0) // Ocio no tiene presupuesto
        val alerts = CategorySpendingAnalyzer.unbudgetedAlerts(spent, budget)
        assertEquals(1, alerts.size)
        assertEquals("Ocio", alerts.first().categoryName)
    }

    // --- InvestmentCalculator ---

    @Test
    fun `metricas de portafolio calculan rendimiento y pesos`() {
        val stocks = listOf(
            InvestmentEntity(id = 1, ticker = "AAA", companyName = "A", quantity = 10.0, purchasePrice = 100.0, currentPrice = 110.0),
            InvestmentEntity(id = 2, ticker = "BBB", companyName = "B", quantity = 5.0, purchasePrice = 200.0, currentPrice = 180.0),
        )
        val m = InvestmentCalculator.analyze(stocks)
        // Invertido: 1000 + 1000 = 2000; Actual: 1100 + 900 = 2000; Gan/Pérd: 0
        assertEquals(2_000.0, m.totalInvested, 0.0001)
        assertEquals(2_000.0, m.totalCurrent, 0.0001)
        assertEquals(0.0, m.totalGainLoss, 0.0001)
        assertEquals(0.0, m.totalYieldPercent, 0.0001)
        assertEquals("AAA", m.best?.ticker) // +10%
        assertEquals("BBB", m.worst?.ticker) // -10%
    }

    @Test
    fun `portafolio vacio no revienta`() {
        val m = InvestmentCalculator.analyze(emptyList())
        assertEquals(0.0, m.totalCurrent, 0.0)
        assertNull(m.best)
        assertNull(m.worst)
    }

    // --- FinancialHealthAnalyzer ---

    @Test
    fun `salud financiera segun ahorro y balance`() {
        assertEquals(FinancialHealth.EXCELENTE, FinancialHealthAnalyzer.evaluate(0.25, 100.0))
        assertEquals(FinancialHealth.BUENO, FinancialHealthAnalyzer.evaluate(0.12, 100.0))
        assertEquals(FinancialHealth.AJUSTADO, FinancialHealthAnalyzer.evaluate(0.02, 100.0))
        assertEquals(FinancialHealth.CRITICO, FinancialHealthAnalyzer.evaluate(0.30, -100.0))
    }
}
