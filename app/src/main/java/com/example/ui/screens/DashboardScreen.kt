package com.example.ui.screens

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.graphics.toColorInt
import com.example.data.entity.CategoryEntity
import com.example.domain.FinancialHealth
import com.example.security.SecurityViewModel
import com.example.ui.components.AmountVisibilityToggle
import com.example.ui.components.CategoryChip
import com.example.ui.components.KpiCard
import com.example.ui.components.MainTopBar
import com.example.ui.components.EmptyState
import com.example.ui.components.FinanceCard
import com.example.ui.components.StatusPill
import com.example.ui.components.CountUpAmountText
import com.example.ui.components.LocalAmountsHidden
import com.example.ui.components.PrivacyAmountText
import com.example.ui.security.ConfirmPinDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.FinanceViewModel
import com.example.ui.viewmodel.SymbolSearchState
import com.example.util.FormatUtils
import kotlinx.coroutines.launch
import com.example.util.IconMapper
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier,
    securityViewModel: SecurityViewModel = viewModel(),
) {
    val dashboardData by viewModel.dashboardSummary.collectAsState()
    val rawCategories by viewModel.allCategories.collectAsState()
    val isPinSet by securityViewModel.isPinSet.collectAsState()
    val hideAmounts by securityViewModel.hideAmounts.collectAsState()
    val unbudgetedAlerts by viewModel.unbudgetedAlertsFlow.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val allInvestments by viewModel.allInvestments.collectAsState()

    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()

    // UX2-09: los flags de visibilidad de diálogos sobreviven a la muerte de proceso / rotación.
    var showAddDialog by rememberSaveable { mutableStateOf(value = false) }
    var dialogType by rememberSaveable { mutableStateOf("INCOME") } // INCOME, EXPENSE
    var showAddStockDialog by rememberSaveable { mutableStateOf(false) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }
    // UX2-09: reauthAction es una lambda y NO es saveable (meterla en rememberSaveable crashea
    // al serializar el Bundle); se mantiene en remember normal de forma deliberada.
    var reauthAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // FIN2-04: refresca la fecha "de hoy" al volver a primer plano (ON_RESUME) para que
    // daysElapsed/proyección no queden congelados si la app cruza la medianoche en segundo
    // plano. Mismo patrón de LifecycleEventObserver usado en MainActivity.AppRoot.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshDateTick()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // UX2-01: tokens semánticos del tema (con variante clara/oscura) en vez de los alias
    // estáticos Excel* que no se adaptaban al modo claro.
    val finance = LocalFinanceColors.current

    // Dropdowns / Calendars helper
    val monthNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- Custom Header ---
        MainTopBar(
            title = "Mi Panel Financiero",
            actions = {
                // Privacidad: alterna el enmascarado global de montos sensibles.
                AmountVisibilityToggle(
                    hidden = hideAmounts,
                    onToggle = { securityViewModel.toggleHideAmounts() },
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("toggle_hide_amounts"),
                )
                IconButton(onClick = { showResetConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Restaurar datos semilla",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        // Estado de carga vs vacío (UX-03): los flows de Room/SQLCipher arrancan con valor vacío
        // antes de resolver. Para no mostrar por un frame el estado vacío "Aún no hay datos" a un
        // usuario que sí tiene datos, se da una breve ventana de asentamiento mostrando un skeleton.
        // Si llegan datos, se muestra el contenido; si tras la ventana sigue vacío, el estado vacío.
        val isCompletelyEmpty = allTransactions.isEmpty() && allInvestments.isEmpty()
        var settled by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(300)
            settled = true
        }
        if (isCompletelyEmpty && !settled) {
            DashboardSkeleton(modifier = Modifier.weight(1f))
        } else if (isCompletelyEmpty) {
            DashboardEmptyState(
                modifier = Modifier.weight(1f),
                onAddIncome = { dialogType = "INCOME"; showAddDialog = true },
                onAddExpense = { dialogType = "EXPENSE"; showAddDialog = true },
                onAddStock = { showAddStockDialog = true },
            )
        } else
        // Scrollable Main Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Month & Year Selector ---
            FinanceCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = {
                        var m = selectedMonth - 1
                        var y = selectedYear
                        if (m <= 0) {
                            m = 12
                            y -= 1
                        }
                        viewModel.updateSelectedMonthYear(m, y)
                    }) {
                        Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Mes Anterior", tint = MaterialTheme.colorScheme.primary)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${monthNames[selectedMonth - 1]} $selectedYear",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    IconButton(onClick = {
                        var m = selectedMonth + 1
                        var y = selectedYear
                        if (m > 12) {
                            m = 1
                            y += 1
                        }
                        viewModel.updateSelectedMonthYear(m, y)
                    }) {
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Mes Siguiente", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // --- KPI CARDS ROW/GRID ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiCard(
                    modifier = Modifier.weight(1f),
                    title = "Ingresos Totales",
                    amount = FormatUtils.formatCLPCompact(dashboardData.incomeTotal),
                    icon = Icons.Default.ArrowUpward,
                    color = finance.success,
                    testTag = "kpi_income"
                )
                KpiCard(
                    modifier = Modifier.weight(1f),
                    title = "Gastos Totales",
                    amount = FormatUtils.formatCLPCompact(dashboardData.expenseTotal),
                    icon = Icons.Default.ArrowDownward,
                    color = finance.negative,
                    testTag = "kpi_expense"
                )
            }

            // --- HERO BALANCE CARD (degradado estático azul → cyan) ---
            val heroShape = MaterialTheme.shapes.large
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_balance_card")
                    .clip(heroShape)
                    .background(
                        Brush.linearGradient(listOf(AccentBlue, AccentCyan))
                    )
            ) {
                // Brillo radial decorativo (estático, sutil) en la esquina superior. Alpha reducido
                // para no contrarrestar el scrim oscuro de contraste (UX-02).
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
                                center = Offset(x = Float.POSITIVE_INFINITY, y = 0f),
                                radius = 600f,
                            )
                        )
                )
                // UX2-02: scrim negro UNIFORME (alpha 0.55) sobre todo el degradado. El gradiente
                // anterior (0.25→0.40) dejaba la zona superior clara (AccentCyan) por debajo de
                // 4.5:1 para las etiquetas translúcidas. Con este tratamiento, el texto blanco
                // pleno y las etiquetas al 85% quedan por encima de 4.5:1 incluso sobre el
                // extremo más claro del degradado.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "BALANCE DISPONIBLE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                // UX2-02: alpha >= 0.85 para mantener contraste sobre el degradado.
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        CountUpAmountText(
                            value = dashboardData.balanceTotal,
                            formatter = FormatUtils::formatCLP,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 28.sp
                            ),
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "Portafolio (USD)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        // UX2-02: alpha >= 0.85 para mantener contraste sobre el degradado.
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                )
                                PrivacyAmountText(
                                    amount = FormatUtils.formatUSD(dashboardData.portfolioTotal),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            // Cápsula de tendencia con la variación REAL respecto al mes anterior.
                            val mom = dashboardData.momBalanceChange
                            val pctText = if (mom != null) {
                                "${FormatUtils.formatPercentage2Signed(mom)} vs mes anterior"
                            } else {
                                "Sin comparación previa"
                            }
                            // UX2-02: fondo sólido oscuro (en vez de blanco translúcido) para que
                            // el texto blanco 100% de la cápsula conserve contraste suficiente.
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = pctText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // --- MÉTRICAS INTELIGENTES DEL MES ---
            FinanceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_metrics_card")
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Métricas del Mes",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        // Estado financiero (no expone montos).
                        val health = dashboardData.health
                        val healthColor = when (health) {
                            FinancialHealth.EXCELENTE -> finance.success
                            FinancialHealth.BUENO -> MaterialTheme.colorScheme.primary
                            FinancialHealth.AJUSTADO -> finance.warning
                            FinancialHealth.CRITICO -> finance.negative
                            // FIN2-10: mes sin datos — estado NEUTRO, no es una advertencia.
                            FinancialHealth.SIN_DATOS -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        StatusPill(text = health.label, color = healthColor)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Fila 1: Tasa de ahorro + Gasto diario promedio.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCell(
                            modifier = Modifier.weight(1f),
                            label = "Tasa de ahorro",
                            // El porcentaje no revela montos exactos: siempre visible.
                            value = FormatUtils.formatPercentage(dashboardData.savingsRate),
                            valueColor = if (dashboardData.savingsRate >= 0) finance.success else finance.negative,
                            sensitive = false
                        )
                        MetricCell(
                            modifier = Modifier.weight(1f),
                            label = "Gasto diario prom.",
                            value = FormatUtils.formatCLPCompact(dashboardData.dailyAvgSpending),
                            sensitive = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Fila 2: Proyección a fin de mes + Categoría con mayor gasto.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCell(
                            modifier = Modifier.weight(1f),
                            label = "Proyección fin de mes",
                            value = FormatUtils.formatCLPCompact(dashboardData.projectedMonthEndSpending),
                            sensitive = true
                        )
                        MetricCell(
                            modifier = Modifier.weight(1f),
                            label = "Mayor gasto",
                            value = dashboardData.topCategoryName?.let {
                                "$it · ${FormatUtils.formatCLPCompact(dashboardData.topCategoryAmount)}"
                            } ?: "Sin gastos",
                            sensitive = dashboardData.topCategoryName != null
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Fila 3: Variación de ingresos y gastos (porcentajes, no sensibles).
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val incChange = dashboardData.momIncomeChange
                        val expChange = dashboardData.momExpenseChange
                        MetricCell(
                            modifier = Modifier.weight(1f),
                            label = "Ingresos vs mes ant.",
                            value = incChange?.let { FormatUtils.formatPercentage2Signed(it) }
                                ?: "Sin comparación",
                            valueColor = when {
                                incChange == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                incChange >= 0 -> finance.success
                                else -> finance.negative
                            },
                            sensitive = false
                        )
                        MetricCell(
                            modifier = Modifier.weight(1f),
                            label = "Gastos vs mes ant.",
                            value = expChange?.let { FormatUtils.formatPercentage2Signed(it) }
                                ?: "Sin comparación",
                            valueColor = when {
                                expChange == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                expChange <= 0 -> finance.success
                                else -> finance.negative
                            },
                            sensitive = false
                        )
                    }
                }
            }

            // --- Quick Actions ---
            FinanceCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(12.dp)
            ) {
                Column {
                    Text(
                        "Acciones Rápidas",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { dialogType = "INCOME"; showAddDialog = true },
                            // UX2-01: contentColor explícito — onPrimary heredado no garantiza
                            // contraste sobre el contenedor semántico en tema claro.
                            colors = ButtonDefaults.buttonColors(
                                containerColor = finance.success,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_add_income"),
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ingreso", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { dialogType = "EXPENSE"; showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = finance.negative,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_add_expense"),
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Gasto", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { showAddStockDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_add_stock"),
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Acción", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- Chart 1: Month comparison (Ingresos vs Gastos) ---
            FinanceCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        "Comparativa Mensual (Ingresos vs Gastos)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (dashboardData.monthlySummaries.isEmpty()) {
                        Text("No hay suficientes datos para el gráfico.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        // Custom Canvas-drawn comparison bars
                        val summariesList = dashboardData.monthlySummaries
                        val maxVal = (summariesList.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1.0).coerceAtLeast(100.0)
                        // Color de las gridlines (capturado fuera del DrawScope del Canvas).
                        val gridlineColor = MaterialTheme.colorScheme.outlineVariant

                        // Accesibilidad (UX-04): el Canvas es un dibujo puro; sin esto TalkBack no
                        // anuncia nada. Se construye un resumen textual por mes RESPETANDO el modo
                        // privacidad: si los montos están ocultos, no se filtran a TalkBack.
                        val amountsHidden = LocalAmountsHidden.current
                        val chartDescription = if (amountsHidden) {
                            "Gráfico comparativo de ingresos y gastos por mes. Montos ocultos por privacidad."
                        } else {
                            "Comparativa mensual de ingresos y gastos. " +
                                summariesList.joinToString(separator = ". ") { sum ->
                                    "${monthNames[sum.month - 1]} ${sum.year}: " +
                                        "ingresos ${FormatUtils.formatCLP(sum.income)}, " +
                                        "gastos ${FormatUtils.formatCLP(sum.expense)}"
                                }
                        }

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .semantics { contentDescription = chartDescription }
                        ) {
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            val barGroupSpacing = canvasWidth / (summariesList.size)
                            val barWidth = 14.dp.toPx()
                            val spacingBetweenBars = 3.dp.toPx()

                            summariesList.forEachIndexed { idx, sum ->
                                val baseX = (idx * barGroupSpacing) + (barGroupSpacing / 2)

                                // Income height normalized
                                val incomeHeight = (sum.income / maxVal) * (canvasHeight - 35.dp.toPx())
                                val expenseHeight = (sum.expense / maxVal) * (canvasHeight - 35.dp.toPx())

                                // Draw standard gridlines
                                drawLine(
                                    color = gridlineColor,
                                    start = Offset(0f, canvasHeight - 25.dp.toPx()),
                                    end = Offset(canvasWidth, canvasHeight - 25.dp.toPx()),
                                    strokeWidth = 1.dp.toPx()
                                )

                                // Income bar
                                drawRect(
                                    color = finance.success,
                                    topLeft = Offset(baseX - barWidth - spacingBetweenBars, canvasHeight - 25.dp.toPx() - incomeHeight.toFloat()),
                                    size = Size(barWidth, incomeHeight.toFloat())
                                )

                                // Expense bar
                                drawRect(
                                    color = finance.negative,
                                    topLeft = Offset(baseX + spacingBetweenBars, canvasHeight - 25.dp.toPx() - expenseHeight.toFloat()),
                                    size = Size(barWidth, expenseHeight.toFloat())
                                )
                            }
                        }

                        // Labels below Canvas to align accurately
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            summariesList.forEach { sum ->
                                Text(
                                    text = "${monthNames[sum.month - 1].take(3)} '${sum.year.toString().takeLast(2)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant),
                                    modifier = Modifier.width(55.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        // Leyenda con icono direccional ADEMÁS del color, para no depender solo del
                        // par verde/rojo (daltonismo deutan/protan). UX-04.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = finance.success,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ingresos", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = finance.negative,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gastos", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // --- Chart 2: Category Breakdown list ---
            FinanceCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        "Desglose de Gastos por Categoría",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Alertas: categorías sin presupuesto con gasto relevante (sin exponer montos).
                    if (unbudgetedAlerts.isNotEmpty()) {
                        val warningColor = LocalFinanceColors.current.warning
                        unbudgetedAlerts.forEach { alert ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .background(warningColor.copy(alpha = 0.1f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = warningColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${alert.categoryName} sin presupuesto · ${FormatUtils.formatPercentage(alert.percentOfTotal)} del gasto",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = warningColor,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (dashboardData.categorySpendingList.isEmpty()) {
                        EmptyState(
                            message = "No hay gastos registrados en este mes.",
                            icon = Icons.Default.Category
                        )
                    } else {
                        dashboardData.categorySpendingList.forEach { item ->
                            val categoryObj = rawCategories.firstOrNull { it.name == item.categoryName }
                            // Fallback claro y legible (AccentBlue #4D8DFF, contraste ~5:1 sobre la
                            // superficie oscura) en vez del antiguo #1F4E79, muy oscuro como tint. UX-09.
                            val colHex = categoryObj?.colorHex ?: "#4D8DFF"
                            val iconName = categoryObj?.iconName ?: "Category"
                            val categoryColor = try {
                                Color(colHex.toColorInt())
                            } catch (_: Exception) {
                                MaterialTheme.colorScheme.primary
                            }

                            // Semántica fusionada (UX-04): TalkBack lee nombre + monto + porcentaje
                            // como una sola unidad; el monto sigue enmascarado por PrivacyAmountText
                            // según privacidad. La barra de progreso no aporta texto adicional, así
                            // que no se lee dos veces el porcentaje.
                            Column(
                                modifier = Modifier
                                    .padding(vertical = 6.dp)
                                    .semantics(mergeDescendants = true) {}
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                               ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(categoryColor.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = IconMapper.mapToIcon(iconName),
                                                contentDescription = null,
                                                tint = categoryColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            item.categoryName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        PrivacyAmountText(
                                            amount = FormatUtils.formatCLP(item.amount),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            textAlign = TextAlign.End,
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        // El porcentaje no revela el monto exacto: siempre visible.
                                        Text(
                                            text = "(${FormatUtils.formatPercentage(item.percentage)})",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            ),
                                            textAlign = TextAlign.End
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                // Horizontal Bar represent percentage
                                val animatedPct by animateFloatAsState(
                                    targetValue = item.percentage.toFloat(),
                                    animationSpec = Motion.long(),
                                    label = "categoryPct",
                                )
                                LinearProgressIndicator(
                                    progress = { animatedPct },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = categoryColor,
                                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS FOR QUICK ACTIONS ---

    // 1. Double Dropdown Add Transaction dialog
    if (showAddDialog) {
        AddTransactionDialog(
            type = dialogType,
            categories = rawCategories.filter { it.type == dialogType },
            onDismiss = { showAddDialog = false }
        ) { date, category, desc, amount ->
            viewModel.addTransaction(dialogType, date, category, desc, amount)
            showAddDialog = false
        }
    }

    // 2. Add Stock investment dialog
    if (showAddStockDialog) {
        AddStockDialog(
            viewModel = viewModel,
            onDismiss = { showAddStockDialog = false },
            onSave = { ticker, name, qty, buyPrice, currentPrice ->
                viewModel.addStock(ticker, name, qty, buyPrice, currentPrice)
                showAddStockDialog = false
            }
        )
    }

    // 3. Confirmación antes de restaurar datos semilla (acción destructiva).
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("¿Restaurar datos semilla?", fontWeight = FontWeight.Bold) },
            text = { Text("Esto sobrescribirá tus movimientos actuales con los balances originales (Ingresos: CLP 1.090.094, Gastos: CLP 748.825).") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        if (isPinSet) reauthAction = { viewModel.resetToSeedData() }
                        else viewModel.resetToSeedData()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                    )
                ) { Text("Restaurar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    // Reautenticación para la acción sensible (solo si hay PIN configurado).
    reauthAction?.let { action ->
        ConfirmPinDialog(
            title = "Confirmar identidad",
            message = "Ingresa tu PIN para restaurar los datos semilla.",
            confirmText = "Confirmar",
            onDismiss = { reauthAction = null },
            onConfirm = { pin, onError ->
                // SEC: verifyPin ahora recibe CharArray y devuelve el mensaje real del intento
                // (incluye lockout "Demasiados intentos..."); se muestra en vez de un genérico.
                securityViewModel.verifyPin(pin.toCharArray()) { ok, error ->
                    if (ok) {
                        reauthAction = null
                        action()
                    } else {
                        onError(error ?: "PIN incorrecto.")
                    }
                }
            }
        )
    }
}

/**
 * Skeleton de carga del Dashboard (UX-03): superficies neutras del tamaño aproximado de las
 * tarjetas reales mientras Room/SQLCipher resuelve la primera emisión. No muestra números ni
 * datos sensibles; solo placeholders. Evita el parpadeo del estado vacío en usuarios con datos.
 */
@Composable
private fun DashboardSkeleton(
    modifier: Modifier = Modifier,
) {
    val placeholder = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Fila de KPIs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(placeholder)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(placeholder)
            )
        }
        // Hero de balance
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(placeholder)
        )
        // Tarjeta de métricas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(placeholder)
        )
    }
}

/**
 * Estado vacío del Dashboard: se muestra cuando no existe ningún dato financiero. No expone
 * números inventados; invita a registrar el primer movimiento/activo. "Restaurar datos demo"
 * sigue disponible (manual, con reautenticación) desde Ajustes.
 */
@Composable
private fun DashboardEmptyState(
    onAddIncome: () -> Unit,
    onAddExpense: () -> Unit,
    onAddStock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // UX2-01: tokens del tema en vez de alias estáticos Excel*.
    val finance = LocalFinanceColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Aún no hay datos financieros",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Registra tu primer ingreso, gasto o inversión para empezar. " +
                "Puedes cargar datos de ejemplo desde Ajustes → Restaurar datos demo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onAddIncome,
                // UX2-01: contentColor explícito para asegurar contraste en tema claro.
                colors = ButtonDefaults.buttonColors(
                    containerColor = finance.success,
                    contentColor = Color.White,
                ),
                modifier = Modifier.weight(1f).testTag("empty_add_income"),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ingreso", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onAddExpense,
                colors = ButtonDefaults.buttonColors(
                    containerColor = finance.negative,
                    contentColor = Color.White,
                ),
                modifier = Modifier.weight(1f).testTag("empty_add_expense"),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Gasto", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onAddStock,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ),
                modifier = Modifier.weight(1f).testTag("empty_add_stock"),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Acción", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Celda de métrica reutilizable del dashboard. Si [sensitive] es true, el valor se enmascara
 * cuando la privacidad global está activa; los porcentajes y estados se marcan como no sensibles.
 */
@Composable
private fun MetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
    sensitive: Boolean = true,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .padding(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (sensitive) {
            PrivacyAmountText(
                amount = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = valueColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = valueColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun AddTransactionDialog(
    type: String,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (date: String, category: String, desc: String, amount: Double) -> Unit
) {
    // UX2-01: tokens del tema en vez de alias estáticos Excel*.
    val finance = LocalFinanceColors.current
    // UX2-09: campos del formulario sobreviven a muerte de proceso / rotación.
    var amountStr by rememberSaveable { mutableStateOf("") }
    var descStr by rememberSaveable { mutableStateOf("") }

    // Date Picker Management. UX2-03/FIN2-01: arranca en la fecha de hoy (antes se forzaba
    // una fecha fija 2026-05-29 que sembraba movimientos en el mes equivocado).
    val calendar = Calendar.getInstance()

    // FIN2-11: Locale.US fijo — es el formato INTERNO de persistencia. Con getDefault(), en
    // locales con dígitos no latinos (p. ej. árabe) se emitían dígitos localizados que rompían
    // el ORDER BY date.
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    var dateStr by rememberSaveable { mutableStateOf(sdf.format(calendar.time)) }

    val context = LocalContext.current
    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, y: Int, m: Int, d: Int ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, y)
                set(Calendar.MONTH, m)
                set(Calendar.DAY_OF_MONTH, d)
            }
            dateStr = sdf.format(cal.time)
        },
        calendar[Calendar.YEAR],
        calendar[Calendar.MONTH],
        calendar[Calendar.DAY_OF_MONTH]
    )

    // Category Selected (UX2-09: saveable; el dropdown abierto es estado transitorio).
    var selectedCategoryName by rememberSaveable { mutableStateOf(categories.firstOrNull()?.name ?: "") }
    var expandedCategoryDropdown by remember { mutableStateOf(false) }

    // Validation fields (UX2-09)
    var errorMsg by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (type == "INCOME") "Agregar Ingreso" else "Agregar Gasto",
                fontWeight = FontWeight.Bold,
                color = if (type == "INCOME") finance.success else finance.negative
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Type label info
                Text(
                    "Ingresa los detalles del movimiento para el panel chileno.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Sin categorías de este tipo: guía al usuario (la app arranca sin categorías).
                if (categories.isEmpty()) {
                    Text(
                        "No tienes categorías de este tipo todavía. Crea una en Ajustes → Categorías para poder registrar el movimiento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = finance.negative,
                        fontWeight = FontWeight.Medium
                    )
                }

                // UX2-08: el error se compone SIEMPRE (no condicional) y se marca como región
                // viva para que TalkBack anuncie el mensaje cuando cambia.
                Text(
                    text = errorMsg,
                    color = finance.negative,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )

                // Date Picker trigger button
                OutlinedButton(
                    onClick = { datePickerDialog.show() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Fecha: $dateStr")
                }

                // Category selection dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandedCategoryDropdown = true },
                        modifier = Modifier.fillMaxWidth().testTag("category_dropdown_trigger"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Categoría: $selectedCategoryName")
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = expandedCategoryDropdown,
                        onDismissRequest = { expandedCategoryDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    selectedCategoryName = cat.name
                                    expandedCategoryDropdown = false
                                }
                            )
                        }
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Monto (CLP)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("Ej: 45000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("amount_input"),
                    shape = RoundedCornerShape(8.dp)
                )

                // Description
                OutlinedTextField(
                    value = descStr,
                    onValueChange = { descStr = it },
                    label = { Text("Descripción") },
                    placeholder = { Text("Opcional") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("description_input"),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    // FIN2-02: rechaza también NaN/Infinity ("NaN" <= 0.0 es false y "9e999"
                    // parsea como Infinity; ambos se persistían y corrompían los totales).
                    if (amt == null || !amt.isFinite() || amt <= 0.0) {
                        errorMsg = "El monto debe ser un número entero mayor a 0."
                        return@Button
                    }
                    if (selectedCategoryName.isEmpty()) {
                        errorMsg = "La categoría es obligatoria."
                        return@Button
                    }
                    onSave(dateStr, selectedCategoryName, descStr.ifEmpty { "Movimiento" }, amt)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (type == "INCOME") finance.success else finance.negative,
                    contentColor = Color.White,
                ),
                modifier = Modifier.testTag("submit_transaction_button")
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun AddStockDialog(
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    onSave: (ticker: String, name: String, qty: Double, buyPrice: Double, currentPrice: Double) -> Unit
) {
    // UX2-01: tokens del tema en vez de alias estáticos Excel*.
    val finance = LocalFinanceColors.current
    // UX2-09: campos del formulario sobreviven a muerte de proceso / rotación.
    var ticker by rememberSaveable { mutableStateOf("") }
    var companyName by rememberSaveable { mutableStateOf("") }
    var quantityStr by rememberSaveable { mutableStateOf("") }
    var purchasePriceStr by rememberSaveable { mutableStateOf("") }
    var currentPriceStr by rememberSaveable { mutableStateOf("") }

    var errorMsg by rememberSaveable { mutableStateOf("") }

    // Buscador de activos (Finnhub /search). Degrada a entrada manual si no hay API key.
    val searchState by viewModel.symbolSearchState.collectAsState()
    val query by viewModel.symbolQuery.collectAsState()
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { viewModel.clearSymbolSearch() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Agregar Activo / Acción", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Registra inversiones de bolsa en USD. Se admiten fracciones de acciones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // UX2-08: el error se compone SIEMPRE (no condicional) y se marca como región
                // viva para que TalkBack anuncie el mensaje cuando cambia.
                Text(
                    text = errorMsg,
                    color = finance.negative,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )

                // --- Buscador de activos: encuentra cualquier símbolo por nombre o ticker ---
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.onSymbolQueryChange(it) },
                    label = { Text("Buscar activo (nombre o ticker)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("symbol_search_field"),
                    shape = RoundedCornerShape(8.dp),
                    trailingIcon = {
                        if (searchState is SymbolSearchState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }
                )
                when (val s = searchState) {
                    is SymbolSearchState.NeedsApiKey -> Text(
                        "Configura una API key para buscar. Puedes ingresar el ticker manualmente.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    is SymbolSearchState.Empty -> Text(
                        "Sin resultados. Revisa el texto o ingresa el ticker manualmente.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    is SymbolSearchState.Results -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        s.matches.forEach { m ->
                            Surface(
                                onClick = {
                                    ticker = m.symbol
                                    companyName = m.description
                                    viewModel.onSymbolQueryChange(m.symbol)
                                    scope.launch {
                                        viewModel.prefillPrice(m.symbol)?.let { p ->
                                            currentPriceStr = p.toString()
                                            if (purchasePriceStr.isBlank()) purchasePriceStr = p.toString()
                                        }
                                    }
                                },
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .testTag("symbol_result_${m.symbol}")
                            ) {
                                Column(Modifier.padding(vertical = 6.dp, horizontal = 8.dp)) {
                                    Text(m.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${m.symbol} · ${m.type}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    else -> {}
                }

                OutlinedTextField(
                    value = ticker,
                    onValueChange = { ticker = it.uppercase() },
                    label = { Text("Ticker seleccionado (editable)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("ticker_input"),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = companyName,
                    onValueChange = { companyName = it },
                    label = { Text("Empresa (Ej: Apple Inc.)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = { quantityStr = it },
                    label = { Text("Cantidad (Permite decimales)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = purchasePriceStr,
                    onValueChange = { purchasePriceStr = it },
                    label = { Text("Precio Compra (USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = currentPriceStr,
                    onValueChange = { currentPriceStr = it },
                    label = { Text("Precio Actual (USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = quantityStr.toDoubleOrNull()
                    val bPrice = purchasePriceStr.toDoubleOrNull()
                    val cPrice = currentPriceStr.toDoubleOrNull() ?: bPrice

                    if (ticker.trim().isEmpty()) {
                        errorMsg = "El Ticker es obligatorio."
                        return@Button
                    }
                    if (companyName.trim().isEmpty()) {
                        errorMsg = "La empresa es obligatoria."
                        return@Button
                    }
                    // FIN2-02: rechaza también NaN/Infinity ("NaN" <= 0.0 es false y "9e999"
                    // parsea como Infinity; ambos se persistían y corrompían los totales).
                    if (qty == null || !qty.isFinite() || qty <= 0.0) {
                        errorMsg = "Cantidad debe ser un número positivo."
                        return@Button
                    }
                    if (bPrice == null || !bPrice.isFinite() || bPrice <= 0.0) {
                        errorMsg = "El precio de compra debe ser mayor a 0."
                        return@Button
                    }
                    if (cPrice == null || !cPrice.isFinite() || cPrice <= 0.0) {
                        errorMsg = "El precio actual debe ser mayor a 0."
                        return@Button
                    }

                    onSave(ticker, companyName, qty, bPrice, cPrice)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ),
                modifier = Modifier.testTag("submit_stock_button")
            ) {
                Text("Invertir", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
