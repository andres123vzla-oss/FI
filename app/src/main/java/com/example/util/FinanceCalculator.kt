package com.example.util

/**
 * Cálculos financieros puros y sin estado, extraídos para poder validarlos con tests unitarios
 * (sin dependencias de Android) y reutilizarlos desde el ViewModel.
 *
 * Todas las divisiones están protegidas contra división por cero, NaN e Infinity: ante un
 * divisor no positivo o no finito devuelven 0.0.
 */
object FinanceCalculator {

    /** Suma robusta de montos. */
    fun sum(amounts: List<Double>): Double =
        amounts.fold(0.0) { acc, v -> acc + (if (v.isFinite()) v else 0.0) }

    /** Balance = ingresos − gastos. */
    fun balance(income: Double, expense: Double): Double = income - expense

    /**
     * Razón parte/total controlada. Devuelve 0.0 si el total es <= 0 o si algún valor no es
     * finito, evitando NaN/Infinity.
     */
    fun ratio(part: Double, whole: Double): Double {
        if (!part.isFinite() || !whole.isFinite() || whole <= 0.0) return 0.0
        val result = part / whole
        return if (result.isFinite()) result else 0.0
    }

    /** Ganancia/pérdida = valor actual − costo. */
    fun gainLoss(current: Double, invested: Double): Double = current - invested

    /** Rendimiento = ganancia / costo (proporción), controlado contra división por cero. */
    fun yieldPercent(gainLoss: Double, invested: Double): Double = ratio(gainLoss, invested)
}
