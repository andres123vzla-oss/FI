package com.example.domain

import com.example.util.FinanceCalculator

/** Análisis de una categoría de gasto. */
data class CategoryAnalysis(
    val categoryName: String,
    val amount: Double,
    val percentOfTotal: Double,
    val budgeted: Double,
    val hasBudget: Boolean,
    val overBudget: Boolean,
)

/** Alerta sobre una categoría sin presupuesto con gasto relevante. */
data class CategoryAlert(
    val categoryName: String,
    val amount: Double,
    val percentOfTotal: Double,
)

/**
 * Análisis de gasto por categoría: ranking, porcentaje sobre el total, comparación contra
 * presupuesto y detección de categorías sin presupuesto con gasto alto.
 */
object CategorySpendingAnalyzer {

    /** Umbral (proporción del total) a partir del cual una categoría sin presupuesto genera alerta. */
    const val UNBUDGETED_ALERT_THRESHOLD = 0.10

    /**
     * @param spentByCategory monto gastado por nombre de categoría.
     * @param budgetByCategory presupuesto por nombre de categoría (puede estar incompleto).
     * Devuelve el ranking ordenado de mayor a menor gasto.
     */
    fun analyze(
        spentByCategory: Map<String, Double>,
        budgetByCategory: Map<String, Double>,
    ): List<CategoryAnalysis> {
        val total = FinanceCalculator.sum(spentByCategory.values.toList())
        return spentByCategory
            .filter { it.value > 0.0 }
            .map { (name, amount) ->
                val budget = budgetByCategory[name] ?: 0.0
                val hasBudget = budget > 0.0
                CategoryAnalysis(
                    categoryName = name,
                    amount = amount,
                    percentOfTotal = FinanceCalculator.ratio(amount, total),
                    budgeted = budget,
                    hasBudget = hasBudget,
                    overBudget = hasBudget && amount > budget,
                )
            }
            .sortedByDescending { it.amount }
    }

    /** Categoría con mayor gasto, o null si no hay gasto. */
    fun topCategory(spentByCategory: Map<String, Double>): CategoryAnalysis? =
        analyze(spentByCategory, emptyMap()).firstOrNull()

    /** Categorías sin presupuesto cuyo gasto supera el umbral relevante. */
    fun unbudgetedAlerts(
        spentByCategory: Map<String, Double>,
        budgetByCategory: Map<String, Double>,
    ): List<CategoryAlert> =
        analyze(spentByCategory, budgetByCategory)
            .filter { !it.hasBudget && it.percentOfTotal >= UNBUDGETED_ALERT_THRESHOLD }
            .map { CategoryAlert(it.categoryName, it.amount, it.percentOfTotal) }
}
