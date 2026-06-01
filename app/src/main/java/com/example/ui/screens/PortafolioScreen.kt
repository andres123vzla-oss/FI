package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.components.AmountVisibilityToggle
import com.example.ui.components.EmptyState
import com.example.ui.components.FinanceCard
import com.example.ui.components.MainTopBar
import com.example.ui.components.CountUpAmountText
import com.example.ui.components.PrivacyAmountText
import com.example.ui.components.pressScale
import com.example.ui.theme.Motion
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.ExcelGreen
import com.example.ui.theme.ExcelMediumBlue
import com.example.ui.theme.ExcelRed
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.theme.Success
import com.example.ui.theme.Negative
import com.example.ui.viewmodel.FinanceViewModel
import com.example.ui.viewmodel.PriceUpdateState
import com.example.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Intervalo del refresco de precios en vivo (foreground). Acorde al rate limit del tier gratis. */
private const val AUTO_REFRESH_INTERVAL_MS = 60_000L

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
    val autoRefreshEnabled by viewModel.autoRefreshEnabled.collectAsState()
    val canAutoRefresh = viewModel.isAutoRefreshAvailable
    val liveError by viewModel.liveError.collectAsState()

    // --- Precios en vivo (solo en foreground) ---
    // Mientras esta pantalla está RESUMED (visible y app desbloqueada), refresca precios cada
    // intervalo. repeatOnLifecycle pausa automáticamente al ir a background o bloquearse, y se
    // cancela al salir de la pantalla. Sin fuente remota o con auto desactivado, no hace nada.
    if (canAutoRefresh && autoRefreshEnabled) {
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(AUTO_REFRESH_INTERVAL_MS)
                    viewModel.autoRefreshPrices()
                }
            }
        }
    }

    var showAddDialog by remember { mutableStateOf(value = false) }
    var selectedStockToEdit by remember { mutableStateOf<InvestmentEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<InvestmentEntity?>(null) }

    val stocksListState = rememberLazyListState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            val fabInteraction = remember { MutableInteractionSource() }
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = ExcelMediumBlue,
                contentColor = Color.White,
                interactionSource = fabInteraction,
                modifier = Modifier
                    .testTag("fab_add_stock")
                    .pressScale(target = 0.96f, interactionSource = fabInteraction),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar Inversión")
            }
        },
        topBar = {
            MainTopBar(
                title = "Portafolio de Inversión",
                elevated = stocksListState.canScrollBackward,
                actions = {
                    AmountVisibilityToggle(
                        hidden = hideAmounts,
                        onToggle = { securityViewModel.toggleHideAmounts() },
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("toggle_hide_amounts_portfolio"),
                    )
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
            // --- PORTFOLIO HERO SUMMARY CARD (degradado estático azul → cyan) ---
            val heroShape = MaterialTheme.shapes.large
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clip(heroShape)
                    .background(Brush.linearGradient(listOf(AccentBlue, AccentCyan)))
            ) {
                // Brillo radial decorativo (estático, sutil) en la esquina superior.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                                center = Offset(x = Float.POSITIVE_INFINITY, y = 0f),
                                radius = 600f,
                            )
                        )
                )
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "VALOR ACTUAL DEL PORTAFOLIO",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                    )

                    CountUpAmountText(
                        value = metrics.totalCurrent,
                        formatter = FormatUtils::formatUSD,
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
                            val labelColor = if (gl >= 0) Success else Negative

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
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Success, fontWeight = FontWeight.Bold),
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
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Negative, fontWeight = FontWeight.Bold),
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
            FinanceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { viewModel.refreshPrices() },
                            enabled = priceState !is PriceUpdateState.Loading,
                            colors = ButtonDefaults.buttonColors(containerColor = ExcelMediumBlue),
                            shape = MaterialTheme.shapes.small,
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

                    // Indicador de "en vivo" + interruptor (solo si hay fuente remota disponible).
                    if (canAutoRefresh) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val amber = LocalFinanceColors.current.warning
                            val showRetrying = autoRefreshEnabled && liveError != null
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        when {
                                            !autoRefreshEnabled -> MaterialTheme.colorScheme.outline
                                            showRetrying -> amber
                                            else -> ExcelGreen
                                        }
                                    )
                            )
                            Text(
                                text = when {
                                    !autoRefreshEnabled -> "Actualización en vivo en pausa"
                                    showRetrying -> liveError!!
                                    else -> "En vivo · cada ${AUTO_REFRESH_INTERVAL_MS / 1000} s"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (showRetrying) amber else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = autoRefreshEnabled,
                                onCheckedChange = { viewModel.setAutoRefresh(it) },
                                modifier = Modifier.testTag("auto_refresh_switch")
                            )
                        }
                    } else {
                        // Modo manual explícito: sin API key no hay precios en vivo (no se siente roto).
                        Text(
                            text = "Modo manual: edita el precio de cada activo a mano, o configura una API de mercado (MARKET_API_KEY) para precios en vivo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Mensaje del resultado de la actualización (éxito o error claro).
                    // Aparece/desaparece con un fade + expand sutil.
                    val statusBanner: Pair<String, Color>? = when (val state = priceState) {
                        is PriceUpdateState.Success -> state.message to ExcelGreen
                        is PriceUpdateState.Error -> state.message to ExcelRed
                        else -> null
                    }
                    AnimatedVisibility(
                        visible = statusBanner != null,
                        enter = fadeIn(Motion.medium()) + expandVertically(Motion.medium()),
                        exit = fadeOut(Motion.fast()) + shrinkVertically(Motion.fast()),
                    ) {
                        statusBanner?.let { (message, color) -> StatusBanner(message, color) }
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
                    state = stocksListState,
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

                        FinanceCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .testTag("stock_item_${stock.ticker}"),
                            contentPadding = PaddingValues(12.dp),
                            onClick = {
                                selectedStockToEdit = rawStocks.firstOrNull { it.id == stock.id }
                            }
                        ) {
                            Column {
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
                                        Text("Participación", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            "Cant: ${stock.quantity}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        PrivacyAmountText(
                                            amount = "Comp: ${FormatUtils.formatUSD(stock.purchasePrice)}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Precio Act.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        PrivacyAmountText(
                                            amount = FormatUtils.formatUSD(stock.currentPrice),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        )
                                        PrivacyAmountText(
                                            amount = "Val: ${FormatUtils.formatUSD(stock.currentValue)}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Gan / Pérd", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            viewModel = viewModel,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
            .clip(MaterialTheme.shapes.small)
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
