package com.example

import com.example.data.entity.BudgetEntity
import com.example.data.entity.InvestmentEntity
import com.example.data.entity.RecurringRuleEntity
import com.example.data.entity.TransactionEntity
import com.example.data.notion.NotionMapper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests del mapeo entidades → propiedades del API de Notion (Robolectric por org.json).
 * Blindan los NOMBRES de propiedad (deben coincidir con las bases espejo), la traducción
 * INCOME/EXPENSE → Ingreso/Gasto, las fechas y el saneo de montos no finitos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotionMapperTest {

    private fun tx(
        amount: Double = 458_000.0,
        type: String = "EXPENSE",
        date: String = "2026-07-01",
        category: String = "Vivienda",
        description: String = "Arriendo",
    ) = TransactionEntity(
        id = 1, type = type, date = date, categoryName = category,
        description = description, amount = amount, createdAt = 1L, updatedAt = 1L,
    )

    @Test
    fun `movimiento mapea nombres de propiedad, tipo y fecha correctos`() {
        val json = JSONObject(NotionMapper.movementProperties(tx()))
        assertEquals(
            "Arriendo",
            json.getJSONObject("Descripción").getJSONArray("title")
                .getJSONObject(0).getJSONObject("text").getString("content"),
        )
        assertEquals("2026-07-01", json.getJSONObject("Fecha").getJSONObject("date").getString("start"))
        assertEquals("Gasto", json.getJSONObject("Tipo").getJSONObject("select").getString("name"))
        assertEquals("Vivienda", json.getJSONObject("Categoría").getJSONObject("select").getString("name"))
        assertEquals(458_000.0, json.getJSONObject("Monto CLP").getDouble("number"), 0.001)
        assertEquals("App", json.getJSONObject("Origen").getJSONObject("select").getString("name"))
    }

    @Test
    fun `ingreso se traduce a Ingreso y descripcion vacia lleva placeholder`() {
        val json = JSONObject(NotionMapper.movementProperties(tx(type = "INCOME", description = "  ")))
        assertEquals("Ingreso", json.getJSONObject("Tipo").getJSONObject("select").getString("name"))
        assertEquals(
            "(sin descripción)",
            json.getJSONObject("Descripción").getJSONArray("title")
                .getJSONObject(0).getJSONObject("text").getString("content"),
        )
    }

    @Test
    fun `monto no finito se sanea a cero (nunca viaja NaN)`() {
        val json = JSONObject(NotionMapper.movementProperties(tx(amount = Double.NaN)))
        assertEquals(0.0, json.getJSONObject("Monto CLP").getDouble("number"), 0.0)
    }

    @Test
    fun `regla recurrente mapea dia y checkbox activa`() {
        val rule = RecurringRuleEntity(
            id = 2, type = "EXPENSE", categoryName = "Vivienda", description = "Arriendo",
            amount = 458_000.0, dayOfMonth = 1, active = false, lastYear = 2026, lastMonth = 6,
        )
        val json = JSONObject(NotionMapper.recurringProperties(rule))
        assertEquals(1.0, json.getJSONObject("Día del mes").getDouble("number"), 0.0)
        assertFalse(json.getJSONObject("Activa").getBoolean("checkbox"))
    }

    @Test
    fun `presupuesto mapea el mes como primer dia y el gastado calculado`() {
        val budget = BudgetEntity(id = 3, categoryName = "Alimentación", month = 7, year = 2026, plannedAmount = 280_000.0)
        val json = JSONObject(NotionMapper.budgetProperties(budget, spentClp = 123_456.0))
        assertEquals("2026-07-01", json.getJSONObject("Mes").getJSONObject("date").getString("start"))
        assertEquals(280_000.0, json.getJSONObject("Presupuestado CLP").getDouble("number"), 0.001)
        assertEquals(123_456.0, json.getJSONObject("Gastado CLP").getDouble("number"), 0.001)
    }

    @Test
    fun `portafolio mapea ticker como title y precios como number`() {
        val inv = InvestmentEntity(
            id = 4, ticker = "PLTR", companyName = "Palantir Technologies",
            quantity = 5.5, purchasePrice = 146.4, currentPrice = 156.54,
        )
        val json = JSONObject(NotionMapper.investmentProperties(inv))
        assertEquals(
            "PLTR",
            json.getJSONObject("Ticker").getJSONArray("title")
                .getJSONObject(0).getJSONObject("text").getString("content"),
        )
        assertEquals(
            "Palantir Technologies",
            json.getJSONObject("Empresa").getJSONArray("rich_text")
                .getJSONObject(0).getJSONObject("text").getString("content"),
        )
        assertEquals(5.5, json.getJSONObject("Cantidad").getDouble("number"), 0.0)
    }

    @Test
    fun `spentFor suma solo gastos de la categoria y el mes`() {
        val txs = listOf(
            tx(amount = 100_000.0, date = "2026-07-05"),
            tx(amount = 50_000.0, date = "2026-07-20"),
            tx(amount = 999.0, date = "2026-06-30"),                       // otro mes
            tx(amount = 999.0, date = "2026-07-10", category = "Ocio"),    // otra categoría
            tx(amount = 999.0, date = "2026-07-11", type = "INCOME"),      // ingreso, no gasto
            tx(amount = Double.POSITIVE_INFINITY, date = "2026-07-12"),    // no finito → 0
        )
        assertEquals(150_000.0, NotionMapper.spentFor(txs, "Vivienda", 7, 2026), 0.001)
        assertTrue(NotionMapper.spentFor(txs, "Inexistente", 7, 2026) == 0.0)
    }
}
