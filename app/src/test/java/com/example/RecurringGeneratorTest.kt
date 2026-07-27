package com.example

import com.example.data.entity.RecurringRuleEntity
import com.example.data.recurring.RecurringGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros de la política de generación de recurrentes (P1-1): catch-up, recorte del
 * día en meses cortos, mes en curso debido/no debido, idempotencia, reglas inválidas y ancla
 * inicial.
 */
class RecurringGeneratorTest {

    private fun rule(
        day: Int,
        lastYear: Int,
        lastMonth: Int,
        active: Boolean = true,
        amount: Double = 458_000.0,
    ) = RecurringRuleEntity(
        id = 1, type = "EXPENSE", categoryName = "Vivienda", description = "Arriendo",
        amount = amount, dayOfMonth = day, active = active,
        lastYear = lastYear, lastMonth = lastMonth, createdAt = 1L, updatedAt = 1L,
    )

    @Test
    fun `catch-up genera cada mes saltado con su fecha correcta`() {
        // Ancla en abril; hoy 26-jul → deben salir mayo, junio y julio (día 1 ya pasó).
        val r = RecurringGenerator.pending(listOf(rule(day = 1, lastYear = 2026, lastMonth = 4)), 2026, 7, 26, nowMs = 99L)
        assertEquals(listOf("2026-05-01", "2026-06-01", "2026-07-01"), r.transactions.map { it.date })
        assertEquals(1, r.updatedRules.size)
        assertEquals(2026 to 7, r.updatedRules.first().lastYear to r.updatedRules.first().lastMonth)
        // Los montos y tipos vienen de la regla, intactos.
        assertTrue(r.transactions.all { it.amount == 458_000.0 && it.type == "EXPENSE" && it.categoryName == "Vivienda" })
    }

    @Test
    fun `regla del dia 31 se recorta en meses cortos y cruza el anio`() {
        // Ancla dic-2027; hoy 01-mar-2028 (bisiesto). Enero 31, febrero 29; marzo aún no (1 < 31).
        val r = RecurringGenerator.pending(listOf(rule(day = 31, lastYear = 2027, lastMonth = 12)), 2028, 3, 1, nowMs = 99L)
        assertEquals(listOf("2028-01-31", "2028-02-29"), r.transactions.map { it.date })
        assertEquals(2028 to 2, r.updatedRules.first().lastYear to r.updatedRules.first().lastMonth)
    }

    @Test
    fun `mes en curso solo genera cuando llega el dia efectivo`() {
        // Día 28, hoy 26: no debe generar. El ancla NO avanza (nada generado).
        val antes = RecurringGenerator.pending(listOf(rule(day = 28, lastYear = 2026, lastMonth = 6)), 2026, 7, 26)
        assertTrue(antes.isEmpty)
        assertTrue(antes.updatedRules.isEmpty())

        // Hoy 28: genera exactamente julio.
        val despues = RecurringGenerator.pending(listOf(rule(day = 28, lastYear = 2026, lastMonth = 6)), 2026, 7, 28, nowMs = 99L)
        assertEquals(listOf("2026-07-28"), despues.transactions.map { it.date })
    }

    @Test
    fun `segunda pasada con el ancla avanzada es idempotente`() {
        val primera = RecurringGenerator.pending(listOf(rule(day = 1, lastYear = 2026, lastMonth = 5)), 2026, 7, 26, nowMs = 99L)
        assertEquals(2, primera.transactions.size) // junio y julio
        val reglaAvanzada = primera.updatedRules.first()
        val segunda = RecurringGenerator.pending(listOf(reglaAvanzada), 2026, 7, 26, nowMs = 99L)
        assertTrue(segunda.isEmpty)
    }

    @Test
    fun `reglas inactivas o invalidas no generan nada`() {
        val inactiva = rule(day = 1, lastYear = 2026, lastMonth = 1, active = false)
        val diaInvalido = rule(day = 0, lastYear = 2026, lastMonth = 1)
        val montoCero = rule(day = 1, lastYear = 2026, lastMonth = 1, amount = 0.0)
        val montoNaN = rule(day = 1, lastYear = 2026, lastMonth = 1, amount = Double.NaN)
        val r = RecurringGenerator.pending(listOf(inactiva, diaInvalido, montoCero, montoNaN), 2026, 7, 26)
        assertTrue(r.isEmpty)
        assertTrue(r.updatedRules.isEmpty())
    }

    @Test
    fun `ancla inicial - dia ya pasado ancla el mes actual, dia por venir ancla el anterior`() {
        // Hoy 26-jul. Día 1 (ya pasó) → ancla jul (no retro).
        assertEquals(2026 to 7, RecurringGenerator.initialAnchor(2026, 7, 26, 1))
        // Día 28 (por venir) → ancla jun (se generará el 28-jul).
        assertEquals(2026 to 6, RecurringGenerator.initialAnchor(2026, 7, 26, 28))
        // Enero con día por venir → ancla dic del año anterior.
        assertEquals(2025 to 12, RecurringGenerator.initialAnchor(2026, 1, 3, 15))
        // Día 31 en febrero (efectivo 28): hoy 28-feb-2026 → ancla feb (ya se cumplió).
        assertEquals(2026 to 2, RecurringGenerator.initialAnchor(2026, 2, 28, 31))
    }
}
