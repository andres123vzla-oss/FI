package com.example.domain

/** Estado financiero del mes, derivado de la tasa de ahorro y el balance. */
enum class FinancialHealth(val label: String) {
    EXCELENTE("Excelente"),
    BUENO("Bueno"),
    AJUSTADO("Ajustado"),
    CRITICO("Crítico"),
}

/**
 * Evalúa la salud financiera del mes a partir de la tasa de ahorro y el balance.
 *
 * Reglas:
 * - Balance negativo ⇒ Crítico (se gasta más de lo que ingresa).
 * - Tasa de ahorro ≥ 20% ⇒ Excelente.
 * - Tasa de ahorro ≥ 10% ⇒ Bueno.
 * - Tasa de ahorro ≥ 0%  ⇒ Ajustado.
 * - En cualquier otro caso ⇒ Crítico.
 */
object FinancialHealthAnalyzer {

    fun evaluate(savingsRate: Double, balance: Double): FinancialHealth {
        if (!savingsRate.isFinite()) return FinancialHealth.AJUSTADO
        if (balance < 0.0) return FinancialHealth.CRITICO
        return when {
            savingsRate >= 0.20 -> FinancialHealth.EXCELENTE
            savingsRate >= 0.10 -> FinancialHealth.BUENO
            savingsRate >= 0.0 -> FinancialHealth.AJUSTADO
            else -> FinancialHealth.CRITICO
        }
    }
}
