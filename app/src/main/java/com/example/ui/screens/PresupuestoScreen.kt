package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.BudgetRecommendation
import com.example.ui.components.EmptyState
import com.example.ui.components.FinanceCard
import com.example.ui.components.MainTopBar
import com.example.ui.components.PrivacyAmountText
import com.example.ui.theme.Motion
import com.example.ui.theme.ExcelGreen
import com.example.ui.theme.ExcelRed
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.viewmodel.BudgetReportItem
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.FormatUtils
import com.example.ui.theme.LocalReducedMotion
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresupuestoScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier,
) {
    val budgetReports by viewModel.budgetReportFlow.collectAsState()
    val budgetSummary by viewModel.budgetSummaryFlow.collectAsState()
    val recommendations by viewModel.budgetRecommendationsFlow.collectAsState()
    val isAvgMode by viewModel.quarterlyAverageMode.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()

    var showEditBudgetDialog by remember { mutableStateOf<BudgetReportItem?>(null) }

    val reduced = LocalReducedMotion.current
    val budgetsListState = rememberLazyListState()

    val monthNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- Header ---
        MainTopBar(
            title = "Presupuesto Mensual",
            elevated = budgetsListState.canScrollBackward
        )

        // --- Option Controls ---
        FinanceCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            contentPadding = PaddingValues(12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Mes Activo: ${monthNames[selectedMonth - 1]} $selectedYear",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Establece tus límites mensuales por categoría",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Simple Month Quick Changer
                    Row {
                        IconButton(onClick = {
                            var m = selectedMonth - 1
                            var y = selectedYear
                            if (m <= 0) {
                                m = 12
                                y -= 1
                            }
                            viewModel.updateSelectedMonthYear(m, y)
                        }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Mes Anterior", tint = MaterialTheme.colorScheme.primary)
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
                            Icon(Icons.Default.ChevronRight, contentDescription = "Mes Siguiente", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Toggle "Quarterly Average Trimestral Mode"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.quarterlyAverageMode.value = !isAvgMode }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isAvgMode,
                        onCheckedChange = { viewModel.quarterlyAverageMode.value = it },
                        modifier = Modifier.testTag("avg_mode_checkbox")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Modo Promedio Trimestral",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Compara presupuestos contra el promedio gastado de los últimos 3 meses.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // --- RESUMEN DEL PRESUPUESTO ---
        FinanceCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .testTag("budget_summary_card"),
            contentPadding = PaddingValues(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Presupuesto total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PrivacyAmountText(
                            amount = FormatUtils.formatCLP(budgetSummary.totalBudget),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Gastado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PrivacyAmountText(
                            amount = FormatUtils.formatCLP(budgetSummary.totalSpent),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (budgetSummary.usagePercent > 1.0) ExcelRed else MaterialTheme.colorScheme.onSurface
                            ),
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Disponible", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PrivacyAmountText(
                            amount = FormatUtils.formatCLP(budgetSummary.remaining),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (budgetSummary.remaining >= 0) ExcelGreen else ExcelRed
                            ),
                        )
                    }
                }

                // Barra global de uso (porcentaje, no revela montos).
                val animatedGlobalUsage by animateFloatAsState(
                    targetValue = budgetSummary.usagePercent.coerceIn(0.0, 1.0).toFloat(),
                    animationSpec = Motion.long(reduced),
                    label = "globalUsage",
                )
                // El porcentaje (no sensible) se anima en sincronía con la barra.
                val animatedGlobalPercent by animateIntAsState(
                    targetValue = (budgetSummary.usagePercent * 100).roundToInt(),
                    animationSpec = Motion.long(reduced),
                    label = "globalUsagePercent",
                )
                LinearProgressIndicator(
                    progress = { animatedGlobalUsage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when {
                        budgetSummary.usagePercent > 1.0 -> ExcelRed
                        budgetSummary.usagePercent >= 0.80 -> LocalFinanceColors.current.warning
                        else -> ExcelGreen
                    },
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Uso global: ${FormatUtils.formatPercentage(animatedGlobalPercent / 100.0)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (budgetSummary.overCount > 0) {
                        Text(
                            "${budgetSummary.overCount} sobrepasada(s)",
                            style = MaterialTheme.typography.bodySmall.copy(color = ExcelRed, fontWeight = FontWeight.Bold)
                        )
                    }
                    if (budgetSummary.nearCount > 0) {
                        Text(
                            "${budgetSummary.nearCount} cerca del límite",
                            style = MaterialTheme.typography.bodySmall.copy(color = LocalFinanceColors.current.warning, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                // Recomendaciones simples.
                if (recommendations.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Recomendaciones", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    recommendations.forEach { rec -> RecommendationRow(rec) }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- BUDGETS PROGRESS CARDS LIST ---
        if (budgetReports.isEmpty()) {
            EmptyState(
                modifier = Modifier.weight(1f),
                message = "Configura categorías en la pestaña de Ajustes para ver presupuestos.",
                icon = Icons.Default.TrackChanges
            )
        } else {
            LazyColumn(
                state = budgetsListState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .testTag("budgets_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(budgetReports, key = { it.categoryName }) { item ->
                    val usage = item.usagePercentage
                    // Determine warning colors based on specifications
                    val statusColor = when {
                        usage <= 0.80 -> ExcelGreen                    // Under Budget: Green
                        (usage > 0.80) && (usage <= 1.0) -> LocalFinanceColors.current.warning // Near Budget Limit
                        else -> ExcelRed                             // Over Budget: Red
                    }

                    FinanceCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .testTag("budget_item_${item.categoryName}"),
                        contentPadding = PaddingValues(14.dp),
                        onClick = { showEditBudgetDialog = item }
                    ) {
                        Column {
                            // Row 1: Title and Category info
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Bullet indicator with statusColor
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(statusColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        item.categoryName,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                val statusLabel = when {
                                    usage > 1.0 -> "Sobrepasado"
                                    usage >= 0.80 -> "Cerca del límite"
                                    else -> "Bajo control"
                                }
                                val animatedItemPercent by animateIntAsState(
                                    targetValue = (usage * 100).roundToInt(),
                                    animationSpec = Motion.long(reduced),
                                    label = "itemUsagePercent",
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Uso: ${FormatUtils.formatPercentage(animatedItemPercent / 100.0)}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = statusColor,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.End
                                    )
                                    Text(
                                        text = statusLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = statusColor,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Row 2: Progress Indicator bar
                            val animatedUsage by animateFloatAsState(
                                targetValue = usage.coerceAtMost(1.0).toFloat(),
                                animationSpec = Motion.long(reduced),
                                label = "budgetUsage",
                            )
                            LinearProgressIndicator(
                                progress = { animatedUsage },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = statusColor,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Row 3: Totals comparison
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Presupuestado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    PrivacyAmountText(
                                        amount = FormatUtils.formatCLP(item.budgetedAmount),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    )
                                }

                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (isAvgMode) "Gasto Promedio" else "Gastado Real", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    PrivacyAmountText(
                                        amount = FormatUtils.formatCLP(item.spentAmount),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = if (usage > 1.0) ExcelRed else MaterialTheme.colorScheme.onSurface),
                                    )
                                }

                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                    Text("Diferencia", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val isUnder = item.difference >= 0
                                    PrivacyAmountText(
                                        amount = (if (isUnder) "+" else "") + FormatUtils.formatCLP(item.difference),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isUnder) ExcelGreen else ExcelRed
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal dialogue to Edit/Save budget limit
    showEditBudgetDialog?.let { model ->
        var budgetInput by remember {
            mutableStateOf(
                if (model.budgetedAmount % 1.0 == 0.0) {
                    model.budgetedAmount.toInt().toString()
                } else {
                    model.budgetedAmount.toString()
                }
            )
        }
        var errorMsg by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showEditBudgetDialog = null },
            title = {
                Text("🎯 Presupuesto: ${model.categoryName}", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Ingresa el límite presupuestado para esta categoría para el mes de ${monthNames[selectedMonth - 1]} $selectedYear:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (errorMsg.isNotEmpty()) {
                        Text(errorMsg, color = ExcelRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    OutlinedTextField(
                        value = budgetInput,
                        onValueChange = { budgetInput = it },
                        modifier = Modifier.fillMaxWidth().testTag("budget_input_field"),
                        label = { Text("Límite Presupuestado (CLP)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = budgetInput.toDoubleOrNull()
                        if (amount == null || amount < 0.0) {
                            errorMsg = "Por favor ingresa un número positivo."
                            return@Button
                        }
                        viewModel.saveBudget(
                            categoryName = model.categoryName,
                            month = selectedMonth,
                            year = selectedYear,
                            amount = amount,
                            existingId = model.budgetId
                        )
                        showEditBudgetDialog = null
                    },
                    modifier = Modifier.testTag("budget_dialog_save")
                ) {
                    Text("Guardar Límite", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditBudgetDialog = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/** Fila de recomendación de presupuesto. El monto se enmascara con la privacidad global. */
@Composable
private fun RecommendationRow(rec: BudgetRecommendation) {
    val (icon, color, label, amount) = when (rec) {
        is BudgetRecommendation.Over -> RecParts(
            Icons.Default.Warning, ExcelRed,
            "Reducir gasto en ${rec.category}",
            "Excede ${FormatUtils.formatCLP(rec.overBy)}"
        )
        is BudgetRecommendation.Near -> RecParts(
            Icons.Default.Info, LocalFinanceColors.current.warning,
            "${rec.category} cerca del límite",
            "Quedan ${FormatUtils.formatCLP(rec.remaining)}"
        )
        is BudgetRecommendation.Available -> RecParts(
            Icons.Default.CheckCircle, ExcelGreen,
            "Disponible en ${rec.category}",
            FormatUtils.formatCLP(rec.remaining)
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        PrivacyAmountText(
            amount = amount,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = color),
            color = color,
        )
    }
}

/** Tupla auxiliar para descomponer las partes visuales de una recomendación. */
private data class RecParts(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val label: String,
    val amount: String,
)
