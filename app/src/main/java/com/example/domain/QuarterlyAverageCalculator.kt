package com.example.domain

import com.example.util.FinanceCalculator

/**
 * FIN2-09: promedio "trimestral" de gasto por categoría, extraído del combine del ViewModel
 * (CALC-03) a una función pura testeable.
 *
 * Semántica CALC-03: numerador y denominador se derivan del MISMO conjunto filtrado; el divisor
 * son los MESES CON DATOS reales dentro de la ventana (no siempre 3), con mínimo 1 para evitar
 * división por cero. Una categoría con gasto en solo 1 de los meses de la ventana se promedia
 * sobre 1, no sobre 3 (no subestima ~3x).
 */
object QuarterlyAverageCalculator {

    /**
     * @param txs pares (fecha "YYYY-MM-DD", monto) YA filtrados por categoría/tipo.
     * @param window meses de la ventana como pares (mes 1..12, año).
     * @return promedio = suma de montos en ventana / meses distintos con datos (>= 1).
     */
    fun average(txs: List<Pair<String, Double>>, window: Collection<Pair<Int, Int>>): Double {
        val windowSet = window.toSet()
        val inRange = txs.mapNotNull { (date, amount) ->
            val parts = date.split("-")
            if (parts.size >= 2) {
                val y = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull()
                if (m != null && y != null && (m to y) in windowSet) (m to y) to amount else null
            } else null
        }
        val total = FinanceCalculator.sum(inRange.map { it.second })
        val mesesConDatos = inRange.map { it.first }.distinct().size.coerceAtLeast(1)
        return total / mesesConDatos
    }
}
