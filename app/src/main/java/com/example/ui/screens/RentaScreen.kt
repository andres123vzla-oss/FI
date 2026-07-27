package com.example.ui.screens

import android.content.ClipData
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.security.SecurityViewModel
import com.example.ui.components.AmountVisibilityToggle
import com.example.ui.components.EmptyState
import com.example.ui.components.FinanceCard
import com.example.ui.components.MainTopBar
import com.example.ui.components.PrivacyAmountText
import com.example.ui.components.pressScale
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Acento decorativo del Art. 107 (lila). Igual que [com.example.ui.screens.HeroPercentChip] usa
 * tonos fijos para el degradado, este color vive solo como tinte de icono dentro de un contenedor;
 * no se usa como color de texto, así que no necesita cumplir contraste AA de texto.
 */
private val Art107Purple = Color(0xFFC792EA)

/**
 * Pantalla "Renta" (Operación Renta / Conciliación SII) del proyecto "Rinde".
 *
 * Recrea la sección RENTA del handoff de diseño con los tokens existentes (dark-first, degradado
 * azul→cyan, semánticos verde/rojo/ámbar) y los componentes del sistema (MainTopBar, FinanceCard,
 * PrivacyAmountText). Cablea TODO al dominio vía [RentaPresenter] (motor + disclosure + F22).
 *
 * Cumplimiento legal (ver docs/RINDE_CUMPLIMIENTO_SII.md):
 *  - Art. 107 y dividendos del exterior (Art. 41 A) se muestran en modo "requiere contador" SIN
 *    cifra de impuesto (decisión de [RentaDisclosure], no de esta UI).
 *  - El sello "Borrador referencial — no se presenta al SII" encabeza toda cifra tributaria.
 *  - El botón final es "Copiar valores para ingresar en sii.cl" (jamás "Presentar"/"Declarar").
 *  - Nunca se pide la Clave Tributaria (el footer lo reafirma explícitamente).
 *
 * Sigue el estilo de [PortafolioScreen]: Scaffold + MainTopBar con el toggle de privacidad, fondo
 * del tema y contenido en scroll vertical.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentaScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier,
    securityViewModel: SecurityViewModel = viewModel(),
) {
    val investments by viewModel.allInvestments.collectAsState()
    val hideAmounts by securityViewModel.hideAmounts.collectAsState()
    val scrollState = rememberScrollState()

    // Año tributario actual (la app abre en blanco: sin ventas/dividendos registrados, el motor
    // devuelve un resumen vacío y la pantalla muestra su estado vacío amable). Las posiciones de la
    // cartera se mapean a lotes de compra para alimentar el FIFO en cuanto existan ventas.
    val taxYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    // C4: el mapeo cartera→lotes vive en el presentador (testeable en JVM), no en la UI.
    val buys = remember(investments) { RentaPresenter.fromInvestments(investments) }
    val state = remember(buys, taxYear) { RentaPresenter.build(taxYear = taxYear, buys = buys) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MainTopBar(
                title = "Renta",
                elevated = scrollState.canScrollBackward,
                actions = {
                    AmountVisibilityToggle(
                        hidden = hideAmounts,
                        onToggle = { securityViewModel.toggleHideAmounts() },
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("toggle_hide_amounts_renta"),
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .testTag("renta_screen"),
        ) {
            Text(
                text = state.overline,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 10.dp),
            )

            SelloBanner(headline = state.sealHeadline, detail = state.sealDetail)

            Spacer(Modifier.height(14.dp))
            ResumenInversionesCard(state)

            Spacer(Modifier.height(12.dp))
            OrganizeCard(
                card = state.art107,
                icon = Icons.Filled.BarChart,
                iconTint = Art107Purple,
                iconBg = Art107Purple.copy(alpha = 0.15f),
            )
            Spacer(Modifier.height(12.dp))
            OrganizeCard(
                card = state.art41a,
                icon = Icons.Filled.Public,
                iconTint = LocalFinanceColors.current.accentCyan,
                iconBg = LocalFinanceColors.current.accentCyan.copy(alpha = 0.13f),
            )

            Spacer(Modifier.height(18.dp))
            ConciliacionCard(state)

            CopyValuesButton(state)

            ClaveTributariaFooter()

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Sello obligatorio: encabeza toda cifra tributaria. Ámbar (warning) con disclaimer versionado. */
@Composable
private fun SelloBanner(headline: String, detail: String) {
    val warning = LocalFinanceColors.current.warning
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(warning.copy(alpha = 0.10f))
            .border(1.dp, warning.copy(alpha = 0.28f), RoundedCornerShape(13.dp))
            .padding(13.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = warning,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = headline,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 16.sp,
                ),
                color = warning,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 15.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Bloque "Resumen de inversiones": 3 métricas (ventas / dividendos / respaldos). */
@Composable
private fun ResumenInversionesCard(state: RentaUiState) {
    FinanceCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(18.dp),
    ) {
        Text(
            text = "Resumen de inversiones",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            state.tiles.forEach { tile -> StatTile(tile) }
        }
        state.emptyHint?.let { hint ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Una métrica del resumen, sobre fondo `surfaceVariant`. El monto respeta el modo privacidad. */
@Composable
private fun RowScope.StatTile(tile: RentaTile) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(13.dp),
    ) {
        Text(
            text = tile.label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        val valueStyle = MaterialTheme.typography.titleMedium.copy(
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        if (tile.isAmount) {
            PrivacyAmountText(
                amount = tile.value,
                style = valueStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(text = tile.value, style = valueStyle, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Tarjeta "requiere contador" (Art. 107 / Art. 41 A): icono + texto + chip ámbar. NUNCA una cifra. */
@Composable
private fun OrganizeCard(
    card: RentaOrganizeCard,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
) {
    FinanceCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = card.message,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        RequiereContadorChip(card.chipLabel)
    }
}

/** Chip ámbar "Requiere contador" con punto de estado. */
@Composable
private fun RequiereContadorChip(label: String) {
    val warning = LocalFinanceColors.current.warning
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(warning.copy(alpha = 0.13f))
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(warning),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = warning,
        )
    }
}

/** Conciliación SII: propuesta del SII vs. nuestros registros; diferencias en rojo. */
@Composable
private fun ConciliacionCard(state: RentaUiState) {
    val finance = LocalFinanceColors.current
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "reconPulse")
    val animatedAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "reconPulseAlpha",
    )
    val dotAlpha = if (reduced) 1f else animatedAlpha

    FinanceCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha)),
            )
            Text(
                text = "Conciliación SII",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Lo que el SII propuso vs. tus registros. Las diferencias se marcan en rojo.",
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))

        if (state.reconRows.isEmpty()) {
            state.reconEmptyMessage?.let { msg ->
                // V3: estado vacío unificado con el componente del sistema (antes texto plano).
                EmptyState(message = msg, icon = Icons.Filled.CompareArrows)
            }
        } else {
            ReconHeaderRow()
            state.reconRows.forEach { row -> ReconRow(row, finance.success, finance.negative) }
            state.diffWarning?.let { warning ->
                Spacer(Modifier.height(14.dp))
                DiffWarningBanner(warning, finance.negative)
            }
        }
    }
}

