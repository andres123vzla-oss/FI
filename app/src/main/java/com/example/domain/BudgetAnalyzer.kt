package com.example.domain

import com.example.util.FinanceCalculator

/** Estado de una categoría frente a su presupuesto. */
enum class BudgetStatus(val label: String) {
    BAJO_CONTROL("Bajo control"),
    CERCA_DEL_LIMITE("Cerca del límite"),
    SOBREPASADO("Sobrepasado"),
}

/** Línea de presupuesto de entrada para el análisis (independiente de la capa de datos). */
data class BudgetLine(
    val categoryName: String,
    val budgeted: Double,
    val spent: Double,
)

/** Resumen agregado del presupuesto del mes. */
data class BudgetSummary(
    val totalBudget: Double = 0.0,
    val totalSpent: Double = 0.0,
    val remaining: Double = 0.0,
    val overCount: Int = 0,
    val nearCount: Int = 0,
    val usagePercent: Double = 0.0,
)

/**
 * Análisis de presupuesto: estado por categoría, resumen agregado y recomendaciones simples.
 * Funciones puras y protegidas contra división por cero / valores no finitos.
 */
object BudgetAnalyzer {

    const val NEAR_LIMIT_THRESHOLD = 0.80

    /** Clasifica el estado según el porcentaje de uso (gastado / presupuestado). */
    fun status(usage: Double): BudgetStatus = when {
        !usage.isFinite() -> BudgetStatus.BAJO_CONTROL
        usage > 1.0 -> BudgetStatus.SOBREPASADO
        usage >= NEAR_LIMIT_THRESHOLD -> BudgetStatus.CERCA_DEL_LIMITE
        else -> BudgetStatus.BAJO_CONTROL
    }

    /** Solo considera categorías con presupuesto > 0 para los conteos de estado. */
    fun summarize(lines: List<BudgetLine>): BudgetSummary {
        val budgeted = lines.filter { it.budgeted > 0.0 }
        val totalBudget = FinanceCalculator.sum(budgeted.map { it.budgeted })
        val totalSpent = FinanceCalculator.sum(budgeted.map { it.spent })
        val overCount = budgeted.count { FinanceCalculator.ratio(it.spent, it.budgeted) > 1.0 }
        val nearCount = budgeted.count {
            val u = FinanceCalculator.ratio(it.spent, it.budgeted)
            u in NEAR_LIMIT_THRESHOLD..1.0
        }
        return BudgetSummary(
            totalBudget = totalBudget,
            totalSpent = totalSpent,
            remaining = totalBudget - totalSpent,
            overCount = overCount,
            nearCount = nearCount,
            usagePercent = FinanceCalculator.ratio(totalSpent, totalBudget),
        )
    }

    /**
     * Recomendaciones simples basadas en el estado de cada categoría. Devuelve textos listos para
     * mostrar (los montos los formatea la UI; aquí se entregan datos crudos en el mensaje).
     */
    fun recommendations(lines: List<BudgetLine>, maxItems: Int = 3): List<BudgetRecommendation> {
        val budgeted = lines.filter { it.budgeted > 0.0 }
        val over = budgeted
            .filter { FinanceCalculator.ratio(it.spent, it.budgeted) > 1.0 }
            .sortedByDescending { it.spent - it.budgeted }
            .map { BudgetRecommendation.Over(it.categoryName, it.spent - it.budgeted) }
        val near = budgeted
            .filter { FinanceCalculator.ratio(it.spent, it.budgeted) in NEAR_LIMIT_THRESHOLD..1.0 }
            .sortedByDescending { it.spent }
            .map { BudgetRecommendation.Near(it.categoryName, it.budgeted - it.spent) }
        val healthy = budgeted
            .filter { FinanceCalculator.ratio(it.spent, it.budgeted) < NEAR_LIMIT_THRESHOLD }
            .sortedByDescending { it.budgeted - it.spent }
            .map { BudgetRecommendation.Available(it.categoryName, it.budgeted - it.spent) }
        return (over + near + healthy).take(maxItems)
    }
}

/** Recomendación de presupuesto con el dato monetario sin formatear. */
sealed class BudgetRecommendation {
    data class Over(val category: String, val overBy: Double) : BudgetRecommendation()
    data class Near(val category: String, val remaining: Double) : BudgetRecommendation()
    data class Available(val category: String, val remaining: Double) : BudgetRecommendation()
}
