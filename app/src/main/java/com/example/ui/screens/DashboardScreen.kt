package com.example.ui.screens

import android.app.DatePickerDialog
import android.widget.DatePicker
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.graphics.toColorInt
import com.example.data.entity.CategoryEntity
import com.example.domain.FinancialHealth
import com.example.security.SecurityViewModel
import com.example.ui.components.CategoryChip
import com.example.ui.components.KpiCard
import com.example.ui.components.MainTopBar
import com.example.ui.components.EmptyState
import com.example.ui.components.PrivacyAmountText
import com.example.ui.security.ConfirmPinDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.FormatUtils
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

    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()

    var showAddDialog by remember { mutableStateOf(value = false) }
    var dialogType by remember { mutableStateOf("INCOME") } // INCOME, EXPENSE
    var showAddStockDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var reauthAction by remember { mutableStateOf<(() -> Unit)?>(null) }

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
            title = "📊 Mi Panel Financiero",
            containerColor = MaterialTheme.colorScheme.primary,
            actions = {
                // Privacidad: alterna el enmascarado global de montos sensibles.
                IconButton(
                    onClick = { securityViewModel.toggleHideAmounts() },
                    modifier = Modifier.testTag("toggle_hide_amounts")
                ) {
                    Icon(
                        imageVector = if (hideAmounts) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (hideAmounts) "Mostrar montos" else "Ocultar montos",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { showResetConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Restaurar datos semilla",
                        tint = Color.White
                    )
                }
            }
        )

        // Scrollable Main Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Month & Year Selector ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
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
                    color = ExcelGreen,
                    testTag = "kpi_income"
                )
                KpiCard(
                    modifier = Modifier.weight(1f),
                    title = "Gastos Totales",
                    amount = FormatUtils.formatCLPCompact(dashboardData.expenseTotal),
                    icon = Icons.Default.ArrowDownward,
                    color = ExcelRed,
                    testTag = "kpi_expense"
                )
            }

            // --- HERO BALANCE CARD (Sophisticated Dark HTML style) ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_balance_card"),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    // Decorative background bubble
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 35.dp, y = (-35).dp)
                            .background(Color.White.copy(alpha = 0.08f), shape = CircleShape)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "BALANCE DISPONIBLE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        PrivacyAmountText(
                            amount = FormatUtils.formatCLP(dashboardData.balanceTotal),
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
                                        color = Color.White.copy(alpha = 0.65f),
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
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.2f))
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_metrics_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "🧠 Métricas del Mes",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        // Estado financiero (no expone montos).
                        val health = dashboardData.health
                        val healthColor = when (health) {
                            FinancialHealth.EXCELENTE -> ExcelGreen
                            FinancialHealth.BUENO -> ExcelMediumBlue
                            FinancialHealth.AJUSTADO -> Color(0xFFEF6C00)
                            FinancialHealth.CRITICO -> ExcelRed
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(healthColor.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = health.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = healthColor,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
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
                            valueColor = if (dashboardData.savingsRate >= 0) ExcelGreen else ExcelRed,
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
                                incChange >= 0 -> ExcelGreen
                                else -> ExcelRed
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
                                expChange <= 0 -> ExcelGreen
                                else -> ExcelRed
                            },
                            sensitive = false
                        )
                    }
                }
            }

            // --- Quick Actions ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
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
                            colors = ButtonDefaults.buttonColors(containerColor = ExcelGreen),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_add_income"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ingreso", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { dialogType = "EXPENSE"; showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ExcelRed),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_add_expense"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Gasto", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { showAddStockDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ExcelMediumBlue),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_add_stock"),
                            shape = RoundedCornerShape(8.dp),
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "📈 Comparativa Mensual (Ingresos vs Gastos)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (dashboardData.monthlySummaries.isEmpty()) {
                        Text("No hay suficientes datos para el gráfico.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    } else {
                        // Custom Canvas-drawn comparison bars
                        val summariesList = dashboardData.monthlySummaries
                        val maxVal = (summariesList.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1.0).coerceAtLeast(100.0)

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
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
                                    color = Color.LightGray.copy(alpha = 0.4f),
                                    start = Offset(0f, canvasHeight - 25.dp.toPx()),
                                    end = Offset(canvasWidth, canvasHeight - 25.dp.toPx()),
                                    strokeWidth = 1.dp.toPx()
                                )

                                // Income bar
                                drawRect(
                                    color = ExcelGreen,
                                    topLeft = Offset(baseX - barWidth - spacingBetweenBars, canvasHeight - 25.dp.toPx() - incomeHeight.toFloat()),
                                    size = Size(barWidth, incomeHeight.toFloat())
                                )

                                // Expense bar
                                drawRect(
                                    color = ExcelRed,
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
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray),
                                    modifier = Modifier.width(55.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(10.dp).background(ExcelGreen))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ingresos", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(16.dp))
                            Box(modifier = Modifier.size(10.dp).background(ExcelRed))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gastos", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // --- Chart 2: Category Breakdown list ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "📌 Desglose de Gastos por Categoría",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Alertas: categorías sin presupuesto con gasto relevante (sin exponer montos).
                    if (unbudgetedAlerts.isNotEmpty()) {
                        unbudgetedAlerts.forEach { alert ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFEF6C00).copy(alpha = 0.1f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFEF6C00),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${alert.categoryName} sin presupuesto · ${FormatUtils.formatPercentage(alert.percentOfTotal)} del gasto",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFEF6C00),
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
                            val colHex = categoryObj?.colorHex ?: "#1F4E79"
                            val iconName = categoryObj?.iconName ?: "Category"
                            val categoryColor = try {
                                Color(colHex.toColorInt())
                            } catch (_: Exception) {
                                MaterialTheme.colorScheme.primary
                            }

                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
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
                                LinearProgressIndicator(
                                    progress = { item.percentage.toFloat() },
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
            title = { Text("⚡ ¿Restaurar datos semilla?", fontWeight = FontWeight.Bold) },
            text = { Text("Esto sobrescribirá tus movimientos actuales con los balances originales (Ingresos: CLP 1.090.094, Gastos: CLP 748.825).") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        if (isPinSet) reauthAction = { viewModel.resetToSeedData() }
                        else viewModel.resetToSeedData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExcelDarkBlue)
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
                securityViewModel.verifyPin(pin) { ok ->
                    if (ok) {
                        reauthAction = null
                        action()
                    } else {
                        onError("PIN incorrecto.")
                    }
                }
            }
        )
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
            .clip(RoundedCornerShape(12.dp))
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
    var amountStr by remember { mutableStateOf("") }
    var descStr by remember { mutableStateOf("") }

    // Date Picker Management
    val calendar = Calendar.getInstance()
    // Pre-seed year 2026 month May (base indices 0-11: May is 4)
    calendar[Calendar.YEAR] = 2026
    calendar[Calendar.MONTH] = Calendar.MAY
    calendar[Calendar.DAY_OF_MONTH] = 29

    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    var dateStr by remember { mutableStateOf(sdf.format(calendar.time)) }

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

    // Category Selected
    var selectedCategoryName by remember { mutableStateOf(categories.firstOrNull()?.name ?: "") }
    var expandedCategoryDropdown by remember { mutableStateOf(false) }

    // Validation fields
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (type == "INCOME") "➕ Agregar Ingreso" else "💸 Agregar Gasto",
                fontWeight = FontWeight.Bold,
                color = if (type == "INCOME") ExcelGreen else ExcelRed
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
                    color = Color.Gray
                )

                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = ExcelRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }

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
                    if (amt == null || amt <= 0.0) {
                        errorMsg = "El monto debe ser un número entero mayor a 0."
                        return@Button
                    }
                    if (selectedCategoryName.isEmpty()) {
                        errorMsg = "La categoría es obligatoria."
                        return@Button
                    }
                    onSave(dateStr, selectedCategoryName, descStr.ifEmpty { "Movimiento" }, amt)
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (type == "INCOME") ExcelGreen else ExcelRed),
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
    onDismiss: () -> Unit,
    onSave: (ticker: String, name: String, qty: Double, buyPrice: Double, currentPrice: Double) -> Unit
) {
    var ticker by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var quantityStr by remember { mutableStateOf("") }
    var purchasePriceStr by remember { mutableStateOf("") }
    var currentPriceStr by remember { mutableStateOf("") }

    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("📈 Agregar Activo / Acción", fontWeight = FontWeight.Bold, color = ExcelMediumBlue)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Registra inversiones de bolsa en USD. Se admiten fracciones de acciones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = ExcelRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value = ticker,
                    onValueChange = { ticker = it.uppercase() },
                    label = { Text("Ticker (Ej: AAPL)") },
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
                    if (qty == null || qty <= 0.0) {
                        errorMsg = "Cantidad debe ser un número positivo."
                        return@Button
                    }
                    if (bPrice == null || bPrice <= 0.0) {
                        errorMsg = "El precio de compra debe ser mayor a 0."
                        return@Button
                    }
                    if (cPrice == null || cPrice <= 0.0) {
                        errorMsg = "El precio actual debe ser mayor a 0."
                        return@Button
                    }

                    onSave(ticker, companyName, qty, bPrice, cPrice)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ExcelMediumBlue),
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