@Composable
private fun ReconHeaderRow() {
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
    )
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.padding(bottom = 8.dp)) {
        Text("CONCEPTO", style = labelStyle, color = color, modifier = Modifier.weight(1.6f))
        Text(
            "SII PROPUSO",
            style = labelStyle,
            color = color,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Text(
            "TUS REGISTROS",
            style = labelStyle,
            color = color,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReconRow(row: RentaReconRow, successColor: Color, negativeColor: Color) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier = Modifier.padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1.6f)) {
            Text(
                text = row.concept,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = row.diffLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (row.matches) successColor else negativeColor,
            )
        }
        PrivacyAmountText(
            amount = row.siiText,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        PrivacyAmountText(
            amount = row.mineText,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = if (row.matches) MaterialTheme.colorScheme.onSurface else negativeColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DiffWarningBanner(message: String, negativeColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(negativeColor.copy(alpha = 0.10f))
            .border(1.dp, negativeColor.copy(alpha = 0.25f), RoundedCornerShape(13.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = negativeColor,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = 16.sp,
            ),
            color = negativeColor,
        )
    }
}

/**
 * Botón final: copia el CSV borrador del F22 al portapapeles. Etiqueta de [SiiPolicy] — JAMÁS
 * "Presentar"/"Declarar". Degradado azul→cyan con contenido blanco (mismo patrón que la hero del
 * Portafolio).
 */
@Composable
private fun CopyValuesButton(state: RentaUiState) {
    // C3: LocalClipboardManager quedó deprecado; LocalClipboard expone una API suspend.
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(AccentBlue, AccentCyan)))
            .pressScale(target = 0.98f, interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null) {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("F22", state.copyPayload)))
                }
                // V1: confirmación táctil del copiado (además del "Copiado ✓" visual).
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                copied = true
            }
            .heightIn(min = 54.dp)
            .testTag("renta_copy_button"),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(19.dp),
            )
            Text(
                text = if (copied) "Copiado ✓" else state.copyLabel,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Footer de cierre: reafirma que Rinde nunca pide la Clave Tributaria; el usuario declara en sii.cl. */
@Composable
private fun ClaveTributariaFooter() {
    val annotated = buildAnnotatedString {
        append("Nunca pedimos tu Clave Tributaria.\nTú ingresas y declaras en ")
        withStyle(SpanStyle(color = AccentBlue, fontWeight = FontWeight.SemiBold)) {
            append("sii.cl")
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 4.dp),
    )
}
