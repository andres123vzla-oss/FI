package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.entity.InvestmentEntity
import com.example.security.SecurityViewModel
import com.example.ui.components.EmptyState
import com.example.ui.components.MainTopBar
import com.example.ui.components.PrivacyAmountText
import com.example.ui.theme.ExcelGreen
import com.example.ui.theme.ExcelMediumBlue
import com.example.ui.theme.ExcelRed
import com.example.ui.viewmodel.FinanceViewModel
import com.example.ui.viewmodel.PriceUpdateState
import com.example.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortafolioScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier,
    securityViewModel: SecurityViewModel = viewModel(),
) {
    val metrics by viewModel.portfolioMetricsFlow.collectAsState()
    val rawStocks by viewModel.allInvestments.collectAsState()
    val priceState by viewModel.priceUpdateState.collectAsState()
    val lastUpdate by viewModel.lastPriceUpdate.collectAsState()
    val hideAmounts by securityViewModel.hideAmounts.collectAsState()

    var showAddDialog by remember { mutableStateOf(value = false) }
    var selectedStockToEdit by remember { mutableStateOf<InvestmentEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<InvestmentEntity?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = ExcelMediumBlue,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_stock"),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar Inversión")
            }
        },
        topBar = {
            MainTopBar(
                title = "📈 Portafolio de Inversión",
                containerColor = ExcelMediumBlue,
                actions = {
                    IconButton(
                        onClick = { securityViewModel.toggleHideAmounts() },
                        modifier = Modifier.testTag("toggle_hide_amounts_portfolio")
                    ) {
                        Icon(
                            imageVector = if (hideAmounts) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (hideAmounts) "Mostrar montos" else "Ocultar montos",
                            tint = Color.White
                        )
                    }
                }
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

                    PrivacyAmountText(
                        amount = FormatUtils.formatUSD(metrics.totalCurrent),
                        style = MaterialTheme.typography.headlineLarge.copy(color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Total Invertido", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                            PrivacyAmountText(
                                amount = FormatUtils.formatUSD(metrics.totalInvested),
                                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Rendimiento Neto", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                            val gl = metrics.totalGainLoss
                            val pct = metrics.totalYieldPercent
                            val sign = if (gl >= 0) "+" else ""
                            val labelColor = if (gl >= 0) Color(0xFFC8E6C9) else Color(0xFFFFCDD2)

                            // Monto enmascarable; el porcentaje siempre visible (no revela monto exacto).
                            PrivacyAmountText(
                                amount = "$sign${FormatUtils.formatUSD(gl)}",
                                style = MaterialTheme.typography.titleMedium.copy(color = labelColor, fontWeight = FontWeight.Bold),
                                color = labelColor,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = FormatUtils.formatPercentage2Signed(pct),
                                style = MaterialTheme.typography.bodySmall.copy(color = labelColor, fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    // Mejor y peor activo por rendimiento (solo porcentajes, sin montos).
                    if (metrics.best != null || metrics.worst != null) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            metrics.best?.let { best ->
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Mejor activo", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                                    Text(
                                        "${best.ticker}  ${FormatUtils.formatPercentage2Signed(best.yieldPercent)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFC8E6C9), fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            metrics.worst?.let { worst ->
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Peor activo", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                                    Text(
                                        "${worst.ticker}  ${FormatUtils.formatPercentage2Signed(worst.yieldPercent)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFFFCDD2), fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- ACTUALIZACIÓN DE PRECIOS ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                viewModel.marketModeLabel,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = lastUpdate?.let { "Última actualización: ${formatTimestamp(it)}" }
                                    ?: "Sin actualizaciones registradas",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        Button(
                            onClick = { viewModel.refreshPrices() },
                            enabled = priceState !is PriceUpdateState.Loading,
                            colors = ButtonDefaults.buttonColors(containerColor = ExcelMediumBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("refresh_prices_button")
                        ) {
                            if (priceState is PriceUpdateState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("Actualizar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Mensaje del resultado de la actualización (éxito o error claro).
                    when (val state = priceState) {
                        is PriceUpdateState.Success -> StatusBanner(state.message, ExcelGreen)
                        is PriceUpdateState.Error -> StatusBanner(state.message, ExcelRed)
                        else -> {}
                    }
                }
            }

            // --- STOCKS LIST VIEW ---
            Text(
                "Mis Acciones y ETFs",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
            )

            if (metrics.stocks.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f),
                    message = "No tienes activos registrados. Presiona '+' para agregar.",
                    icon = Icons.AutoMirrored.Filled.ShowChart
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
                    items(metrics.stocks, key = { it.id }) { stock ->
                        val isPositive = stock.gainLoss >= 0
                        val valueColor = if (isPositive) ExcelGreen else ExcelRed
                        val sign = if (isPositive) "+" else ""

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedStockToEdit = rawStocks.firstOrNull { it.id == stock.id }
                                }
                                .testTag("stock_item_${stock.ticker}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Fila 1: Ticker, nombre, peso en el portafolio y borrar.
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

                                    // Peso (%) — no revela montos.
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(ExcelMediumBlue.copy(alpha = 0.08f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = FormatUtils.formatPercentage(stock.weight),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ExcelMediumBlue
                                            )
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            showDeleteConfirmDialog = rawStocks.firstOrNull { it.id == stock.id }
                                        },
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

                                // Fila 2: cantidad/compra, precio actual/valor, ganancia/rendimiento.
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Participación", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        Text(
                                            "Cant: ${stock.quantity}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        PrivacyAmountText(
                                            amount = "Comp: ${FormatUtils.formatUSD(stock.purchasePrice)}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                                            color = Color.Gray,
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Precio Act.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        PrivacyAmountText(
                                            amount = FormatUtils.formatUSD(stock.currentPrice),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        )
                                        PrivacyAmountText(
                                            amount = "Val: ${FormatUtils.formatUSD(stock.currentValue)}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                                            color = Color.Gray,
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Gan / Pérd", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        PrivacyAmountText(
                                            amount = "$sign${FormatUtils.formatUSD(stock.gainLoss)}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = valueColor,
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(valueColor.copy(alpha = 0.1f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            // El porcentaje no revela montos: siempre visible.
                                            Text(
                                                text = FormatUtils.formatPercentage2Signed(stock.yieldPercent),
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
        ) { ticker, name, qty, buyPrice, currentPrice ->
            viewModel.addStock(ticker, name, qty, buyPrice, currentPrice)
            showAddDialog = false
        }
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

                        if ((qty == null) || (qty <= 0.0)) {
                            errorMsg = "Cantidad inválida."
                            return@Button
                        }
                        if ((buyPrice == null) || (buyPrice <= 0.0)) {
                            errorMsg = "Precio compra inválido."
                            return@Button
                        }
                        if ((currentPrice == null) || (currentPrice <= 0.0)) {
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

/** Banner de estado simple para mensajes de actualización de precios. */
@Composable
private fun StatusBanner(message: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall.copy(color = color, fontWeight = FontWeight.Medium)
        )
    }
}

/** Formatea un timestamp epoch a fecha y hora local legible (sin segundos). */
private fun formatTimestamp(epochMillis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(epochMillis))
