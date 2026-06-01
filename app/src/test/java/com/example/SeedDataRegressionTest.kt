package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.repository.FinanceRepository
import com.example.domain.PortfolioMetrics
import com.example.ui.viewmodel.DashboardDetails
import com.example.ui.viewmodel.InvestmentSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regresión: la app debe arrancar y permanecer VACÍA. No existe ninguna ruta de auto-siembra;
 * los datos de ejemplo solo se cargan con la acción manual [FinanceRepository.restoreSeedData].
 *
 * Estos tests blindan el fix que eliminó `ensureSeedData()` (auto-seed en el init del ViewModel)
 * y el fallback hardcodeado de presupuestos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SeedDataRegressionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "test_finance_regression_db"

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

    // 1. Primer arranque: base completamente vacía.
    @Test
    fun `first launch starts empty`() = runBlocking {
        val db = openDb()
        val repo = FinanceRepository(db.financeDao())
        assertTrue(repo.allTransactions.first().isEmpty())
        assertTrue(repo.allCategories.first().isEmpty())
        assertTrue(repo.allBudgets.first().isEmpty())
        assertTrue(repo.allInvestments.first().isEmpty())
        db.close()
    }

    // 2. Una base vacía NO dispara siembra automática (leer los flujos no inserta nada).
    @Test
    fun `empty database does not trigger automatic seed`() = runBlocking {
        val db = openDb()
        val repo = FinanceRepository(db.financeDao())
        repeat(3) {
            repo.allCategories.first()
            repo.allTransactions.first()
            repo.allBudgets.first()
            repo.allInvestments.first()
        }
        assertEquals(0, repo.allCategories.first().size)
        assertEquals(0, repo.allTransactions.first().size)
        assertEquals(0, repo.allBudgets.first().size)
        assertEquals(0, repo.allInvestments.first().size)
        db.close()
    }

    // 3. Borrar todo + "reiniciar" (nueva instancia/repositorio) NO vuelve a sembrar.
    @Test
    fun `deleting all data does not reseed on next startup`() = runBlocking {
        // El usuario carga datos demo manualmente y luego borra todo.
        val db1 = openDb()
        val repo1 = FinanceRepository(db1.financeDao())
        repo1.restoreSeedData()
        assertTrue(repo1.allCategories.first().isNotEmpty())
        repo1.clearAllData()
        assertTrue(repo1.allTransactions.first().isEmpty())
        db1.close() // simula cierre de la app

        // "Reinicio": nueva instancia de BD/repositorio sobre el mismo archivo.
        val db2 = openDb()
        val repo2 = FinanceRepository(db2.financeDao())
        assertTrue(repo2.allTransactions.first().isEmpty())
        assertTrue(repo2.allCategories.first().isEmpty())
        assertTrue(repo2.allBudgets.first().isEmpty())
        assertTrue(repo2.allInvestments.first().isEmpty())
        db2.close()
    }

    // Extra: la restauración MANUAL sí inserta el set de ejemplo con los totales esperados.
    @Test
    fun `manual restore inserts the demo seed set with expected totals`() = runBlocking {
        val db = openDb()
        val repo = FinanceRepository(db.financeDao())
        repo.restoreSeedData()
        val tx = repo.allTransactions.first()
        val income = tx.filter { it.type == "INCOME" }.sumOf { it.amount }
        val expense = tx.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        assertEquals(1_090_094.0, income, 0.001)
        assertEquals(748_825.0, expense, 0.001)
        assertEquals(341_269.0, income - expense, 0.001)
        assertTrue(repo.allCategories.first().isNotEmpty())
        assertTrue(repo.allInvestments.first().isNotEmpty())
        db.close()
    }

    // 4. Estado del Dashboard sin datos: ceros y listas vacías, nunca valores demo.
    @Test
    fun `dashboard state with empty data returns zero not demo values`() {
        val d = DashboardDetails()
        assertEquals(0.0, d.incomeTotal, 0.0)
        assertEquals(0.0, d.expenseTotal, 0.0)
        assertEquals(0.0, d.balanceTotal, 0.0)
        assertEquals(0.0, d.portfolioTotal, 0.0)
        assertTrue(d.categorySpendingList.isEmpty())
        assertTrue(d.monthlySummaries.isEmpty())
        assertEquals(null, d.topCategoryName)
    }

    // 5. Estado del Portafolio sin datos: lista vacía, nunca acciones demo.
    @Test
    fun `portfolio state with empty data returns empty list not demo stocks`() {
        val summary = InvestmentSummary()
        assertTrue(summary.stocks.isEmpty())
        assertEquals(0.0, summary.totalCurrent, 0.0)
        assertEquals(0.0, summary.totalInvested, 0.0)

        val metrics = PortfolioMetrics()
        assertTrue(metrics.stocks.isEmpty())
    }
}
