package com.example.data.repository

import com.example.data.dao.FinanceDao
import com.example.data.entity.BudgetEntity
import com.example.data.entity.CategoryEntity
import com.example.data.entity.InvestmentEntity
import com.example.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

class FinanceRepository(private val dao: FinanceDao) {

    val allTransactions: Flow<List<TransactionEntity>> = dao.getAllTransactions()
    val allCategories: Flow<List<CategoryEntity>> = dao.getAllCategories()
    val allBudgets: Flow<List<BudgetEntity>> = dao.getAllBudgets()
    val allInvestments: Flow<List<InvestmentEntity>> = dao.getAllInvestments()

    fun getBudgetsForMonthAndYear(month: Int, year: Int): Flow<List<BudgetEntity>> =
        dao.getBudgetsForMonthAndYear(month, year)

    /**
     * Carga el conjunto de datos de ejemplo. ACCIÓN MANUAL EXCLUSIVAMENTE: se invoca solo desde
     * "Restaurar datos demo" en Ajustes/Dashboard (con reautenticación). NO existe ninguna ruta
     * automática que llame a esta función; una base vacía nunca se re-siembra sola.
     */
    suspend fun restoreSeedData() {
        // Clear everything first
        dao.clearAllTransactions()
        dao.clearAllCategories()
        dao.clearAllBudgets()
        dao.clearAllInvestments()

        // 1. Core Categories
        val defaultCategories = listOf(
            // Incomes
            CategoryEntity(name = "Sueldo", type = "INCOME", colorHex = "#107C41", iconName = "AttachMoney", isDefault = true),
            CategoryEntity(name = "Freelance", type = "INCOME", colorHex = "#1B5E20", iconName = "Work", isDefault = true),
            CategoryEntity(name = "Inversiones", type = "INCOME", colorHex = "#0D47A1", iconName = "TrendingUp", isDefault = true),
            CategoryEntity(name = "Ventas", type = "INCOME", colorHex = "#E65100", iconName = "Storefront", isDefault = true),
            CategoryEntity(name = "Bonos", type = "INCOME", colorHex = "#311B92", iconName = "EmojiEvents", isDefault = true),
            CategoryEntity(name = "Otros", type = "INCOME", colorHex = "#37474F", iconName = "MoreHoriz", isDefault = true),
            CategoryEntity(name = "Otros ingresos", type = "INCOME", colorHex = "#558B2F", iconName = "AccountBalanceWallet", isDefault = true),
            CategoryEntity(name = "Propinas", type = "INCOME", colorHex = "#00838F", iconName = "Handshake", isDefault = true),

            // Expenses
            CategoryEntity(name = "Vivienda", type = "EXPENSE", colorHex = "#C62828", iconName = "Home", isDefault = true),
            CategoryEntity(name = "Servicios", type = "EXPENSE", colorHex = "#AD1457", iconName = "Power", isDefault = true),
            CategoryEntity(name = "Alimentación", type = "EXPENSE", colorHex = "#2E7D32", iconName = "Restaurant", isDefault = true),
            CategoryEntity(name = "Transporte", type = "EXPENSE", colorHex = "#EF6C00", iconName = "DirectionsCar", isDefault = true),
            CategoryEntity(name = "Entretenimiento", type = "EXPENSE", colorHex = "#6A1B9A", iconName = "Celebration", isDefault = true),
            CategoryEntity(name = "Salud", type = "EXPENSE", colorHex = "#0277BD", iconName = "MedicalServices", isDefault = true),
            CategoryEntity(name = "Educación", type = "EXPENSE", colorHex = "#1565C0", iconName = "School", isDefault = true),
            CategoryEntity(name = "Ropa", type = "EXPENSE", colorHex = "#37474F", iconName = "Checkroom", isDefault = true),
            CategoryEntity(name = "Ahorro", type = "EXPENSE", colorHex = "#00695C", iconName = "Savings", isDefault = true),
            CategoryEntity(name = "Otros", type = "EXPENSE", colorHex = "#4E342E", iconName = "Category", isDefault = true),
            CategoryEntity(name = "Micro", type = "EXPENSE", colorHex = "#283593", iconName = "DirectionsBus", isDefault = false),
            CategoryEntity(name = "Uber", type = "EXPENSE", colorHex = "#311B92", iconName = "LocalTaxi", isDefault = false),
            CategoryEntity(name = "Suscripciones", type = "EXPENSE", colorHex = "#00838F", iconName = "Subscriptions", isDefault = false),
            CategoryEntity(name = "Regalos", type = "EXPENSE", colorHex = "#C2185B", iconName = "CardGiftcard", isDefault = false),
            CategoryEntity(name = "Ocio", type = "EXPENSE", colorHex = "#D84315", iconName = "SportsEsports", isDefault = false),
        )
        dao.insertCategories(defaultCategories)

        // 2. Budget Initial (for month=5, year=2026)
        val initialBudgets = listOf(
            BudgetEntity(categoryName = "Vivienda", month = 5, year = 2026, plannedAmount = 300000.0),
            BudgetEntity(categoryName = "Servicios", month = 5, year = 2026, plannedAmount = 120000.0),
            BudgetEntity(categoryName = "Alimentación", month = 5, year = 2026, plannedAmount = 280000.0),
            BudgetEntity(categoryName = "Transporte", month = 5, year = 2026, plannedAmount = 80000.0),
            BudgetEntity(categoryName = "Entretenimiento", month = 5, year = 2026, plannedAmount = 50000.0),
            BudgetEntity(categoryName = "Salud", month = 5, year = 2026, plannedAmount = 60000.0),
            BudgetEntity(categoryName = "Ahorro", month = 5, year = 2026, plannedAmount = 150000.0),
            BudgetEntity(categoryName = "Otros", month = 5, year = 2026, plannedAmount = 50000.0),
        )
        dao.insertBudgets(initialBudgets)

        // 3. Stock Seed Data
        val initialStocks = listOf(
            InvestmentEntity(ticker = "PLTR", companyName = "Palantir Technologies", quantity = 5.085598192, purchasePrice = 146.40, currentPrice = 156.54),
            InvestmentEntity(ticker = "MSFT", companyName = "Microsoft", quantity = 2.693500945, purchasePrice = 466.83, currentPrice = 450.24),
            InvestmentEntity(ticker = "SPGI", companyName = "S&P Global", quantity = 0.607185186, purchasePrice = 411.74, currentPrice = 424.17),
            InvestmentEntity(ticker = "TSLA", companyName = "Tesla", quantity = 5.654281582, purchasePrice = 70.74, currentPrice = 70.39),
            InvestmentEntity(ticker = "NVDA", companyName = "NVIDIA", quantity = 1.37081707, purchasePrice = 216.24, currentPrice = 211.14),
            InvestmentEntity(ticker = "INTC", companyName = "Intel", quantity = 1.002848909, purchasePrice = 122.15, currentPrice = 114.68),
            InvestmentEntity(ticker = "MA", companyName = "Mastercard", quantity = 0.409148217, purchasePrice = 493.34, currentPrice = 494.04),
            InvestmentEntity(ticker = "AMZN", companyName = "Amazon.com", quantity = 1.127453887, purchasePrice = 269.78, currentPrice = 270.64)
        )
        dao.insertInvestments(initialStocks)

        // 4. Incomes Seed Data (Total: CLP 1.090.094)
        val seedIncomes = listOf(
            TransactionEntity(type = "INCOME", date = "2026-05-01", categoryName = "Sueldo", description = "Salario mensual", amount = 421000.0),
            TransactionEntity(type = "INCOME", date = "2026-05-02", categoryName = "Propinas", description = "Semana 1 mayo", amount = 327094.0),
            TransactionEntity(type = "INCOME", date = "2026-05-03", categoryName = "Propinas", description = "Semana 2 mayo", amount = 270000.0),
            TransactionEntity(type = "INCOME", date = "2026-05-04", categoryName = "Otros ingresos", description = "INACAP", amount = 40000.0),
            TransactionEntity(type = "INCOME", date = "2026-05-05", categoryName = "Otros ingresos", description = "Beneficios", amount = 32000.0)
        )
        for (inc in seedIncomes) {
            dao.insertTransaction(inc)
        }

        // 5. Expense Seed Data (Total: CLP 748.825)
        val seedExpenses = listOf(
            TransactionEntity(type = "EXPENSE", date = "2026-05-01", categoryName = "Vivienda", description = "Arriendo", amount = 458000.0),
            TransactionEntity(type = "EXPENSE", date = "2026-05-02", categoryName = "Micro", description = "Supermercado", amount = 20000.0),
            TransactionEntity(type = "EXPENSE", date = "2026-05-03", categoryName = "Uber", description = "Transporte público / combustible", amount = 127405.0),
            TransactionEntity(type = "EXPENSE", date = "2026-05-04", categoryName = "Suscripciones", description = "Streaming y apps", amount = 2700.0),
            TransactionEntity(type = "EXPENSE", date = "2026-05-05", categoryName = "Regalos", description = "Andrea Regalo", amount = 105000.0),
            TransactionEntity(type = "EXPENSE", date = "2026-05-06", categoryName = "Ocio", description = "Choritos", amount = 5560.0),
            TransactionEntity(type = "EXPENSE", date = "2026-05-07", categoryName = "Ocio", description = "Despedida Edu", amount = 5160.0),
            TransactionEntity(type = "EXPENSE", date = "2026-05-29", categoryName = "Alimentación", description = "Harina, jamón y queso", amount = 25000.0)
        )
        for (exp in seedExpenses) {
            dao.insertTransaction(exp)
        }
    }

