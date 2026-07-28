package com.example.data.notion

import com.example.data.repository.FinanceRepository
import kotlinx.coroutines.flow.first

/**
 * IDs de las bases espejo del dueño en Notion (página "Finanzas"; ver CLAUDE.md). No son
 * secretos — el secreto es el TOKEN, que viaja aparte y se guarda cifrado.
 */
data class NotionSyncConfig(
    val movimientosDatabaseId: String = "3ab443f0434381d79f9acd92a61e05cb",
    val recurrentesDatabaseId: String = "3ab443f04343813b93bdf9afcfb27110",
    val presupuestoDatabaseId: String = "3ab443f04343814d9a06e038983d6a66",
    val portafolioDatabaseId: String = "3ab443f043438168b85ef1a39eb51d36",
)

/**
 * Sync ONE-WAY app → Notion (la app es la fuente de verdad; jamás se lee ni borra en Notion).
 *
 * Upsert sin duplicados: cada fila local guarda su `notionPageId`. Sin id → se CREA la página
 * y el id se persiste de inmediato (update parcial del DAO); con id → se ACTUALIZA la página.
 * Propiedad clave: la sync es REANUDABLE — si falla a mitad (red, 429), lo ya creado quedó con
 * su id guardado y el reintento no duplica nada.
 *
 * El presupuesto además calcula "Gastado CLP" real del mes desde los movimientos, para que la
 * fórmula "Disponible" del espejo funcione.
 */
class NotionSyncManager(
    private val repository: FinanceRepository,
    private val client: NotionClient,
    private val config: NotionSyncConfig = NotionSyncConfig(),
) {

    data class Summary(val created: Int, val updated: Int) {
        val total: Int get() = created + updated
    }

    suspend fun syncAll(): Summary {
        var created = 0
        var updated = 0

        // Movimientos (también alimentan el "Gastado CLP" del presupuesto).
        val transactions = repository.allTransactions.first()
        for (t in transactions) {
            val props = NotionMapper.movementProperties(t)
            val pageId = t.notionPageId
            if (pageId == null) {
                repository.setTransactionNotionId(
                    t.id,
                    client.createPage(config.movimientosDatabaseId, props),
                )
                created++
            } else {
                client.updatePage(pageId, props)
                updated++
            }
        }

        for (r in repository.allRecurringOnce()) {
            val props = NotionMapper.recurringProperties(r)
            val pageId = r.notionPageId
            if (pageId == null) {
                repository.setRecurringNotionId(
                    r.id,
                    client.createPage(config.recurrentesDatabaseId, props),
                )
                created++
            } else {
                client.updatePage(pageId, props)
                updated++
            }
        }

        for (b in repository.allBudgets.first()) {
            val spent = NotionMapper.spentFor(transactions, b.categoryName, b.month, b.year)
            val props = NotionMapper.budgetProperties(b, spent)
            val pageId = b.notionPageId
            if (pageId == null) {
                repository.setBudgetNotionId(
                    b.id,
                    client.createPage(config.presupuestoDatabaseId, props),
                )
                created++
            } else {
                client.updatePage(pageId, props)
                updated++
            }
        }

        for (i in repository.allInvestments.first()) {
            val props = NotionMapper.investmentProperties(i)
            val pageId = i.notionPageId
            if (pageId == null) {
                repository.setInvestmentNotionId(
                    i.id,
                    client.createPage(config.portafolioDatabaseId, props),
                )
                created++
            } else {
                client.updatePage(pageId, props)
                updated++
            }
        }

        return Summary(created, updated)
    }
}
