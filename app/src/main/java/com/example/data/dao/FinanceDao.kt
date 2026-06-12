package com.example.data.dao

import androidx.room.*
import com.example.data.entity.BudgetEntity
import com.example.data.entity.CategoryEntity
import com.example.data.entity.InvestmentEntity
import com.example.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {

    // --- Transactions ---
    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Int)

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

    // --- Categories ---
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryById(id: Int)

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryName = :categoryName")
    suspend fun getTransactionCountForCategory(categoryName: String): Int

    @Query("DELETE FROM categories")
    suspend fun clearAllCategories()

    // --- Budgets ---
    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE month = :month AND year = :year")
    fun getBudgetsForMonthAndYear(month: Int, year: Int): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgets(budgets: List<BudgetEntity>)

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudgetById(id: Int)

    @Query("DELETE FROM budgets")
    suspend fun clearAllBudgets()

    // --- Investments ---
    @Query("SELECT * FROM investments ORDER BY ticker ASC")
    fun getAllInvestments(): Flow<List<InvestmentEntity>>

    /**
     * C1 (A3 dedupe inversiones): devuelve la posición existente para un ticker (o null).
     * Lo usa FinanceRepository.addOrMergeInvestment para decidir si fusiona o inserta.
     */
    @Query("SELECT * FROM investments WHERE ticker = :ticker LIMIT 1")
    suspend fun getInvestmentByTicker(ticker: String): InvestmentEntity?

    // A3: NUNCA OnConflictStrategy.REPLACE en investments (borraría la posición previa al
    // chocar el índice único en ticker). Se usa ABORT; la fusión la maneja el repositorio.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInvestment(investment: InvestmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInvestments(investments: List<InvestmentEntity>)

    @Update
    suspend fun updateInvestment(investment: InvestmentEntity)

    @Query("DELETE FROM investments WHERE id = :id")
    suspend fun deleteInvestmentById(id: Int)

    @Query("DELETE FROM investments")
    suspend fun clearAllInvestments()

    // --- Operaciones atómicas (A2: seed/clear multi-tabla sin estado inconsistente) ---

    /**
     * A2: borra las 4 tablas y reinserta el conjunto completo en una sola transacción.
     * Room hace rollback ante CancellationException o cualquier excepción, garantizando
     * todo-o-nada. Más rápido que ~32 operaciones sueltas.
     */
    @Transaction
    suspend fun replaceAllData(
        categories: List<CategoryEntity>,
        budgets: List<BudgetEntity>,
        investments: List<InvestmentEntity>,
        transactions: List<TransactionEntity>,
    ) {
        clearAllTransactions()
        clearAllCategories()
        clearAllBudgets()
        clearAllInvestments()
        insertCategories(categories)
        insertBudgets(budgets)
        insertInvestments(investments)
        transactions.forEach { insertTransaction(it) }
    }

    /** A2: borra las 4 tablas de forma atómica. */
    @Transaction
    suspend fun clearAll() {
        clearAllTransactions()
        clearAllCategories()
        clearAllBudgets()
        clearAllInvestments()
    }
}
