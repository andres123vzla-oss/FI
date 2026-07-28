package com.example.data.dao

import androidx.room.*
import com.example.data.entity.BudgetEntity
import com.example.data.entity.CategoryEntity
import com.example.data.entity.InvestmentEntity
import com.example.data.entity.RecurringRuleEntity
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

    // ARQ2-05: ABORT (no REPLACE) sobre el índice único (name, type): un nombre duplicado lanza
    // SQLiteConstraintException que el ViewModel captura y traduce a mensaje de error, en vez de
    // machacar la categoría existente.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: CategoryEntity): Long

    // Solo para el seed (replaceAllData): corre tras clearAll, sin conflictos posibles.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    /**
     * ARQ2-06: renombrar una categoría propagando el cambio a movimientos y presupuestos en una
     * sola transacción. El esquema referencia categorías por nombre (sin FK), así que un rename
     * sin cascada deja huérfanos: movimientos sin icono/filtro y presupuestos divididos.
     * El caller debe validar antes que [new] no colisione con otra categoría existente.
     */
    @Query("UPDATE transactions SET categoryName = :new WHERE categoryName = :old")
    suspend fun renameTransactionsCategory(old: String, new: String)

    @Query("UPDATE budgets SET categoryName = :new WHERE categoryName = :old")
    suspend fun renameBudgetsCategory(old: String, new: String)

    @Transaction
    suspend fun renameCategoryCascade(updated: CategoryEntity, oldName: String) {
        updateCategory(updated)
        renameTransactionsCategory(oldName, updated.name)
        renameBudgetsCategory(oldName, updated.name)
    }

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryById(id: Int)

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryName = :categoryName")
    suspend fun getTransactionCountForCategory(categoryName: String): Int

    // UX2-04: salvaguarda de borrado — una categoría con presupuestos no debe eliminarse
    // (quedarían presupuestos huérfanos que siguen sumando en los análisis).
    @Query("SELECT COUNT(*) FROM budgets WHERE categoryName = :categoryName")
    suspend fun getBudgetCountForCategory(categoryName: String): Int

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

    /**
     * ARQ2-04: actualización PARCIAL de precio (solo currentPrice/updatedAt). El refresco de
     * precios trabaja sobre un snapshot que puede quedar estancado durante el ciclo de red; un
     * @Update de fila completa sobrescribiría quantity/purchasePrice editados por el usuario
     * mientras tanto (lost update). Sobre una fila borrada afecta 0 filas sin error.
     */
    @Query("UPDATE investments SET currentPrice = :price, updatedAt = :now WHERE id = :id")
    suspend fun updateInvestmentPrice(id: Int, price: Double, now: Long)

    /**
     * ARQ2-02: fusión/inserción por ticker DENTRO de una transacción. El check-then-act fuera de
     * transacción permitía que dos llamadas concurrentes (doble tap en "Invertir") vieran ambas
     * existing == null: la segunda chocaba con el índice único (ABORT) y crasheaba la app, o un
     * merge concurrente perdía un lote. Room serializa los escritores dentro de @Transaction.
     * La lógica de fusión (promedio ponderado) vive en el caller vía [merge].
     */
    @Transaction
    suspend fun upsertInvestmentMergingByTicker(
        candidate: InvestmentEntity,
        merge: (existing: InvestmentEntity, incoming: InvestmentEntity) -> InvestmentEntity,
    ) {
        val existing = getInvestmentByTicker(candidate.ticker)
        if (existing == null) {
            insertInvestment(candidate)
        } else {
            updateInvestment(merge(existing, candidate))
        }
    }

    @Query("DELETE FROM investments WHERE id = :id")
    suspend fun deleteInvestmentById(id: Int)

    @Query("DELETE FROM investments")
    suspend fun clearAllInvestments()

    // --- Movimientos recurrentes (P1-1) ---

    @Query("SELECT * FROM recurring_rules ORDER BY dayOfMonth ASC, description ASC")
    fun getAllRecurring(): Flow<List<RecurringRuleEntity>>

    /** One-shot para el generador y el respaldo (sin suscribirse a un Flow). */
    @Query("SELECT * FROM recurring_rules")
    suspend fun getAllRecurringOnce(): List<RecurringRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurring(rule: RecurringRuleEntity): Long

    // Solo para restauración de respaldo (corre tras clearAll, sin conflictos posibles).
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurrings(rules: List<RecurringRuleEntity>)

    @Update
    suspend fun updateRecurring(rule: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun deleteRecurringById(id: Int)

    @Query("DELETE FROM recurring_rules")
    suspend fun clearAllRecurring()

    /**
     * P1-1: aplica una pasada del generador de recurrentes de forma ATÓMICA: inserta los
     * movimientos del período y avanza el ancla (lastYear/lastMonth) de cada regla en la MISMA
     * transacción. Sin esto, un fallo a mitad dejaría o el movimiento sin ancla (duplicado al
     * reintentar) o el ancla sin movimiento (mes perdido).
     */
    @Transaction
    suspend fun applyGenerated(
        transactions: List<TransactionEntity>,
        updatedRules: List<RecurringRuleEntity>,
    ) {
        transactions.forEach { insertTransaction(it) }
        updatedRules.forEach { updateRecurring(it) }
    }

    /**
     * P1-2: importación de cartola — inserta el lote completo en UNA transacción (todo o nada).
     * Solo agrega; jamás borra ni modifica movimientos existentes.
     */
    @Transaction
    suspend fun insertTransactionsAtomic(transactions: List<TransactionEntity>) {
        transactions.forEach { insertTransaction(it) }
    }

    // --- Sync Notion: guardar el id de página espejo (update PARCIAL, patrón ARQ2-04) ---
    // Solo toca notionPageId: la sync corre sobre un snapshot y un @Update de fila completa
    // pisaría ediciones concurrentes del usuario (lost update).

    @Query("UPDATE transactions SET notionPageId = :pageId WHERE id = :id")
    suspend fun setTransactionNotionId(id: Int, pageId: String)

    @Query("UPDATE budgets SET notionPageId = :pageId WHERE id = :id")
    suspend fun setBudgetNotionId(id: Int, pageId: String)

    @Query("UPDATE investments SET notionPageId = :pageId WHERE id = :id")
    suspend fun setInvestmentNotionId(id: Int, pageId: String)

    @Query("UPDATE recurring_rules SET notionPageId = :pageId WHERE id = :id")
    suspend fun setRecurringNotionId(id: Int, pageId: String)

    // --- Operaciones atómicas (A2: seed/clear multi-tabla sin estado inconsistente) ---

    /**
     * A2: borra las 5 tablas y reinserta el conjunto completo en una sola transacción.
     * Room hace rollback ante CancellationException o cualquier excepción, garantizando
     * todo-o-nada. (P1-1: incluye las reglas recurrentes, para el import del respaldo.)
     */
    @Transaction
    suspend fun replaceAllData(
        categories: List<CategoryEntity>,
        budgets: List<BudgetEntity>,
        investments: List<InvestmentEntity>,
        transactions: List<TransactionEntity>,
        recurring: List<RecurringRuleEntity>,
    ) {
        clearAllTransactions()
        clearAllCategories()
        clearAllBudgets()
        clearAllInvestments()
        clearAllRecurring()
        insertCategories(categories)
        insertBudgets(budgets)
        insertInvestments(investments)
        transactions.forEach { insertTransaction(it) }
        insertRecurrings(recurring)
    }

    /** A2: borra las 5 tablas de forma atómica (P1-1: recurrentes incluidas). */
    @Transaction
    suspend fun clearAll() {
        clearAllTransactions()
        clearAllCategories()
        clearAllBudgets()
        clearAllInvestments()
        clearAllRecurring()
    }
}
