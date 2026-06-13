package com.example.domain

/** Estado financiero del mes, derivado de la tasa de ahorro y el balance. */
enum class FinancialHealth(val label: String) {
    EXCELENTE("Excelente"),
    BUENO("Bueno"),
    AJUSTADO("Ajustado"),
    CRITICO("Crítico"),

    /** FIN2-10: mes sin ningún dato — no hay nada que evaluar (se muestra neutro, no warning). */
    SIN_DATOS("Sin datos"),
}

/**
 * Evalúa la salud financiera del mes a partir de la tasa de ahorro y el balance.
 *
 * Reglas:
 * - Sin ingresos NI gastos ⇒ Sin datos (FIN2-10: un mes vacío no es "Ajustado").
 * - Balance negativo ⇒ Crítico (se gasta más de lo que ingresa).
 * - Tasa de ahorro ≥ 20% ⇒ Excelente.
 * - Tasa de ahorro ≥ 10% ⇒ Bueno.
 * - Tasa de ahorro ≥ 0%  ⇒ Ajustado.
 * - En cualquier otro caso ⇒ Crítico.
 */
object FinancialHealthAnalyzer {

    fun evaluate(savingsRate: Double, balance: Double): FinancialHealth =
        evaluate(savingsRate, balance, hasData = true)

    /**
     * Variante con señal explícita de datos (FIN2-10): [hasData] debe ser
     * `income != 0.0 || expense != 0.0` — NO `balance == 0.0`, que enmascararía meses
     * legítimos con ingresos == gastos.
     */
    fun evaluate(savingsRate: Double, balance: Double, hasData: Boolean): FinancialHealth {
        if (!hasData) return FinancialHealth.SIN_DATOS
        // Tasa no finita: sin ingresos evaluables — tampoco hay nada que valorar.
        if (!savingsRate.isFinite()) return FinancialHealth.SIN_DATOS
        if (balance < 0.0) return FinancialHealth.CRITICO
        return when {
            savingsRate >= 0.20 -> FinancialHealth.EXCELENTE
            savingsRate >= 0.10 -> FinancialHealth.BUENO
            savingsRate >= 0.0 -> FinancialHealth.AJUSTADO
            else -> FinancialHealth.CRITICO
        }
    }
}
