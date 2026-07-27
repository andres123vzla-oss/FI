package com.example.data.recurring

import com.example.data.entity.RecurringRuleEntity
import com.example.data.entity.TransactionEntity
import java.util.Calendar

/**
 * Generador PURO de movimientos recurrentes (P1-1). Decide, de forma determinista y sin tocar
 * la BD, qué TransactionEntity corresponde crear hoy para cada regla y cómo avanza su ancla.
 *
 * Política (documentada en el plan y blindada por RecurringGeneratorTest):
 *  - Día efectivo del mes = min(dayOfMonth, días del mes): la regla del 31 genera el 30/28-29
 *    en meses cortos, nunca se salta el mes.
 *  - Se generan TODOS los meses pendientes desde el ancla (catch-up): si la app no se abre en
 *    dos meses, ambos aparecen con su fecha correcta.
 *  - El mes en curso solo genera cuando hoy.día >= día efectivo.
 *  - El ancla (lastYear/lastMonth) avanza al último mes generado → idempotente: una segunda
 *    pasada el mismo día genera cero.
 *  - Reglas inactivas o con datos inválidos (día fuera de 1..31, monto no finito o <= 0) se
 *    saltan: jamás se genera basura (convención de casos límite del proyecto).
 *
 * OBJETO PURO DE JVM: solo entidades (POJOs), kotlin.* y java.util.Calendar → test JUnit plano.
 */
object RecurringGenerator {

    /** Resultado de una pasada: movimientos a insertar + reglas con su ancla avanzada. */
    data class Result(
        val transactions: List<TransactionEntity>,
        val updatedRules: List<RecurringRuleEntity>,
    ) {
        val isEmpty: Boolean get() = transactions.isEmpty()
    }

    /** Día efectivo de la regla en un mes concreto: recortado a los días reales del mes. */
    fun effectiveDay(dayOfMonth: Int, year: Int, month: Int): Int =
        dayOfMonth.coerceIn(1, daysInMonth(year, month))

    /**
     * Ancla inicial de una regla NUEVA: si el día efectivo del mes actual ya pasó (o es hoy),
     * el ancla es el mes actual — no se genera retroactivo, ese movimiento el usuario ya lo
     * registró o no lo quiere. Si el día aún no llega, el ancla es el mes anterior y el
     * movimiento aparecerá al llegar el día. (También se usa al REACTIVAR una regla, para no
     * generar de golpe los meses en que estuvo apagada.)
     */
    fun initialAnchor(todayYear: Int, todayMonth: Int, todayDay: Int, dayOfMonth: Int): Pair<Int, Int> {
        val due = effectiveDay(dayOfMonth, todayYear, todayMonth)
        return if (todayDay >= due) todayYear to todayMonth else prevMonth(todayYear, todayMonth)
    }

    /**
     * Calcula lo pendiente de TODAS las reglas hasta hoy. [nowMs] alimenta createdAt/updatedAt
     * (inyectable para tests deterministas).
     */
    fun pending(
        rules: List<RecurringRuleEntity>,
        todayYear: Int,
        todayMonth: Int,
        todayDay: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): Result {
        val transactions = mutableListOf<TransactionEntity>()
        val updatedRules = mutableListOf<RecurringRuleEntity>()

        for (rule in rules) {
            if (!rule.active) continue
            if (rule.dayOfMonth !in 1..31) continue
            if (!rule.amount.isFinite() || rule.amount <= 0.0) continue

            var (y, m) = nextMonth(rule.lastYear, rule.lastMonth)
            var lastY = rule.lastYear
            var lastM = rule.lastMonth
            var generated = false

            while (y < todayYear || (y == todayYear && m <= todayMonth)) {
                val day = effectiveDay(rule.dayOfMonth, y, m)
                val isCurrentMonth = y == todayYear && m == todayMonth
                if (isCurrentMonth && todayDay < day) break // aún no llega el día de este mes

                transactions += TransactionEntity(
                    type = rule.type,
                    date = "%04d-%02d-%02d".format(y, m, day),
                    categoryName = rule.categoryName,
                    description = rule.description,
                    amount = rule.amount,
                    createdAt = nowMs,
                    updatedAt = nowMs,
                )
                lastY = y
                lastM = m
                generated = true
                val next = nextMonth(y, m)
                y = next.first
                m = next.second
            }

            if (generated) {
                updatedRules += rule.copy(lastYear = lastY, lastMonth = lastM, updatedAt = nowMs)
            }
        }
        return Result(transactions, updatedRules)
    }

    private fun nextMonth(year: Int, month: Int): Pair<Int, Int> =
        if (month >= 12) (year + 1) to 1 else year to (month + 1)

    private fun prevMonth(year: Int, month: Int): Pair<Int, Int> =
        if (month <= 1) (year - 1) to 12 else year to (month - 1)

    /** Días reales del mes (bisiestos incluidos), sin java.time (minSdk 24 sin desugaring). */
    fun daysInMonth(year: Int, month: Int): Int {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, (month - 1).coerceIn(0, 11))
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
}
