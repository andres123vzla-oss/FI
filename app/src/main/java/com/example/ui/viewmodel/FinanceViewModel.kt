package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.entity.BudgetEntity
import com.example.data.entity.CategoryEntity
import com.example.data.entity.InvestmentEntity
import com.example.data.entity.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.util.FinanceCalculator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = FinanceRepository(database.financeDao())

    // App state: Selected Month and Year for Dashboard & Budget
    val selectedMonth = MutableStateFlow(5) // Default to May 2026 as per specification
    val selectedYear = MutableStateFlow(2026)

    // Budget Screen: "Modo promedio trimestral" toggle
    val quarterlyAverageMode = MutableStateFlow(false)

    // Movimientos list filters
    val filterType = MutableStateFlow("ALL") // "ALL", "INCOME", "EXPENSE"
    val filterCategory = MutableStateFlow("ALL")
    val filterMonth = MutableStateFlow(5) // Default to May to match initial seeding filters
    val filterYear = MutableStateFlow(2026)
    val searchQuery = MutableStateFlow("")

    // Raw Room flows
    val allTransactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCategories: StateFlow<List<CategoryEntity>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBudgets: StateFlow<List<BudgetEntity>> = repository.allBudgets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInvestments: StateFlow<List<InvestmentEntity>> = repository.allInvestments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.ensureSeedData()
        }
    }

    // --- Computed flows for custom months ---
    val dashboardSummary: StateFlow<DashboardDetails> = combine(
        allTransactions,
        allInvestments,
        selectedMonth,
        selectedYear
    ) { txList, stockList, month, year ->
        // Format month-year strings in transaction: "YYYY-MM-DD" -> parse year and month
        val filteredTx = txList.filter { tx ->
            val dateParts = tx.date.split("-")
            if (dateParts.size >= 2) {
                val txYear = dateParts[0].toIntOrNull() ?: 0
                val txMonth = dateParts[1].toIntOrNull() ?: 0
                txYear == year && txMonth == month
            } else false
        }

        val totalIncome = filteredTx.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalExpense = filteredTx.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val balance = FinanceCalculator.balance(totalIncome, totalExpense)

        val portfolioCurrentValue = stockList.sumOf { it.quantity * it.currentPrice }

        // Monthly overview list for Chart & comparison: let's build 6 months up to selectedMonth
        val monthlySummaryList = mutableListOf<MonthlySummary>()
        // Calculate figures for past 4 months + selected month & next month to show a trend list
        for (mOffset in -3..1) {
            var targetM = month + mOffset
            var targetY = year
            if (targetM <= 0) {
                targetM += 12
                targetY -= 1
            } else if (targetM > 12) {
                targetM -= 12
                targetY += 1
            }

            val curFiltered = txList.filter { tx ->
                val dateParts = tx.date.split("-")
                if (dateParts.size >= 2) {
                    val txYear = dateParts[0].toIntOrNull() ?: 0
                    val txMonth = dateParts[1].toIntOrNull() ?: 0
                    txYear == targetY && txMonth == targetM
                } else false
            }
            val incSum = curFiltered.filter { it.type == "INCOME" }.sumOf { it.amount }
            val expSum = curFiltered.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            monthlySummaryList.add(
                MonthlySummary(
                    month = targetM,
                    year = targetY,
                    income = incSum,
                    expense = expSum,
                    balance = incSum - expSum
                )
            )
        }

        // Sector distribution by category spent
        val spentByCategory = filteredTx.filter { it.type == "EXPENSE" }
            .groupBy { it.categoryName }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val categorySpentList = spentByCategory.map { (catName, sumAmount) ->
            val percentage = FinanceCalculator.ratio(sumAmount, totalExpense)
            CategoryExpenseSummary(catName, sumAmount, percentage)
        }.sortedByDescending { it.amount }

        // Variación real del balance respecto al mes anterior (sin datos fabricados).
        val curIdx = monthlySummaryList.indexOfFirst { it.month == month && it.year == year }
        val momBalanceChange: Double? = if (curIdx > 0) {
            val prevBalance = monthlySummaryList[curIdx - 1].balance
            val curBalance = monthlySummaryList[curIdx].balance
            if (prevBalance != 0.0) (curBalance - prevBalance) / kotlin.math.abs(prevBalance) else null
        } else null

        DashboardDetails(
            incomeTotal = totalIncome,
            expenseTotal = totalExpense,
            balanceTotal = balance,
            portfolioTotal = portfolioCurrentValue,
            monthlySummaries = monthlySummaryList,
            categorySpendingList = categorySpentList,
            momBalanceChange = momBalanceChange
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardDetails())

    // --- Filtered and searched transactions flow for Movimientos tab ---
    val filteredTransactionsFlow: StateFlow<List<TransactionEntity>> = combine(
        allTransactions,
        filterType,
        filterCategory,
        filterMonth,
        filterYear,
        searchQuery
    ) { flowsArray ->
        val txList = flowsArray[0] as List<TransactionEntity>
        val type = flowsArray[1] as String
        val category = flowsArray[2] as String
        val fm = flowsArray[3] as Int
        val fy = flowsArray[4] as Int
        val search = flowsArray[5] as String

        txList.filter { tx ->
            // Date parsing
            val dateParts = tx.date.split("-")
            val txYear = dateParts.getOrNull(0)?.toIntOrNull() ?: 0
            val txMonth = dateParts.getOrNull(1)?.toIntOrNull() ?: 0

            val matchesType = type == "ALL" || tx.type == type
            val matchesCategory = category == "ALL" || tx.categoryName == category
            val matchesMonth = fm == 0 || txMonth == fm
            val matchesYear = fy == 0 || txYear == fy
            val matchesSearch = search.isEmpty() ||
                    tx.description.contains(search, ignoreCase = true) ||
                    tx.categoryName.contains(search, ignoreCase = true) ||
                    tx.amount.toString().contains(search)

            matchesType && matchesCategory && matchesMonth && matchesYear && matchesSearch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Budget calculations flow ---
    val budgetReportFlow: StateFlow<List<BudgetReportItem>> = combine(
        allBudgets,
        allTransactions,
        allCategories,
        selectedMonth,
        selectedYear,
        quarterlyAverageMode
    ) { flowsArray ->
        val budgets = flowsArray[0] as List<BudgetEntity>
        val transactions = flowsArray[1] as List<TransactionEntity>
        val categories = flowsArray[2] as List<CategoryEntity>
        val sMonth = flowsArray[3] as Int
        val sYear = flowsArray[4] as Int
        val isAvgMode = flowsArray[5] as Boolean

        // Filter out expense categories
        val expenseCategories = categories.filter { it.type == "EXPENSE" }.map { it.name }.toSet()
        val defaultReferenceCategories = listOf("Vivienda", "Servicios", "Alimentación", "Transporte", "Entretenimiento", "Salud", "Ahorro", "Otros")
        val allExpenseCategoryNames = (expenseCategories + defaultReferenceCategories + budgets.map { it.categoryName }).toList().distinct()

        allExpenseCategoryNames.map { category ->
            // 1. Get budgeted amount for selected month and year
            val budgetObj = budgets.firstOrNull { it.categoryName == category && it.month == sMonth && it.year == sYear }
            val budgetedAmount = budgetObj?.plannedAmount ?: when (category) {
                "Vivienda" -> 300000.0
                "Servicios" -> 120000.0
                "Alimentación" -> 280000.0
                "Transporte" -> 80000.0
                "Entretenimiento" -> 50000.0
                "Salud" -> 60000.0
                "Ahorro" -> 150000.0
                "Otros" -> 50000.0
                else -> 0.0 // Default dynamic category gets 0 budgeted if not saved
            }

            // 2. Get spent amount
            val spentAmount = if (isAvgMode) {
                // Average of last 3 months spent in this category
                val threeMonthsRange = List(3) { i ->
                    var m = sMonth - i
                    var y = sYear
                    if (m <= 0) {
                        m += 12
                        y -= 1
                    }
                    m to y
                }
                val totalSpent3M = transactions.filter { tx ->
                    if (tx.type == "EXPENSE" && tx.categoryName == category) {
                        val parts = tx.date.split("-")
                        if (parts.size >= 2) {
                            val txYear = parts[0].toIntOrNull() ?: 0
                            val txMonth = parts[1].toIntOrNull() ?: 0
                            threeMonthsRange.contains(txMonth to txYear)
                        } else false
                    } else false
                }.sumOf { it.amount }
                totalSpent3M / 3.0
            } else {
                // Real spent amount for the selected month
                transactions.filter { tx ->
                    if (tx.type == "EXPENSE" && tx.categoryName == category) {
                        val parts = tx.date.split("-")
                        if (parts.size >= 2) {
                            val txY = parts[0].toIntOrNull() ?: 0
                            val txM = parts[1].toIntOrNull() ?: 0
                            txY == sYear && txM == sMonth
                        } else false
                    } else false
                }.sumOf { it.amount }
            }

            val difference = budgetedAmount - spentAmount
            val usagePercent = FinanceCalculator.ratio(spentAmount, budgetedAmount)

            BudgetReportItem(
                categoryName = category,
                budgetedAmount = budgetedAmount,
                spentAmount = spentAmount,
                difference = difference,
                usagePercentage = usagePercent,
                budgetId = budgetObj?.id
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Investment stocks summary flow ---
    val investmentSummaryFlow: StateFlow<InvestmentSummary> = allInvestments.map { stocks ->
        val totalInvested = stocks.sumOf { it.quantity * it.purchasePrice }
        val totalCurrent = stocks.sumOf { it.quantity * it.currentPrice }
        val gainLoss = FinanceCalculator.gainLoss(totalCurrent, totalInvested)
        val yieldPercent = FinanceCalculator.yieldPercent(gainLoss, totalInvested)

        InvestmentSummary(
            totalInvested = totalInvested,
            totalCurrent = totalCurrent,
            totalGainLoss = gainLoss,
            totalGainLossPercent = yieldPercent,
            stocks = stocks
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InvestmentSummary())


    // --- Actions/Operations ---

    fun updateSelectedMonthYear(month: Int, year: Int) {
        selectedMonth.value = month
        selectedYear.value = year
    }

    fun addTransaction(type: String, date: String, categoryName: String, description: String, amount: Double) {
        viewModelScope.launch {
            repository.insertTransaction(
                TransactionEntity(
                    type = type,
                    date = date,
                    categoryName = categoryName,
                    description = description,
                    amount = amount
                )
            )
        }
    }

    fun updateTransaction(id: Int, type: String, date: String, categoryName: String, description: String, amount: Double) {
        viewModelScope.launch {
            repository.updateTransaction(
                TransactionEntity(
                    id = id,
                    type = type,
                    date = date,
                    categoryName = categoryName,
                    description = description,
                    amount = amount
                )
            )
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun addCategory(name: String, type: String, colorHex: String, iconName: String) {
        viewModelScope.launch {
            repository.insertCategory(
                CategoryEntity(
                    name = name,
                    type = type,
                    colorHex = colorHex,
                    iconName = iconName
                )
            )
        }
    }

    fun updateCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    fun deleteCategory(category: CategoryEntity, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val txCount = repository.getTransactionCountForCategory(category.name)
            if (txCount > 0) {
                onResult(false, "No se puede eliminar la categoría porque tiene $txCount movimientos asociados.")
            } else {
                repository.deleteCategory(category)
                onResult(true, "Categoría eliminada con éxito.")
            }
        }
    }

    fun saveBudget(categoryName: String, month: Int, year: Int, amount: Double, existingId: Int?) {
        viewModelScope.launch {
            repository.insertBudget(
                BudgetEntity(
                    id = existingId ?: 0,
                    categoryName = categoryName,
                    month = month,
                    year = year,
                    plannedAmount = amount
                )
            )
        }
    }

    fun addStock(ticker: String, companyName: String, quantity: Double, purchasePrice: Double, currentPrice: Double) {
        viewModelScope.launch {
            repository.insertInvestment(
                InvestmentEntity(
                    ticker = ticker,
                    companyName = companyName,
                    quantity = quantity,
                    purchasePrice = purchasePrice,
                    currentPrice = currentPrice
                )
            )
        }
    }

    fun updateStock(stock: InvestmentEntity) {
        viewModelScope.launch {
            repository.updateInvestment(stock)
        }
    }

    fun deleteStock(id: Int) {
        viewModelScope.launch {
            repository.deleteInvestmentById(id)
        }
    }

    fun resetToSeedData() {
        viewModelScope.launch {
            repository.restoreSeedData()
            // Reset dates & filters
            selectedMonth.value = 5
            selectedYear.value = 2026
            filterMonth.value = 5
            filterYear.value = 2026
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
        }
    }
}

// --- Companion / Auxiliary Data Structures ---

data class DashboardDetails(
    val incomeTotal: Double = 0.0,
    val expenseTotal: Double = 0.0,
    val balanceTotal: Double = 0.0,
    val portfolioTotal: Double = 0.0,
    val monthlySummaries: List<MonthlySummary> = emptyList(),
    val categorySpendingList: List<CategoryExpenseSummary> = emptyList(),
    /** Variación proporcional del balance respecto al mes anterior; null si no hay base. */
    val momBalanceChange: Double? = null
)

data class MonthlySummary(
    val month: Int,
    val year: Int,
    val income: Double,
    val expense: Double,
    val balance: Double
)

data class CategoryExpenseSummary(
    val categoryName: String,
    val amount: Double,
    val percentage: Double
)

data class BudgetReportItem(
    val categoryName: String,
    val budgetedAmount: Double,
    val spentAmount: Double,
    val difference: Double,
    val usagePercentage: Double,
    val budgetId: Int? // If already exists in Room, holds the id
)

data class InvestmentSummary(
    val totalInvested: Double = 0.0,
    val totalCurrent: Double = 0.0,
    val totalGainLoss: Double = 0.0,
    val totalGainLossPercent: Double = 0.0,
    val stocks: List<InvestmentEntity> = emptyList()
)
