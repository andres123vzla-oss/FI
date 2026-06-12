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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvestment(investment: InvestmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvestments(investments: List<InvestmentEntity>)

    @Update
    suspend fun updateInvestment(investment: InvestmentEntity)

    @Query("DELETE FROM investments WHERE id = :id")
    suspend fun deleteInvestmentById(id: Int)

    @Query("DELETE FROM investments")
    suspend fun clearAllInvestments()
}
