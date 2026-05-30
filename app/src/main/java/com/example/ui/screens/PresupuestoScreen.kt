package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.ui.components.EmptyState
import com.example.ui.components.MainTopBar
import com.example.ui.theme.ExcelGreen
import com.example.ui.theme.ExcelRed
import com.example.ui.viewmodel.BudgetReportItem
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.FormatUtils
import com.example.util.IconMapper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresupuestoScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val budgetReports by viewModel.budgetReportFlow.collectAsState()
    val isAvgMode by viewModel.quarterlyAverageMode.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()

    var showEditBudgetDialog by remember { mutableStateOf<BudgetReportItem?>(null) }

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
            title = "🎯 Presupuesto Mensual",
            containerColor = MaterialTheme.colorScheme.primary
        )

        // --- Option Controls ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
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
                            color = Color.Gray
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

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

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
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        // --- BUDGETS PROGRESS CARDS LIST ---
        if (budgetReports.isEmpty()) {
            EmptyState(
                modifier = Modifier.weight(1f),
                message = "Configura categorías en la pestaña de Ajustes para ver presupuestos.",
                icon = Icons.Default.TrackChanges
            )
        } else {
            LazyColumn(
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
                        usage > 0.80 && usage <= 1.0 -> Color(0xFFEF6C00) // Near Budget Limit: Orange/Yellow
                        else -> ExcelRed                             // Over Budget: Red
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showEditBudgetDialog = item }
                            .testTag("budget_item_${item.categoryName}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
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

                                Text(
                                    text = "Uso: ${FormatUtils.formatPercentage(usage)}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    textAlign = TextAlign.End
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Row 2: Progress Indicator bar
                            LinearProgressIndicator(
                                progress = usage.coerceAtMost(1.0).toFloat(),
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
                                Column {
                                    Text("Presupuestado", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    Text(
                                        FormatUtils.formatCLP(item.budgetedAmount),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (isAvgMode) "Gasto Promedio" else "Gastado Real", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    Text(
                                        FormatUtils.formatCLP(item.spentAmount),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = if (usage > 1.0) ExcelRed else MaterialTheme.colorScheme.onSurface)
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Diferencia", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    val isUnder = item.difference >= 0
                                    Text(
                                        text = (if (isUnder) "+" else "") + FormatUtils.formatCLP(item.difference),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isUnder) ExcelGreen else ExcelRed
                                        )
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
                        color = Color.Gray
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
