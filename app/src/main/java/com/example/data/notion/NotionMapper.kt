package com.example.data.notion

import com.example.data.entity.BudgetEntity
import com.example.data.entity.InvestmentEntity
import com.example.data.entity.RecurringRuleEntity
import com.example.data.entity.TransactionEntity
import com.example.domain.Clp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mapeo entidades → propiedades de página del API de Notion (JSON). Puro (`org.json`):
 * testeable con Robolectric (NotionMapperTest).
 *
 * Los NOMBRES de propiedad deben coincidir con el esquema de las bases espejo de la página
 * "Finanzas" del usuario (documentadas en CLAUDE.md): Descripción (title), Fecha (date),
 * Tipo (select Ingreso/Gasto), Categoría (select — Notion crea la opción si no existe),
 * Monto CLP (number), Origen (select), etc. Números saneados con [Clp.sanitize]
 * (NaN/Infinity jamás viajan).
 */
object NotionMapper {

    fun movementProperties(t: TransactionEntity): String = JSONObject()
        .put("Descripción", title(t.description.ifBlank { "(sin descripción)" }))
        .put("Fecha", date(t.date))
        .put("Tipo", select(if (t.type == "INCOME") "Ingreso" else "Gasto"))
        .put("Categoría", select(t.categoryName))
        .put("Monto CLP", number(t.amount))
        .put("Origen", select("App"))
        .toString()

    fun recurringProperties(r: RecurringRuleEntity): String = JSONObject()
        .put("Descripción", title(r.description))
        .put("Tipo", select(if (r.type == "INCOME") "Ingreso" else "Gasto"))
        .put("Categoría", select(r.categoryName))
        .put("Monto CLP", number(r.amount))
        .put("Día del mes", number(r.dayOfMonth.toDouble()))
        .put("Activa", JSONObject().put("checkbox", r.active))
        .toString()

    fun budgetProperties(b: BudgetEntity, spentClp: Double): String = JSONObject()
        .put("Categoría", title(b.categoryName))
        .put("Mes", date("%04d-%02d-01".format(b.year, b.month)))
        .put("Presupuestado CLP", number(b.plannedAmount))
        .put("Gastado CLP", number(spentClp))
        .toString()

    fun investmentProperties(i: InvestmentEntity): String = JSONObject()
        .put("Ticker", title(i.ticker))
        .put("Empresa", JSONObject().put("rich_text", JSONArray().put(textContent(i.companyName))))
        .put("Cantidad", number(i.quantity))
        .put("Precio compra USD", number(i.purchasePrice))
        .put("Precio actual USD", number(i.currentPrice))
        .toString()

    /** Gasto real del mes por categoría (columna "Gastado CLP" del presupuesto espejo). */
    fun spentFor(
        transactions: List<TransactionEntity>,
        categoryName: String,
        month: Int,
        year: Int,
    ): Double {
        val prefix = "%04d-%02d-".format(year, month)
        return transactions
            .filter { it.type == "EXPENSE" && it.categoryName == categoryName && it.date.startsWith(prefix) }
            .sumOf { Clp.sanitize(it.amount) }
    }

    // --- Bloques primitivos del API de Notion ---

    private fun title(text: String): JSONObject =
        JSONObject().put("title", JSONArray().put(textContent(text)))

    private fun textContent(text: String): JSONObject =
        JSONObject().put("text", JSONObject().put("content", text.take(MAX_TEXT)))

    private fun select(name: String): JSONObject =
        JSONObject().put("select", JSONObject().put("name", name.take(MAX_SELECT)))

    private fun date(isoDate: String): JSONObject =
        JSONObject().put("date", JSONObject().put("start", isoDate))

    private fun number(value: Double): JSONObject =
        JSONObject().put("number", Clp.sanitize(value))

    private const val MAX_TEXT = 2000 // límite de un bloque rich_text/title de Notion
    private const val MAX_SELECT = 100 // límite del nombre de una opción select
}
