package com.example.domain

import com.example.util.FinanceCalculator

/**
 * Análisis de flujo de caja del mes: tasa de ahorro, gasto diario promedio, proyección de gasto
 * a fin de mes y variación frente al mes anterior.
 *
 * Todas las divisiones están protegidas contra división por cero, NaN e Infinity. Las funciones
 * son puras (sin estado ni dependencias de Android) para poder validarlas con tests unitarios.
 */
object CashFlowAnalyzer {

    /** Tasa de ahorro = (ingresos − gastos) / ingresos. Devuelve 0.0 si no hay ingresos. */
    fun savingsRate(income: Double, expense: Double): Double {
        if (!income.isFinite() || !expense.isFinite() || income <= 0.0) return 0.0
        val rate = (income - expense) / income
        return if (rate.isFinite()) rate else 0.0
    }

    /** Gasto diario promedio del periodo transcurrido. Devuelve 0.0 si no hay días. */
    fun dailyAverageSpending(totalExpense: Double, daysElapsed: Int): Double =
        FinanceCalculator.ratio(totalExpense, daysElapsed.toDouble())

    /**
     * Proyección de gasto a fin de mes: extrapola el ritmo diario actual al total de días del mes.
     * Si aún no hay días transcurridos, devuelve el gasto actual.
     */
    fun projectedMonthEndSpending(totalExpense: Double, daysElapsed: Int, daysInMonth: Int): Double {
        if (daysElapsed <= 0 || daysInMonth <= 0) return if (totalExpense.isFinite()) totalExpense else 0.0
        val daily = dailyAverageSpending(totalExpense, daysElapsed)
        val projected = daily * daysInMonth
        return if (projected.isFinite()) projected else 0.0
    }

    /**
     * Variación proporcional entre el valor actual y el del periodo anterior.
     * Devuelve `null` cuando no hay base de comparación (sin mes previo o base en cero), para que
     * la UI muestre "Sin comparación previa".
     */
    fun variation(current: Double, previous: Double?): Double? {
        if (previous == null || !current.isFinite() || !previous.isFinite() || previous == 0.0) return null
        val v = (current - previous) / kotlin.math.abs(previous)
        return if (v.isFinite()) v else null
    }
}
