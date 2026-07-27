package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupManager
import com.example.data.database.AppDatabase
import com.example.data.repository.FinanceRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.AEADBadTagException
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
 * Test de EXTREMO A EXTREMO del respaldo sobre Room real (estilo SeedDataRegressionTest):
 * exportar → borrar todo → importar debe devolver la base EXACTA (las 4 tablas, con ids).
 * Además blinda la garantía SEC-09/A1: un import fallido (passphrase errada) NO toca la BD.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupRoundTripTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "test_backup_roundtrip_db"
    private val passphrase = "mi-passphrase-de-respaldo"

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
    fun `exportar, borrar todo e importar restaura las 4 tablas exactas`() = runBlocking {
        val db = openDb()
        try {
            val repo = FinanceRepository(db.financeDao())
            val manager = BackupManager(repo)

            // Estado conocido: el seed demo manual (totales del CLAUDE.md).
            repo.restoreSeedData()
            val txAntes = repo.allTransactions.first().sortedBy { it.id }
            val catAntes = repo.allCategories.first().sortedBy { it.id }
            val budAntes = repo.allBudgets.first().sortedBy { it.id }
            val invAntes = repo.allInvestments.first().sortedBy { it.id }
            assertTrue(txAntes.isNotEmpty())

            // Export a memoria (el flujo real usa un stream de SAF; el formato es idéntico).
            val out = ByteArrayOutputStream()
            manager.exportTo(out, passphrase.toCharArray())
            val archivo = out.toByteArray()

            // Catástrofe simulada: se pierde todo (equivale a teléfono nuevo/Keystore invalidado).
            repo.clearAllData()
            assertTrue(repo.allTransactions.first().isEmpty())

            // Import: la base vuelve EXACTA (ids y timestamps incluidos; orden normalizado por id).
            val importado = manager.importFrom(ByteArrayInputStream(archivo), passphrase.toCharArray())
            assertEquals(txAntes, repo.allTransactions.first().sortedBy { it.id })
            assertEquals(catAntes, repo.allCategories.first().sortedBy { it.id })
            assertEquals(budAntes, repo.allBudgets.first().sortedBy { it.id })
            assertEquals(invAntes, repo.allInvestments.first().sortedBy { it.id })

            // Totales de referencia del proyecto (CLAUDE.md).
            val tx = repo.allTransactions.first()
            val income = tx.filter { it.type == "INCOME" }.sumOf { it.amount }
            val expense = tx.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            assertEquals(1_090_094.0, income, 0.001)
            assertEquals(748_825.0, expense, 0.001)
            assertEquals(txAntes.size, importado.transactions.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `import con passphrase incorrecta falla y deja la base intacta`() = runBlocking {
        val db = openDb()
        try {
            val repo = FinanceRepository(db.financeDao())
            val manager = BackupManager(repo)
            repo.restoreSeedData()
            val txAntes = repo.allTransactions.first().sortedBy { it.id }

            val out = ByteArrayOutputStream()
            manager.exportTo(out, passphrase.toCharArray())

            assertThrows(AEADBadTagException::class.java) {
                runBlocking {
                    manager.importFrom(
                        ByteArrayInputStream(out.toByteArray()),
                        "passphrase-equivocada".toCharArray(),
                    )
                }
            }
            // Garantía SEC-09/A1: nada se tocó.
            assertEquals(txAntes, repo.allTransactions.first().sortedBy { it.id })
        } finally {
            db.close()
        }
    }

    @Test
    fun `un respaldo de una app vacia importa una base vacia`() = runBlocking {
        val db = openDb()
        try {
            val repo = FinanceRepository(db.financeDao())
            val manager = BackupManager(repo)

            // Export con la app recién instalada (vacía por diseño).
            val out = ByteArrayOutputStream()
            val exportado = manager.exportTo(out, passphrase.toCharArray())
            assertTrue(exportado.isEmpty)

            // Con datos presentes, importar ese respaldo vacío deja la base vacía (es el estado
            // que el usuario respaldó; el flujo de UI ya advirtió que el import REEMPLAZA todo).
            repo.restoreSeedData()
            manager.importFrom(ByteArrayInputStream(out.toByteArray()), passphrase.toCharArray())
            assertTrue(repo.allTransactions.first().isEmpty())
            assertTrue(repo.allCategories.first().isEmpty())
            assertTrue(repo.allBudgets.first().isEmpty())
            assertTrue(repo.allInvestments.first().isEmpty())
        } finally {
            db.close()
        }
    }
}
