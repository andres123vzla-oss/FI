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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.security.SecurityViewModel
import com.example.ui.components.AmountVisibilityToggle
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState
import com.example.ui.components.FinanceCard
import com.example.ui.components.LocalAmountsHidden
import com.example.ui.components.MainTopBar
import com.example.ui.components.CountUpAmountText
import com.example.ui.components.PrivacyAmountText
import com.example.ui.components.maskAmount
import com.example.ui.components.pressScale
import com.example.ui.theme.Motion
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.LocalFinanceColors
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.example.ui.viewmodel.FinanceViewModel
import com.example.ui.viewmodel.PriceUpdateState
import com.example.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Intervalo del refresco de precios en vivo (foreground). Acorde al rate limit del tier gratis. */
private const val AUTO_REFRESH_INTERVAL_MS = 60_000L

// UX2-02: colores de rendimiento de la hero card. Medidos sobre el degradado azul→cyan con scrim,
// estos tonos NO alcanzan AA (>4.5:1) como color de texto, por lo que SOLO se usan en el icono
// ▲/▼ dentro de un chip con fondo sólido oscuro (negro alpha 0.45). Los montos y porcentajes en
// la hero van en blanco puro, que sí pasa AA sobre el scrim uniforme de negro alpha 0.55.
private val HeroPositive = Color(0xFF6EE7B7)
private val HeroNegative = Color(0xFFFCA5A5)

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

    // UX2-01: colores semánticos del tema (claro/oscuro) en lugar de los Excel* fijos.
    val finance = LocalFinanceColors.current

    // UX2-09: estado de diálogos en rememberSaveable para sobrevivir muerte de proceso/rotación.
    // Para los diálogos de edición/borrado se guarda solo el ID (Int, saveable) y se re-resuelve
    // la entidad desde el flow de inversiones; así no hay que serializar la entidad completa.
    var showAddDialog by rememberSaveable { mutableStateOf(value = false) }
    var selectedStockToEditId by rememberSaveable { mutableStateOf<Int?>(null) }
    var deleteConfirmStockId by rememberSaveable { mutableStateOf<Int?>(null) }
    val selectedStockToEdit = selectedStockToEditId?.let { id -> rawStocks.firstOrNull { it.id == id } }
    val showDeleteConfirmDialog = deleteConfirmStockId?.let { id -> rawStocks.firstOrNull { it.id == id } }

    val stocksListState = rememberLazyListState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            val fabInteraction = remember { MutableInteractionSource() }
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary, // C5: token, no blanco fijo

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
                // Brillo radial decorativo (estático, sutil) en la esquina superior. Se reduce su
                // alpha para no contrarrestar el scrim oscuro de contraste (UX-02).
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
                // UX2-02: scrim negro UNIFORME (alpha 0.55) sobre TODO el degradado. El gradiente
                // anterior (0.25→0.40) dejaba ratios medidos de 1.65–3.7:1 en el lado claro
                // (AccentCyan); con 0.55 uniforme el texto blanco (alpha ≥ 0.85) sí alcanza AA.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                )
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "VALOR ACTUAL DEL PORTAFOLIO",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold)
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
                            Text("Total Invertido", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
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
                            Text("Rendimiento Neto", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                            val gl = metrics.totalGainLoss
                            val pct = metrics.totalYieldPercent
                            val sign = if (gl >= 0) "+" else ""

                            // UX2-02: el monto va en blanco puro (HeroPositive/HeroNegative no
                            // pasan AA como color de texto sobre el scrim). El signo semántico
                            // viaja en el chip de porcentaje con fondo sólido oscuro.
                            // Monto enmascarable; el porcentaje siempre visible (no revela monto exacto).
                            PrivacyAmountText(
                                amount = "$sign${FormatUtils.formatUSD(gl)}",
                                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            HeroPercentChip(
                                percentText = FormatUtils.formatPercentage2Signed(pct),
                                positive = gl >= 0,
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
                            // UX2-02: ticker en blanco puro y porcentaje en chip de fondo sólido
                            // oscuro (los colores Hero* solo van en el icono ▲/▼ del chip).
                            metrics.best?.let { best ->
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Mejor activo", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            best.ticker,
                                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        HeroPercentChip(
                                            percentText = FormatUtils.formatPercentage2Signed(best.yieldPercent),
                                            positive = true,
                                        )
                                    }
                                }
                            }
                            metrics.worst?.let { worst ->
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Peor activo", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            worst.ticker,
                                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        HeroPercentChip(
                                            percentText = FormatUtils.formatPercentage2Signed(worst.yieldPercent),
                                            positive = false,
                                        )
                                    }
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
                            // C5: par de tokens primary/onPrimary (contraste garantizado por tema).
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.testTag("refresh_prices_button")
                        ) {
                            if (priceState is PriceUpdateState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary, // C5
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
                            val amber = finance.warning
                            val showRetrying = autoRefreshEnabled && liveError != null
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        when {
                                            !autoRefreshEnabled -> MaterialTheme.colorScheme.outline
                                            showRetrying -> amber
                                            else -> finance.success
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
                        is PriceUpdateState.Success -> state.message to finance.success
                        is PriceUpdateState.Error -> state.message to finance.negative
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

            // UX2-07: ventana de asentamiento (mismo patrón "settled" de DashboardScreen). Los
            // flows de Room/SQLCipher arrancan vacíos antes de resolver; sin esta ventana se ve
            // por un frame el estado vacío aunque el usuario sí tenga activos.
            var settled by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(300)
                settled = true
            }

            if (metrics.stocks.isEmpty()) {
                // UX-08: diferencia "vacío" (sin activos) de "error" (falló la carga de precios).
                // Si la lista está vacía por un fallo de red/API, se muestra ErrorState con acción
                // de reintento en vez del estado vacío genérico. El ErrorState tiene PRIORIDAD
                // sobre la ventana de asentamiento (UX2-07) para no enmascarar errores.
                val loadErrorMessage = (priceState as? PriceUpdateState.Error)?.message ?: liveError
                if (loadErrorMessage != null) {
                    ErrorState(
                        modifier = Modifier.weight(1f),
                        message = loadErrorMessage,
                        icon = Icons.Default.CloudOff,
                        onRetry = { viewModel.refreshPrices() },
                    )
                } else if (!settled) {
                    // UX2-07: placeholder neutro mientras asienta el flow; evita el destello del
                    // estado vacío durante la carga inicial.
                    Box(modifier = Modifier.weight(1f))
                } else {
                    EmptyState(
                        modifier = Modifier.weight(1f),
                        message = "No tienes activos registrados. Presiona '+' para agregar.",
                        icon = Icons.AutoMirrored.Filled.ShowChart
                    )
                }
            } else {
                // V2: pull-to-refresh manual sobre la lista de activos. Complementa el botón
                // "Actualizar" y el auto-refresh; comparte PriceUpdateState.Loading como indicador
                // y, sin API key, el gesto muestra el mismo mensaje claro del flujo manual.
                PullToRefreshBox(
                    isRefreshing = priceState is PriceUpdateState.Loading,
                    onRefresh = { viewModel.refreshPrices() },
                    modifier = Modifier.weight(1f),
                ) {
                LazyColumn(
                    state = stocksListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                        .testTag("stocks_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(metrics.stocks, key = { it.id }) { stock ->
                        val isPositive = stock.gainLoss >= 0
                        val valueColor = if (isPositive) finance.success else finance.negative
                        val sign = if (isPositive) "+" else ""

                        FinanceCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .testTag("stock_item_${stock.ticker}"),
                            contentPadding = PaddingValues(12.dp),
                            onClick = {
                                // UX2-09: se guarda solo el id (saveable); la entidad se
                                // re-resuelve desde el flow al componer el diálogo.
                                selectedStockToEditId = stock.id
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
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                stock.ticker,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
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
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = FormatUtils.formatPercentage(stock.weight),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }

                                    // Touch target accesible de 48dp (WCAG 2.5.5): el IconButton de
                                    // M3 mide 48dp por defecto; el ícono mantiene su tamaño visual. UX-05.
                                    IconButton(
                                        onClick = {
                                            // UX2-09: id saveable; la entidad se re-resuelve del flow.
                                            deleteConfirmStockId = stock.id
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Borrar",
                                            tint = finance.negative,
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
                                        // UX2-13: la cantidad también se enmascara en modo privacidad
                                        // (cantidad × precio público reconstruiría el valor oculto).
                                        Text(
                                            "Cant: ${maskAmount(stock.quantity.toString(), LocalAmountsHidden.current)}",
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
                } // cierre de PullToRefreshBox (V2)
            }
        }
    }

    // Modal dialogue to ADD Stock
    if (showAddDialog) {
        // ARQ2-02 (defensa UI): bloquea el doble-submit lógico. La transacción de BD ya evita el
        // crash, pero un doble tap en "Invertir" antes de cerrarse el diálogo fusionaría la misma
        // posición dos veces. El botón vive en AddStockDialog (DashboardScreen.kt, fuera del
        // alcance de este fix), por lo que la guarda se aplica aquí, en el callback de confirmación.
        var isSaving by remember { mutableStateOf(false) }
        AddStockDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
        ) { ticker, name, qty, buyPrice, currentPrice ->
            if (!isSaving) {
                isSaving = true
                viewModel.addStock(ticker, name, qty, buyPrice, currentPrice)
                showAddDialog = false
            }
        }
    }

    // Modal dialogue to EDIT Stock
    selectedStockToEdit?.let { stock ->
        // UX2-09: campos del formulario en rememberSaveable, keyed por stock.id, para que lo
        // tecleado sobreviva rotación/muerte de proceso y se reinicie al cambiar de activo.
        var editQty by rememberSaveable(stock.id) { mutableStateOf(stock.quantity.toString()) }
        var editBuyPrice by rememberSaveable(stock.id) { mutableStateOf(stock.purchasePrice.toString()) }
        var editCurrentPrice by rememberSaveable(stock.id) { mutableStateOf(stock.currentPrice.toString()) }

        var errorMsg by rememberSaveable(stock.id) { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { selectedStockToEditId = null },
            title = {
                Text("Editar Stock: ${stock.ticker}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Actualiza los valores correspondientes para el activo '${stock.companyName}':",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (errorMsg.isNotEmpty()) {
                        // UX2-08: liveRegion para que los lectores de pantalla anuncien el error
                        // de validación al aparecer, sin mover el foco.
                        Text(
                            errorMsg,
                            color = finance.negative,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
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

                        // FIN2-02: además de null/<=0, se rechazan NaN/Infinity. Kotlin parsea
                        // "NaN", "Infinity" y "9e999" (overflow a Infinity) como Double válidos.
                        if ((qty == null) || !qty.isFinite() || (qty <= 0.0)) {
                            errorMsg = "Cantidad inválida."
                            return@Button
                        }
                        if ((buyPrice == null) || !buyPrice.isFinite() || (buyPrice <= 0.0)) {
                            errorMsg = "Precio compra inválido."
                            return@Button
                        }
                        if ((currentPrice == null) || !currentPrice.isFinite() || (currentPrice <= 0.0)) {
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
                        selectedStockToEditId = null
                    },
                    modifier = Modifier.testTag("submit_edit_stock")
                ) {
                    Text("Confirmar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedStockToEditId = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Confirmation delete dialog
    showDeleteConfirmDialog?.let { currentDeletingStock ->
        AlertDialog(
            onDismissRequest = { deleteConfirmStockId = null },
            title = { Text("Confirmar Retiro", fontWeight = FontWeight.Bold) },
            text = { Text("¿Deseas de verdad eliminar la acción ${currentDeletingStock.ticker} (${currentDeletingStock.companyName}) por completo?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStock(currentDeletingStock.id)
                        deleteConfirmStockId = null
                    },
                    // UX2-01: negativo semántico del tema + contenido blanco explícito.
                    colors = ButtonDefaults.buttonColors(
                        containerColor = finance.negative,
                        contentColor = Color.White,
                    )
                ) {
                    Text("Retirar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmStockId = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/**
 * UX2-02: chip de porcentaje para la hero card. El fondo sólido oscuro (negro alpha 0.45) da una
 * base estable independiente del degradado, de modo que el texto blanco del porcentaje pasa AA.
 * El color semántico (HeroPositive/HeroNegative) queda confinado al icono ▲/▼, que es redundante
 * con el signo del porcentaje y no necesita cumplir contraste de texto.
 */
@Composable
private fun HeroPercentChip(percentText: String, positive: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (positive) "▲" else "▼",
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (positive) HeroPositive else HeroNegative,
                fontWeight = FontWeight.Bold,
            )
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = percentText,
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
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
