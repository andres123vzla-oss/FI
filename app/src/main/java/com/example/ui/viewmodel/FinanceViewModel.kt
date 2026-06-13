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
import com.example.domain.QuarterlyAverageCalculator
import com.example.util.FinanceCalculator
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * FIN2-04: fecha "de hoy" como dato inyectable (sin java.time: minSdk 24 sin desugaring).
 * Permite testear daysElapsed/proyección de forma determinista y refrescar el cálculo al
 * cruzar la medianoche vía [FinanceViewModel.refreshDateTick].
 */
data class Today(val year: Int, val month: Int, val day: Int)

class FinanceViewModel(
    application: Application,
    /** FIN2-04: proveedor de fecha inyectable (default: calendario del dispositivo). */
    private val todayProvider: () -> Today = { systemToday() },
) : AndroidViewModel(application) {

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

    /**
     * FIN2-04: tick de fecha observable. Quinto flow del dashboard para que el cálculo de
     * daysElapsed/proyección se invalide al cambiar el día (la UI lo refresca en ON_RESUME).
     */
    private val dateTick = MutableStateFlow(todayProvider())

    /** Refresca la fecha "de hoy" (llamar en ON_RESUME). Barato e idempotente. */
    fun refreshDateTick() {
        val t = todayProvider()
        if (t != dateTick.value) dateTick.value = t
    }

    // App state: Selected Month and Year for Dashboard & Budget
    // FIN2-01: por defecto el MES ACTUAL (antes 5/2026 hardcodeado del seed: la app abría
    // siempre sobre un mes cerrado y la lógica de mes-en-curso casi nunca se ejecutaba).
    val selectedMonth = MutableStateFlow(dateTick.value.month)
    val selectedYear = MutableStateFlow(dateTick.value.year)

    // Budget Screen: "Modo promedio trimestral" toggle
    val quarterlyAverageMode = MutableStateFlow(false)

    // Movimientos list filters
    val filterType = MutableStateFlow("ALL") // "ALL", "INCOME", "EXPENSE"
    val filterCategory = MutableStateFlow("ALL")
    val filterMonth = MutableStateFlow(dateTick.value.month) // FIN2-01: mes actual por defecto
    val filterYear = MutableStateFlow(dateTick.value.year)
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
    // FIN2-04: dateTick como quinto flow para que daysElapsed/proyección se invaliden al
    // cambiar el día con la app abierta (antes el reloj se leía dentro del combine y quedaba
    // congelado hasta la siguiente emisión de Room).
    val dashboardSummary: StateFlow<DashboardDetails> = combine(
        allTransactions,
        allInvestments,
        selectedMonth,
        selectedYear,
        dateTick
    ) { txList, stockList, month, year, today ->
        // Format month-year strings in transaction: "YYYY-MM-DD" -> parse year and month
        val filteredTx = txList.filter { tx ->
            val dateParts = tx.date.split("-")
            if (dateParts.size >= 2) {
                val txYear = dateParts[0].toIntOrNull() ?: 0
                val txMonth = dateParts[1].toIntOrNull() ?: 0
                txYear == year && txMonth == month
            } else false
        }

        // FIN2-02: agregación con suma protegida (ignora NaN/Infinity). Un solo movimiento
        // corrupto convertía el total del mes en Infinity, que FormatUtils mostraba como "CLP 0".
        val totalIncome = FinanceCalculator.sum(filteredTx.filter { it.type == "INCOME" }.map { it.amount })
        val totalExpense = FinanceCalculator.sum(filteredTx.filter { it.type == "EXPENSE" }.map { it.amount })
        val balance = FinanceCalculator.balance(totalIncome, totalExpense)

        val portfolioCurrentValue = FinanceCalculator.sum(stockList.map { it.quantity * it.currentPrice })

        // FIN2-08: ventana de comparativa = 4 meses históricos + el mes seleccionado (5 barras).
        // Antes era -3..1: incluía el MES SIGUIENTE (casi siempre en 0), dibujando una caída
        // ficticia al final del gráfico de barras del dashboard.
        val monthlySummaryList = mutableListOf<MonthlySummary>()
        for (mOffset in -4..0) {
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
            val incSum = FinanceCalculator.sum(curFiltered.filter { it.type == "INCOME" }.map { it.amount })
            val expSum = FinanceCalculator.sum(curFiltered.filter { it.type == "EXPENSE" }.map { it.amount })
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
            .mapValues { entry -> FinanceCalculator.sum(entry.value.map { it.amount }) }

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
        // FIN2-04: "hoy" viene del dateTick observable (testeable e invalidable), no del reloj
        // leído inline.
        val esMesEnCurso = month == today.month && year == today.year
        val daysElapsed = if (esMesEnCurso) {
            today.day.coerceIn(1, daysInMonth)
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
        // FIN2-10: un mes sin ningún dato es "Sin datos" (neutro), no "Ajustado" (warning).
        // La señal es income/expense (NO balance==0, que enmascararía income == expense).
        val health = FinancialHealthAnalyzer.evaluate(
            savingsRate,
            balance,
            hasData = totalIncome != 0.0 || totalExpense != 0.0,
        )

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

        // FIN2-05: representación PLANA del monto para la búsqueda. Double.toString emite
        // notación científica desde 1e7 ("1.2E7"), por lo que buscar "12000000" no encontraba
        // montos de 8 dígitos (plausibles en CLP). Se crea una vez por emisión (DecimalFormat
        // no es thread-safe, no debe ser field compartido) con símbolos fijos en-US.
        val plainFmt = DecimalFormat("#.##", DecimalFormatSymbols(Locale.US))

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
                    plainFmt.format(tx.amount).contains(search) ||
                    // También matchea lo que el usuario ve en pantalla ("12.000.000").
                    com.example.util.FormatUtils.formatCLP(tx.amount).contains(search)

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
        quarterlyAverageMode,
        dateTick
    ) { flowsArray ->
        val budgets = flowsArray[0] as List<BudgetEntity>
        val transactions = flowsArray[1] as List<TransactionEntity>
        val categories = flowsArray[2] as List<CategoryEntity>
        val sMonth = flowsArray[3] as Int
        val sYear = flowsArray[4] as Int
        val isAvgMode = flowsArray[5] as Boolean
        val today = flowsArray[6] as Today

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
                // FIN2-03: si el mes seleccionado es el MES EN CURSO (parcial), la ventana son
                // los 3 meses CERRADOS anteriores (i=1..3); contarlo como mes completo sesgaba
                // el promedio a la baja (~22% en el ejemplo de la auditoría). Para meses
                // históricos se mantiene la ventana i=0..2 (el mes seleccionado y 2 previos).
                // Efecto documentado: una categoría con gasto SOLO en el mes en curso muestra
                // promedio 0 en este modo (el modo normal sí lo refleja).
                val esMesEnCurso = sMonth == today.month && sYear == today.year
                val offsets = if (esMesEnCurso) 1..3 else 0..2
                val threeMonthsRange = offsets.map { i ->
                    var m = sMonth - i
                    var y = sYear
                    if (m <= 0) {
                        m += 12
                        y -= 1
                    }
                    m to y
                }
                // CALC-03 (extraído a QuarterlyAverageCalculator, FIN2-09): numerador y
                // denominador derivan del MISMO conjunto; el divisor son los meses CON DATOS.
                QuarterlyAverageCalculator.average(
                    txs = transactions
                        .filter { it.type == "EXPENSE" && it.categoryName == category }
                        .map { it.date to it.amount },
                    window = threeMonthsRange,
                )
            } else {
                // Real spent amount for the selected month (FIN2-02: suma protegida).
                FinanceCalculator.sum(
                    transactions.filter { tx ->
                        if (tx.type == "EXPENSE" && tx.categoryName == category) {
                            val parts = tx.date.split("-")
                            if (parts.size >= 2) {
                                val txY = parts[0].toIntOrNull() ?: 0
                                val txM = parts[1].toIntOrNull() ?: 0
                                txY == sYear && txM == sMonth
                            } else false
                        } else false
                    }.map { it.amount }
                )
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
        // FIN2-02: sumas protegidas (un producto no-finito se ignora, no contamina el total).
        val totalInvested = FinanceCalculator.sum(stocks.map { it.quantity * it.purchasePrice })
        val totalCurrent = FinanceCalculator.sum(stocks.map { it.quantity * it.currentPrice })
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

    /**
     * UX2-12: años con movimientos para los chips de filtro (derivados de la lista COMPLETA,
     * nunca de la filtrada — sería circular). Incluye siempre el año del filtro activo (para que
     * el chip seleccionado no desaparezca) y el año actual como fallback con lista vacía.
     */
    val availableYears: StateFlow<List<Int>> = combine(
        allTransactions,
        filterYear,
        dateTick,
    ) { txs, fy, today ->
        val years = txs.mapNotNull { it.date.split("-").getOrNull(0)?.toIntOrNull() }
            .toMutableSet()
        if (years.isEmpty()) years.add(today.year)
        if (fy != 0) years.add(fy)
        years.sortedDescending()
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            .mapValues { entry -> FinanceCalculator.sum(entry.value.map { it.amount }) }
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
            .mapValues { entry -> FinanceCalculator.sum(entry.value.map { it.amount }) }
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

    /**
     * ARQ2-04: serializa los ciclos de refresco. Con 8 activos offline un ciclo puede superar
     * los 60 s del poller, solapándose con el siguiente (tráfico duplicado, escrituras
     * intercaladas). El manual espera al ciclo en curso (withLock) en vez de fallar en silencio.
     */
    private val refreshMutex = Mutex()

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
            refreshMutex.withLock {
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
                        // ARQ2-04: UPDATE parcial (solo currentPrice/updatedAt). El @Update de
                        // fila completa sobre el snapshot revertía ediciones de cantidad/precio
                        // de compra hechas por el usuario durante el ciclo de red (lost update).
                        repository.updateInvestmentPrice(
                            id = stock.id,
                            price = result.price,
                            now = System.currentTimeMillis(),
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
            } // refreshMutex.withLock
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

    /**
     * ARQ2-03: recibe la entidad ORIGINAL y aplica `copy` para preservar `createdAt` (antes se
     * construía una entidad nueva y cada edición re-sellaba createdAt con la hora de la edición,
     * rompiendo el orden del listado y la semántica de auditoría del campo).
     */
    fun updateTransaction(original: TransactionEntity, type: String, date: String, categoryName: String, description: String, amount: Double) {
        viewModelScope.launch {
            repository.updateTransaction(
                original.copy(
                    type = type,
                    date = date,
                    categoryName = categoryName,
                    description = description,
                    amount = amount,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    /**
     * ARQ2-05: el insert usa ABORT sobre el índice único (name, type); un duplicado ya no
     * machaca la categoría existente ni crashea — se reporta por [onResult].
     */
    fun addCategory(name: String, type: String, colorHex: String, iconName: String, onResult: (error: String?) -> Unit = {}) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                onResult("El nombre no puede estar vacío.")
                return@launch
            }
            // Validación previa amistosa (el índice único es la red de seguridad).
            val duplicate = allCategories.value.any {
                it.name.equals(trimmed, ignoreCase = true) && it.type == type
            }
            if (duplicate) {
                onResult("Ya existe una categoría \"$trimmed\" de ese tipo.")
                return@launch
            }
            runCatching {
                repository.insertCategory(
                    CategoryEntity(
                        name = trimmed,
                        type = type,
                        colorHex = colorHex,
                        iconName = iconName
                    )
                )
            }.fold(
                onSuccess = { onResult(null) },
                onFailure = { onResult("Ya existe una categoría con ese nombre.") },
            )
        }
    }

    /**
     * ARQ2-06: si cambió el nombre, propaga el rename a movimientos y presupuestos en una
     * transacción (el esquema referencia por nombre, sin FK). Valida colisión con otra categoría
     * para no fusionar silenciosamente historiales.
     */
    fun updateCategory(category: CategoryEntity, oldName: String, onResult: (error: String?) -> Unit = {}) {
        viewModelScope.launch {
            val newName = category.name.trim()
            if (newName.isEmpty()) {
                onResult("El nombre no puede estar vacío.")
                return@launch
            }
            if (newName == oldName) {
                runCatching { repository.updateCategory(category.copy(name = newName)) }
                    .fold(onSuccess = { onResult(null) }, onFailure = { onResult("No se pudo guardar la categoría.") })
                return@launch
            }
            val collision = allCategories.value.any {
                it.id != category.id && it.name.equals(newName, ignoreCase = true) && it.type == category.type
            }
            if (collision) {
                onResult("Ya existe otra categoría \"$newName\"; elige un nombre distinto.")
                return@launch
            }
            runCatching { repository.renameCategoryCascade(category.copy(name = newName), oldName) }
                .fold(onSuccess = { onResult(null) }, onFailure = { onResult("No se pudo renombrar la categoría.") })
        }
    }

    fun deleteCategory(category: CategoryEntity, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val txCount = repository.getTransactionCountForCategory(category.name)
            if (txCount > 0) {
                onResult(false, "No se puede eliminar la categoría porque tiene $txCount movimientos asociados.")
                return@launch
            }
            // UX2-04: tampoco se borra una categoría con presupuestos asociados (quedarían
            // presupuestos huérfanos invisibles que seguirían sumando en los análisis).
            val budgetCount = repository.getBudgetCountForCategory(category.name)
            if (budgetCount > 0) {
                onResult(false, "No se puede eliminar: la categoría tiene $budgetCount presupuesto(s) asociados.")
                return@launch
            }
            repository.deleteCategory(category)
            onResult(true, "Categoría eliminada con éxito.")
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
            // ARQ2-02: la fusión corre en una transacción Room (sin carrera de doble tap);
            // defensa en profundidad: una SQLiteConstraintException residual no debe crashear.
            runCatching {
                repository.addOrMergeInvestment(
                    ticker = ticker,
                    companyName = companyName,
                    quantity = quantity,
                    purchasePrice = purchasePrice,
                    currentPrice = currentPrice
                )
            }.onFailure {
                priceUpdateState.value = PriceUpdateState.Error("No se pudo guardar el activo. Intenta de nuevo.")
            }
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
            // FIN2-01: se CONSERVA la navegación a 5/2026 a propósito: los datos demo están
            // fechados en mayo 2026; sin esto el usuario vería pantallas vacías tras restaurar.
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

    /** Días reales del mes (incluye años bisiestos), sin depender del reloj del sistema. */
    private fun daysInMonth(month: Int, year: Int): Int {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, (month - 1).coerceIn(0, 11))
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    companion object {
        /** FIN2-04: fecha actual del calendario del dispositivo (default de [todayProvider]). */
        fun systemToday(): Today {
            val cal = Calendar.getInstance()
            return Today(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH) + 1,
                day = cal.get(Calendar.DAY_OF_MONTH),
            )
        }

        /**
         * A5: Factory explícita para construir el [FinanceViewModel]. Centraliza la creación del VM
         * en un único punto (en lugar de depender del default de `viewModel()`), lo que mejora la
         * testabilidad y deja la puerta abierta a inyectar dependencias en el futuro (FIN2-04:
         * acepta un [todayProvider] inyectable). Conserva el comportamiento de runtime actual.
         */
        fun provideFactory(
            application: Application,
            todayProvider: () -> Today = { systemToday() },
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(FinanceViewModel::class.java)) {
                        "Factory desconocida para ${modelClass.name}"
                    }
                    return FinanceViewModel(application, todayProvider) as T
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
    /**
     * Estado financiero del mes. FIN2-10: el default es SIN_DATOS (neutro) para que el pill
     * de advertencia "Ajustado" no aparezca durante la carga inicial ni en meses vacíos.
     */
    val health: FinancialHealth = FinancialHealth.SIN_DATOS,
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
