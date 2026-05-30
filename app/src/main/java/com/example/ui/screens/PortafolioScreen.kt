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
import com.example.data.entity.InvestmentEntity
import com.example.ui.components.EmptyState
import com.example.ui.components.MainTopBar
import com.example.ui.theme.ExcelGreen
import com.example.ui.theme.ExcelMediumBlue
import com.example.ui.theme.ExcelRed
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortafolioScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val summary by viewModel.investmentSummaryFlow.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedStockToEdit by remember { mutableStateOf<InvestmentEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<InvestmentEntity?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = ExcelMediumBlue,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_stock")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar Inversión")
            }
        },
        topBar = {
            MainTopBar(
                title = "📈 Portafolio de Inversión",
                containerColor = ExcelMediumBlue
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // --- PORTFOLIO HERO SUMMARY CARD ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ExcelMediumBlue)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "VALOR ACTUAL DEL PORTAFOLIO",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                    )

                    Text(
                        FormatUtils.formatUSD(summary.totalCurrent),
                        style = MaterialTheme.typography.headlineLarge.copy(color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                    )

                    Divider(color = Color.White.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Invertido", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                            Text(
                                FormatUtils.formatUSD(summary.totalInvested),
                                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Rendimiento Neto", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                            val gl = summary.totalGainLoss
                            val pct = summary.totalGainLossPercent
                            val sign = if (gl >= 0) "+" else ""
                            val labelColor = if (gl >= 0) Color(0xFFC8E6C9) else Color(0xFFFFCDD2)

                            Text(
                                text = "$sign${FormatUtils.formatUSD(gl)} (${FormatUtils.formatPercentage2Signed(pct)})",
                                style = MaterialTheme.typography.titleMedium.copy(color = labelColor, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // --- STOCKS LIST VIEW ---
            Text(
                "Mis Acciones y ETFs",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
            )

            if (summary.stocks.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f),
                    message = "No tienes activos registrados. Presiona '+' para agregar.",
                    icon = Icons.Default.ShowChart
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .testTag("stocks_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(summary.stocks, key = { it.id }) { stock ->
                        val investedValue = stock.quantity * stock.purchasePrice
                        val currentValue = stock.quantity * stock.currentPrice
                        val gainLoss = currentValue - investedValue
                        val yield = if (investedValue > 0) gainLoss / investedValue else 0.0

                        val isPositive = gainLoss >= 0
                        val valueColor = if (isPositive) ExcelGreen else ExcelRed
                        val sign = if (isPositive) "+" else ""

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedStockToEdit = stock }
                                .testTag("stock_item_${stock.ticker}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Row 1: Ticker, Name, and Quick Edit price values
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
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(ExcelMediumBlue.copy(alpha = 0.1f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                stock.ticker,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = ExcelMediumBlue
                                                )
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            stock.companyName,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    IconButton(
                                        onClick = { showDeleteConfirmDialog = stock },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Borrar",
                                            tint = ExcelRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Row 2: Matrix Grid formulas
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Participación", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        Text(
                                            "Cant: ${stock.quantity}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                        )
                                        Text(
                                            "Comp: ${FormatUtils.formatUSD(stock.purchasePrice)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Precio Act.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        Text(
                                            FormatUtils.formatUSD(stock.currentPrice),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            "Val: ${FormatUtils.formatUSD(currentValue)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.LightGray
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Gan / Pérd", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        Text(
                                            text = "$sign${FormatUtils.formatUSD(gainLoss)}",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = valueColor
                                            )
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(valueColor.copy(alpha = 0.1f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "$sign${FormatUtils.formatPercentage(yield)}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = valueColor
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
        }
    }

    // Modal dialogue to ADD Stock
    if (showAddDialog) {
        AddStockDialog(
            onDismiss = { showAddDialog = false },
            onSave = { ticker, name, qty, buyPrice, currentPrice ->
                viewModel.addStock(ticker, name, qty, buyPrice, currentPrice)
                showAddDialog = false
            }
        )
    }

    // Modal dialogue to EDIT Stock
    selectedStockToEdit?.let { stock ->
        var editQty by remember { mutableStateOf(stock.quantity.toString()) }
        var editBuyPrice by remember { mutableStateOf(stock.purchasePrice.toString()) }
        var editCurrentPrice by remember { mutableStateOf(stock.currentPrice.toString()) }

        var errorMsg by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { selectedStockToEdit = null },
            title = {
                Text("📈 Editar Stock: ${stock.ticker}", fontWeight = FontWeight.Bold, color = ExcelMediumBlue)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Actualiza los valores correspondientes para el activo '${stock.companyName}':",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    if (errorMsg.isNotEmpty()) {
                        Text(errorMsg, color = ExcelRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    OutlinedTextField(
                        value = editQty,
                        onValueChange = { editQty = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Cantidad") },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_qty_field")
                    )

                    OutlinedTextField(
                        value = editBuyPrice,
                        onValueChange = { editBuyPrice = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Precio Compra (USD)") },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editCurrentPrice,
                        onValueChange = { editCurrentPrice = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Precio Actual (USD)") },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_curr_price_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = editQty.toDoubleOrNull()
                        val buyPrice = editBuyPrice.toDoubleOrNull()
                        val currentPrice = editCurrentPrice.toDoubleOrNull()

                        if (qty == null || qty <= 0.0) {
                            errorMsg = "Cantidad inválida."
                            return@Button
                        }
                        if (buyPrice == null || buyPrice <= 0.0) {
                            errorMsg = "Precio compra inválido."
                            return@Button
                        }
                        if (currentPrice == null || currentPrice <= 0.0) {
                            errorMsg = "Precio actual inválido."
                            return@Button
                        }

                        viewModel.updateStock(
                            stock.copy(
                                quantity = qty,
                                purchasePrice = buyPrice,
                                currentPrice = currentPrice,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        selectedStockToEdit = null
                    },
                    modifier = Modifier.testTag("submit_edit_stock")
                ) {
                    Text("Confirmar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedStockToEdit = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Confirmation delete dialog
    showDeleteConfirmDialog?.let { currentDeletingStock ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("🗑️ Confirmar Retiro", fontWeight = FontWeight.Bold) },
            text = { Text("¿Deseas de verdad eliminar la acción ${currentDeletingStock.ticker} (${currentDeletingStock.companyName}) por completo?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStock(currentDeletingStock.id)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExcelRed)
                ) {
                    Text("Retirar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
