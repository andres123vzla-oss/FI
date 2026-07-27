package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.RecurringRuleEntity
import com.example.data.notion.NotionApiException
import com.example.data.notion.NotionClient
import com.example.data.notion.NotionSyncConfig
import com.example.data.notion.NotionSyncManager
import com.example.data.repository.FinanceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests de la sync one-way app→Notion sobre Room real y un cliente FALSO (sin red).
 * Blindan las garantías clave: primera sync crea todo y persiste los pageId; la segunda solo
 * actualiza (cero duplicados); y un fallo a mitad es REANUDABLE sin duplicar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotionSyncManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "test_notion_sync_db"
    private val config = NotionSyncConfig(
        movimientosDatabaseId = "db-mov",
        recurrentesDatabaseId = "db-rec",
        presupuestoDatabaseId = "db-pre",
        portafolioDatabaseId = "db-por",
    )

    private class FakeNotionClient : NotionClient {
        val created = mutableListOf<String>() // databaseId por página creada
        val updated = mutableListOf<String>() // pageId por página actualizada
        private var nextId = 1
        override suspend fun createPage(databaseId: String, propertiesJson: String): String {
            created += databaseId
            return "pg-${nextId++}"
        }
        override suspend fun updatePage(pageId: String, propertiesJson: String) {
            updated += pageId
        }
    }

    /** Falla (como la red real) después de N creaciones exitosas. */
    private class FailingClient(private val failAfter: Int) : NotionClient {
        var creations = 0
        override suspend fun createPage(databaseId: String, propertiesJson: String): String {
            if (creations >= failAfter) throw NotionApiException("Sin conexión con Notion.")
            creations++
            return "pg-parcial-$creations"
        }
        override suspend fun updatePage(pageId: String, propertiesJson: String) = Unit
    }

    private fun openDb(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()

    @Before
    fun clean() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `primera sync crea todo con destino correcto y la segunda solo actualiza`() = runBlocking {
        val db = openDb()
        try {
            val repo = FinanceRepository(db.financeDao())
            repo.restoreSeedData() // 13 movimientos, 8 presupuestos, 8 activos
            repo.addRecurringRule(
                RecurringRuleEntity(
                    type = "EXPENSE", categoryName = "Vivienda", description = "Arriendo",
                    amount = 458_000.0, dayOfMonth = 1, lastYear = 2026, lastMonth = 7,
                ),
            )

            val fake = FakeNotionClient()
            val primera = NotionSyncManager(repo, fake, config).syncAll()

            assertEquals(30, primera.created) // 13 + 1 + 8 + 8
            assertEquals(0, primera.updated)
            assertEquals(13, fake.created.count { it == "db-mov" })
            assertEquals(1, fake.created.count { it == "db-rec" })
            assertEquals(8, fake.created.count { it == "db-pre" })
            assertEquals(8, fake.created.count { it == "db-por" })
            // Los pageId quedaron persistidos en las filas locales.
            assertTrue(repo.allTransactions.first().all { it.notionPageId != null })
            assertTrue(repo.allRecurringOnce().all { it.notionPageId != null })

            // Segunda pasada: cero creaciones nuevas, todo se actualiza (upsert real).
            val segunda = NotionSyncManager(repo, fake, config).syncAll()
            assertEquals(0, segunda.created)
            assertEquals(30, segunda.updated)
        } finally {
            db.close()
        }
    }

    @Test
    fun `un fallo a mitad es reanudable sin duplicar paginas`() = runBlocking {
        val db = openDb()
        try {
            val repo = FinanceRepository(db.financeDao())
            repo.restoreSeedData()

            // Falla tras 5 creaciones: la sync lanza, pero esos 5 pageId YA quedaron guardados.
            assertThrows(NotionApiException::class.java) {
                runBlocking { NotionSyncManager(repo, FailingClient(failAfter = 5), config).syncAll() }
            }
            assertEquals(5, repo.allTransactions.first().count { it.notionPageId != null })

            // Reintento con red sana: crea SOLO lo que faltaba y actualiza lo ya creado.
            val fake = FakeNotionClient()
            val retry = NotionSyncManager(repo, fake, config).syncAll()
            assertEquals(29 - 5, retry.created) // 13+8+8 = 29 filas del seed, 5 ya creadas
            assertEquals(5, retry.updated)
        } finally {
            db.close()
        }
    }
}
