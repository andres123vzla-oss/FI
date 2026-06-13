package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.repository.FinanceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FIN2-09: red de regresión para [FinanceRepository.addOrMergeInvestment] — la fórmula del
 * promedio ponderado determina el costo base y por tanto TODA la ganancia/pérdida reportada
 * del portafolio, y no tenía ningún test. También cubre la normalización de ticker y el caso
 * degenerado de cantidad 0 (sin NaN), y de paso ejercita la vía transaccional ARQ2-02
 * ([com.example.data.dao.FinanceDao.upsertInvestmentMergingByTicker]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InvestmentMergeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "test_investment_merge_db"

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
    fun `fusion pondera el precio de compra por cantidad`() = runBlocking {
        val db = openDb()
        val repo = FinanceRepository(db.financeDao())
        repo.addOrMergeInvestment("AAPL", "Apple", quantity = 10.0, purchasePrice = 100.0, currentPrice = 110.0)
        repo.addOrMergeInvestment("AAPL", "Apple", quantity = 10.0, purchasePrice = 200.0, currentPrice = 120.0)

        val stocks = repo.allInvestments.first()
        assertEquals(1, stocks.size)
        val s = stocks.single()
        assertEquals(20.0, s.quantity, 0.0001)
        assertEquals(150.0, s.purchasePrice, 0.0001) // (10*100 + 10*200) / 20
        assertEquals(120.0, s.currentPrice, 0.0001) // siempre el más reciente
        db.close()
    }

    @Test
    fun `ticker se normaliza antes de fusionar`() = runBlocking {
        val db = openDb()
        val repo = FinanceRepository(db.financeDao())
        repo.addOrMergeInvestment("AAPL", "Apple", 10.0, 100.0, 110.0)
        repo.addOrMergeInvestment(" aapl ", "Apple", 5.0, 100.0, 110.0)

        val stocks = repo.allInvestments.first()
        assertEquals(1, stocks.size)
        assertEquals("AAPL", stocks.single().ticker)
        assertEquals(15.0, stocks.single().quantity, 0.0001)
        db.close()
    }

    @Test
    fun `caso degenerado cantidad total cero conserva el precio nuevo sin NaN`() = runBlocking {
        val db = openDb()
        val repo = FinanceRepository(db.financeDao())
        repo.addOrMergeInvestment("ZERO", "Zero Corp", 0.0, 100.0, 100.0)
        repo.addOrMergeInvestment("ZERO", "Zero Corp", 0.0, 200.0, 210.0)

        val s = repo.allInvestments.first().single()
        assertEquals(0.0, s.quantity, 0.0)
        assertEquals(200.0, s.purchasePrice, 0.0001) // fallback: precio nuevo, no NaN
        db.close()
    }

    @Test
    fun `tickers distintos no se fusionan`() = runBlocking {
        val db = openDb()
        val repo = FinanceRepository(db.financeDao())
        repo.addOrMergeInvestment("AAPL", "Apple", 1.0, 100.0, 100.0)
        repo.addOrMergeInvestment("MSFT", "Microsoft", 2.0, 300.0, 310.0)
        assertEquals(2, repo.allInvestments.first().size)
        db.close()
    }
}