    suspend fun clearAllData() {
        dao.clearAllTransactions()
        dao.clearAllCategories()
        dao.clearAllBudgets()
        dao.clearAllInvestments()
    }

    // --- Direct CRUD delegation ---
    suspend fun insertTransaction(transaction: TransactionEntity) = dao.insertTransaction(transaction)
    suspend fun updateTransaction(transaction: TransactionEntity) = dao.updateTransaction(transaction)
    suspend fun deleteTransaction(transaction: TransactionEntity) = dao.deleteTransaction(transaction)
    suspend fun deleteTransactionById(id: Int) = dao.deleteTransactionById(id)

    suspend fun insertCategory(category: CategoryEntity) = dao.insertCategory(category)
    suspend fun updateCategory(category: CategoryEntity) = dao.updateCategory(category)
    suspend fun deleteCategory(category: CategoryEntity) = dao.deleteCategory(category)
    suspend fun deleteCategoryById(id: Int) = dao.deleteCategoryById(id)
    suspend fun getTransactionCountForCategory(categoryName: String) = dao.getTransactionCountForCategory(categoryName)

    suspend fun insertBudget(budget: BudgetEntity) = dao.insertBudget(budget)
    suspend fun updateBudget(budget: BudgetEntity) = dao.updateBudget(budget)
    suspend fun deleteBudgetById(id: Int) = dao.deleteBudgetById(id)

    suspend fun insertInvestment(investment: InvestmentEntity) = dao.insertInvestment(investment)
    suspend fun updateInvestment(investment: InvestmentEntity) = dao.updateInvestment(investment)
    suspend fun deleteInvestmentById(id: Int) = dao.deleteInvestmentById(id)
}
