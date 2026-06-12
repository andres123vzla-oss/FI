package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.entity.BudgetEntity
import com.example.data.entity.CategoryEntity
import com.example.data.entity.InvestmentEntity
import com.example.data.entity.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.data.market.MarketDataProvider
import com.example.data.market.PriceResult
import com.example.data.market.SymbolMatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import com.example.domain.BudgetAnalyzer
import com.example.domain.BudgetLine
import com.example.domain.BudgetRecommendation
import com.example.domain.BudgetSummary
import com.example.domain.CashFlowAnalyzer
import com.example.domain.CategoryAlert
import com.example.domain.CategoryAnalysis
import com.example.domain.CategorySpendingAnalyzer
import com.example.domain.FinancialHealth
import com.example.domain.FinancialHealthAnalyzer
import com.example.domain.InvestmentCalculator
import com.example.domain.PortfolioMetrics
import com.example.util.FinanceCalculator
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = FinanceRepository(database.financeDao())
    private val marketRepo = MarketDataProvider.repository

    /** Estado de la actualización de precios de mercado (para la pantalla de Portafolio). */
    val priceUpdateState = MutableStateFlow<PriceUpdateState>(PriceUpdateState.Idle)
    val marketCanRefresh: Boolean get() = marketRepo.canRefresh
    val marketModeLabel: String get() = marketRepo.modeLabel

    /** ¿Hay una fuente remota que permita actualización en vivo? */
    val isAutoRefreshAvailable: Boolean get() = marketRepo.canRefresh

    /** Interruptor de la actualización automática en vivo (en foreground). Activa por defecto. */
    val autoRefreshEnabled = MutableStateFlow(true)

    fun setAutoRefresh(enabled: Boolean) { autoRefreshEnabled.value = enabled }

    /** Señal discreta de fallo del refresco automático (silencioso): null si todo va bien. */
    val liveError = MutableStateFlow<String?>(null)

    // --- Búsqueda de activos (Finnhub /search) para el diálogo "Agregar" ---
    val symbolQuery = MutableStateFlow("")
    val symbolSearchState = MutableStateFlow<SymbolSearchState>(SymbolSearchState.Idle)

    fun onSymbolQueryChange(text: String) { symbolQuery.value = text }
    fun clearSymbolSearch() {
        symbolQuery.value = ""
        symbolSearchState.value = SymbolSearchState.Idle
    }

    /** Indica al diálogo si la búsqueda remota está disponible (hay API key configurada). */
    val isSymbolSearchAvailable: Boolean get() = marketRepo.canRefresh

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val symbolSearchPipeline = symbolQuery
        .debounce(350) // ≥300 ms recomendado por Finnhub (respeta el rate limit)
        .map { it.trim() }
        .distinctUntilChanged()
        .onEach { q ->
            symbolSearchState.value = when {
                !marketRepo.canRefresh -> SymbolSearchState.NeedsApiKey
                q.length < 2 -> SymbolSearchState.Idle
                else -> SymbolSearchState.Loading
            }
        }
        .filter { marketRepo.canRefresh && it.length >= 2 }
        .flatMapLatest { q -> // cancela búsquedas obsoletas al teclear
            flow {
                val results = marketRepo.searchSymbols(q)
                emit(
                    if (results.isEmpty()) SymbolSearchState.Empty
                    else SymbolSearchState.Results(results)
                )
            }
        }
        .onEach { symbolSearchState.value = it }
        .launchIn(viewModelScope)

    /**
     * Obtiene el precio actual de un símbolo para prellenar el diálogo. null si no hay fuente
     * remota o no hay dato (c==0 / NaN ya filtrados por fetchPrice). No persiste nada.
     */
    suspend fun prefillPrice(symbol: String): Double? =
        when (val r = marketRepo.fetchPrice(symbol)) {
            is PriceResult.Success -> r.price
            is PriceResult.Failure -> null
        }

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

    // Sin auto-seeding: la app arranca completamente vacía. Los datos solo aparecen cuando el
    // usuario los crea, o cuando invoca manualmente "Restaurar datos demo" (con reautenticación)
    // desde Ajustes / Dashboard. Una base vacía NUNCA dispara siembra automática.

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

        // Variaciones reales respecto al mes anterior (sin datos fabricados).
        val curIdx = monthlySummaryList.indexOfFirst { it.month == month && it.year == year }
        val prev = if (curIdx > 0) monthlySummaryList[curIdx - 1] else null
        val momBalanceChange = CashFlowAnalyzer.variation(balance, prev?.balance)
        val momIncomeChange = CashFlowAnalyzer.variation(totalIncome, prev?.income)
        val momExpenseChange = CashFlowAnalyzer.variation(totalExpense, prev?.expense)

        // CALC-04: días transcurridos REALES del mes para promedio diario y proyección.
        // - Mes en curso: usar el día de HOY (no el día del último movimiento, que inflaba el
        //   promedio ~2.5x si el último gasto fue el día 10 pero hoy es 25).
        // - Mes histórico cerrado: usar el mes completo y NO proyectar (la "proyección a fin de
        //   mes" no tiene sentido en un mes ya terminado).
        val daysInMonth = daysInMonth(month, year)
        val esMesEnCurso = month == currentMonth() && year == currentYear()
        val daysElapsed = if (esMesEnCurso) {
            currentDayOfMonth().coerceIn(1, daysInMonth)
        } else {
            daysInMonth
        }

        val savingsRate = CashFlowAnalyzer.savingsRate(totalIncome, totalExpense)
        val dailyAvgSpending = CashFlowAnalyzer.dailyAverageSpending(totalExpense, daysElapsed)
        val projectedMonthEndSpending = if (esMesEnCurso) {
            CashFlowAnalyzer.projectedMonthEndSpending(totalExpense, daysElapsed, daysInMonth)
        } else {
            // Mes cerrado: la proyección es el gasto total real (sin extrapolar a futuro).
            totalExpense
        }
        val topCategory = CategorySpendingAnalyzer.topCategory(spentByCategory)
        val health = FinancialHealthAnalyzer.evaluate(savingsRate, balance)

        DashboardDetails(
            incomeTotal = totalIncome,
            expenseTotal = totalExpense,
            balanceTotal = balance,
            portfolioTotal = portfolioCurrentValue,
            monthlySummaries = monthlySummaryList,
            categorySpendingList = categorySpentList,
            momBalanceChange = momBalanceChange,
            momIncomeChange = momIncomeChange,
            momExpenseChange = momExpenseChange,
            savingsRate = savingsRate,
            dailyAvgSpending = dailyAvgSpending,
            projectedMonthEndSpending = projectedMonthEndSpending,
            topCategoryName = topCategory?.categoryName,
            topCategoryAmount = topCategory?.amount ?: 0.0,
            health = health,
        )
    }
        // A6: el combine recomputa O(n) con split de fechas; fuera del hilo Main para evitar jank.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardDetails())

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
    }
        // A6: filtrado/parseo de fechas O(n) fuera del hilo Main.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

        // Solo categorías de gasto REALES del usuario + las que ya tienen un presupuesto guardado.
        // Sin categorías de referencia hardcodeadas: con la BD vacía, la lista queda vacía y la
        // pantalla de Presupuesto muestra su empty state.
        val expenseCategories = categories.filter { it.type == "EXPENSE" }.map { it.name }.toSet()
        val allExpenseCategoryNames = (expenseCategories + budgets.map { it.categoryName }).toList().distinct()

        allExpenseCategoryNames.map { category ->
            // 1. Get budgeted amount for selected month and year (0 si el usuario no lo ha fijado;
            // nunca un monto inventado).
            val budgetObj = budgets.firstOrNull { it.categoryName == category && it.month == sMonth && it.year == sYear }
            val budgetedAmount = budgetObj?.plannedAmount ?: 0.0

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
                // CALC-03: derivar numerador y denominador del MISMO conjunto filtrado para que no
                // diverjan. El promedio se divide por los MESES CON DATOS reales (no siempre 3): una
                // categoría con gasto en solo 1 de los últimos 3 meses se promedia sobre 1, no sobre 3,
                // evitando subestimar el gasto real ~3x. coerceAtLeast(1) impide división por cero.
                val txsEnRango = transactions.filter { tx ->
                    if (tx.type == "EXPENSE" && tx.categoryName == category) {
                        val parts = tx.date.split("-")
                        if (parts.size >= 2) {
                            val txYear = parts[0].toIntOrNull() ?: 0
                            val txMonth = parts[1].toIntOrNull() ?: 0
                            threeMonthsRange.contains(txMonth to txYear)
                        } else false
                    } else false
                }
                val totalSpent3M = txsEnRango.sumOf { it.amount }
                val mesesConDatos = txsEnRango.mapNotNull { tx ->
                    val p = tx.date.split("-")
                    if (p.size >= 2) {
                        val m = p[1].toIntOrNull()
                        val y = p[0].toIntOrNull()
                        if (m != null && y != null) m to y else null
                    } else null
                }.distinct().size.coerceAtLeast(1)
                totalSpent3M / mesesConDatos.toDouble()
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
    }
        // A6: agregación por categoría con split de fechas O(categorías*n) fuera del hilo Main.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    /** Métricas completas del portafolio (pesos, mejor/peor, rendimiento) para la pantalla. */
    val portfolioMetricsFlow: StateFlow<PortfolioMetrics> = allInvestments
        .map { InvestmentCalculator.analyze(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PortfolioMetrics())

    /** Momento de la última actualización de precios (máximo `updatedAt`); null si no hay activos. */
    val lastPriceUpdate: StateFlow<Long?> = allInvestments
        .map { stocks -> stocks.maxOfOrNull { it.updatedAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Análisis de presupuesto derivado del reporte por categoría ---
    private fun budgetLines(items: List<BudgetReportItem>): List<BudgetLine> =
        items.map { BudgetLine(it.categoryName, it.budgetedAmount, it.spentAmount) }

    /** Resumen agregado del presupuesto del mes seleccionado. */
    val budgetSummaryFlow: StateFlow<BudgetSummary> = budgetReportFlow
        .map { BudgetAnalyzer.summarize(budgetLines(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetSummary())

    /** Recomendaciones simples de presupuesto (montos crudos; la UI los formatea). */
    val budgetRecommendationsFlow: StateFlow<List<BudgetRecommendation>> = budgetReportFlow
        .map { BudgetAnalyzer.recommendations(budgetLines(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Análisis de gasto por categoría del mes seleccionado ---
    val categoryAnalysisFlow: StateFlow<List<CategoryAnalysis>> = combine(
        allTransactions,
        allBudgets,
        selectedMonth,
        selectedYear
    ) { txs, budgets, month, year ->
        val spentByCategory = txs
            .filter { tx ->
                val parts = tx.date.split("-")
                val txYear = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val txMonth = parts.getOrNull(1)?.toIntOrNull() ?: 0
                tx.type == "EXPENSE" && txYear == year && txMonth == month
            }
            .groupBy { it.categoryName }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
        val budgetByCategory = budgets
            .filter { it.month == month && it.year == year }
            .associate { it.categoryName to it.plannedAmount }
        CategorySpendingAnalyzer.analyze(spentByCategory, budgetByCategory)
    }
        // A6: filtrado/agrupación O(n) con split de fechas fuera del hilo Main.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Alertas de categorías sin presupuesto con gasto relevante. */
    val unbudgetedAlertsFlow: StateFlow<List<CategoryAlert>> = combine(
        allTransactions,
        allBudgets,
        selectedMonth,
        selectedYear
    ) { txs, budgets, month, year ->
        val spentByCategory = txs
            .filter { tx ->
                val parts = tx.date.split("-")
                val txYear = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val txMonth = parts.getOrNull(1)?.toIntOrNull() ?: 0
                tx.type == "EXPENSE" && txYear == year && txMonth == month
            }
            .groupBy { it.categoryName }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
        val budgetByCategory = budgets
            .filter { it.month == month && it.year == year }
            .associate { it.categoryName to it.plannedAmount }
        CategorySpendingAnalyzer.unbudgetedAlerts(spentByCategory, budgetByCategory)
    }
        // A6: filtrado/agrupación O(n) con split de fechas fuera del hilo Main.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // --- Actions/Operations ---

    /**
     * Actualiza los precios actuales consultando la fuente de mercado. Si la fuente no admite
     * actualización (modo manual) o falla, se conservan los últimos precios conocidos y se informa
     * al usuario. Nunca lanza excepción hacia la UI.
     */
    /** Actualización MANUAL (botón): muestra spinner y banner de resultado. */
    fun refreshPrices() = refreshPricesInternal(auto = false)

    /**
     * Actualización AUTOMÁTICA en vivo (poller en foreground): silenciosa. No muestra spinner ni
     * banner de éxito; solo refresca los precios y `lastPriceUpdate`. Si no hay fuente remota o no
     * hay activos, no hace nada (sin ruido ni tráfico).
     */
    fun autoRefreshPrices() = refreshPricesInternal(auto = true)

    private fun refreshPricesInternal(auto: Boolean) {
        if (!marketRepo.canRefresh) {
            if (!auto) {
                priceUpdateState.value = PriceUpdateState.Error(
                    "Actualización automática no disponible. Ingresa los precios manualmente."
                )
            }
            return
        }
        viewModelScope.launch {
            if (!auto) priceUpdateState.value = PriceUpdateState.Loading
            val stocks = repository.allInvestments.first()
            if (stocks.isEmpty()) {
                if (!auto) priceUpdateState.value = PriceUpdateState.Error("No hay activos en el portafolio.")
                return@launch
            }
            var updated = 0
            var failed = 0
            // A7: conservar el último mensaje de fallo (ticker inválido / rate limit / offline) que
            // hoy se descartaba, para mostrar un aviso accionable en vez de uno genérico. El mensaje
            // de la fuente no contiene la API key ni datos sensibles (el ticker no es sensible).
            var lastFailMsg: String? = null
            for (stock in stocks) {
                when (val result = marketRepo.fetchPrice(stock.ticker)) {
                    is PriceResult.Success -> {
                        repository.updateInvestment(
                            stock.copy(
                                currentPrice = result.price,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        updated++
                    }
                    is PriceResult.Failure -> {
                        failed++
                        lastFailMsg = result.message
                    }
                }
            }
            // Señal discreta para el camino automático (que no usa banners): si nada se actualizó
            // se avisa de forma sutil; cualquier éxito limpia el aviso.
            if (updated > 0) liveError.value = null
            else if (auto) liveError.value = lastFailMsg ?: "Sin conexión a la API · reintentando"

            if (!auto) {
                priceUpdateState.value = if (updated > 0) {
                    val tail = if (failed > 0) " ($failed sin cambios)" else ""
                    PriceUpdateState.Success(updated, "Se actualizaron $updated precio(s)$tail.")
                } else {
                    PriceUpdateState.Error(
                        lastFailMsg
                            ?: "No se pudieron actualizar los precios. Se conservan los últimos conocidos."
                    )
                }
            }
        }
    }

    /** Restablece el estado de actualización (p. ej. tras mostrar un mensaje). */
    fun clearPriceUpdateState() {
        priceUpdateState.value = PriceUpdateState.Idle
    }

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
            // A3 (contrato C1): no insertar un Entity nuevo (id=0); delegar en el repositorio,
            // que FUSIONA la posición si el ticker ya existe (cantidad acumulada + precio de compra
            // promedio ponderado) en vez de duplicar o reemplazar/borrar la posición previa.
            repository.addOrMergeInvestment(
                ticker = ticker,
                companyName = companyName,
                quantity = quantity,
                purchasePrice = purchasePrice,
                currentPrice = currentPrice
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

    /** Mes actual (1..12) del calendario del dispositivo. Aislado para legibilidad de CALC-04. */
    private fun currentMonth(): Int = Calendar.getInstance().get(Calendar.MONTH) + 1

    /** Año actual del calendario del dispositivo. */
    private fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

    /** Día del mes actual (1..31) del calendario del dispositivo. */
    private fun currentDayOfMonth(): Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

    /** Días reales del mes (incluye años bisiestos), sin depender del reloj del sistema. */
    private fun daysInMonth(month: Int, year: Int): Int {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, (month - 1).coerceIn(0, 11))
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    companion object {
        /**
         * A5: Factory explícita para construir el [FinanceViewModel]. Centraliza la creación del VM
         * en un único punto (en lugar de depender del default de `viewModel()`), lo que mejora la
         * testabilidad y deja la puerta abierta a inyectar dependencias en el futuro. Conserva el
         * comportamiento de runtime actual: sigue siendo un AndroidViewModel que obtiene su
         * repositorio de [AppDatabase.getDatabase] y el singleton de mercado, sin tocar el flujo de
         * claves ni la seguridad.
         */
        fun provideFactory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(FinanceViewModel::class.java)) {
                        "Factory desconocida para ${modelClass.name}"
                    }
                    return FinanceViewModel(application) as T
                }
            }
    }
}

/** Estado de la actualización de precios de mercado mostrado en la pantalla de Portafolio. */
sealed class PriceUpdateState {
    object Idle : PriceUpdateState()
    object Loading : PriceUpdateState()
    data class Success(val updatedCount: Int, val message: String) : PriceUpdateState()
    data class Error(val message: String) : PriceUpdateState()
}

/** Estado de la búsqueda de símbolos en el diálogo "Agregar activo". */
sealed class SymbolSearchState {
    object Idle : SymbolSearchState()
    object NeedsApiKey : SymbolSearchState() // modo manual / sin key → mensaje en el diálogo
    object Loading : SymbolSearchState()
    object Empty : SymbolSearchState() // sin coincidencias o error de red (degrada suave)
    data class Results(val matches: List<SymbolMatch>) : SymbolSearchState()
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
    val momBalanceChange: Double? = null,
    /** Variación proporcional de ingresos respecto al mes anterior; null si no hay base. */
    val momIncomeChange: Double? = null,
    /** Variación proporcional de gastos respecto al mes anterior; null si no hay base. */
    val momExpenseChange: Double? = null,
    /** Tasa de ahorro del mes: (ingresos − gastos) / ingresos. */
    val savingsRate: Double = 0.0,
    /** Gasto diario promedio del periodo con actividad. */
    val dailyAvgSpending: Double = 0.0,
    /** Proyección de gasto a fin de mes según el ritmo actual. */
    val projectedMonthEndSpending: Double = 0.0,
    /** Categoría con mayor gasto del mes; null si no hay gasto. */
    val topCategoryName: String? = null,
    val topCategoryAmount: Double = 0.0,
    /** Estado financiero del mes (Excelente/Bueno/Ajustado/Crítico). */
    val health: FinancialHealth = FinancialHealth.AJUSTADO,
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
