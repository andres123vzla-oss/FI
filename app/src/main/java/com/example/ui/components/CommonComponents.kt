package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.IconMapper
import com.example.ui.theme.LocalFinanceColors
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.Motion

/**
 * Microinteracción de "presionado": escala sutilmente el componente mientras está presionado
 * y vuelve a 1f al soltar. Respeta "reducir movimiento" ([LocalReducedMotion]) saltando al
 * estado final. No reemplaza el ripple; debe combinarse con un [interactionSource] compartido
 * para que la escala reaccione a los mismos toques que el ripple.
 *
 * @param enabled si es false, no hay escala (siempre 1f).
 * @param target escala objetivo al presionar (Cards 0.98f, FAB 0.96f).
 * @param interactionSource fuente de interacción a observar (la misma del clickable/Surface).
 */
fun Modifier.pressScale(
    enabled: Boolean = true,
    target: Float = 0.98f,
    interactionSource: MutableInteractionSource,
): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed && !reduced) target else 1f,
        animationSpec = Motion.fast(reduced),
        label = "pressScale",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Tarjeta base del sistema "dark premium": superficie de tarjeta + hairline de 1dp, sin sombra.
 * En modo oscuro la jerarquía se logra con color de superficie y borde, no con elevación.
 * Acepta un [onClick] opcional para filas/tarjetas pulsables (preserva el área de toque).
 */
@Composable
fun FinanceCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val color = MaterialTheme.colorScheme.surfaceContainer
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            modifier = modifier.pressScale(target = 0.98f, interactionSource = interactionSource),
            color = color,
            shape = shape,
            border = border,
            interactionSource = interactionSource,
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(
            modifier = modifier,
            color = color,
            shape = shape,
            border = border,
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

/**
 * Cápsula de estado (pill) unificada: texto sobre un fondo tintado al 14% del color semántico,
 * con esquinas totalmente redondeadas. Reemplaza las cápsulas ad-hoc dispersas por la UI.
 */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            color = color,
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * Tarjeta KPI compacta. El monto llega ya formateado en compacto (p. ej. `CLP 1,09M`) y se
 * renderiza en una sola línea para que nunca se parta en dos. Hairline sutil + ícono con
 * fondo tintado al 14% del color semántico.
 */
@Composable
fun KpiCard(
    modifier: Modifier = Modifier,
    title: String,
    amount: String,
    subtitle: String? = null,
    icon: ImageVector,
    color: Color,
    testTag: String,
) {
    val finance = LocalFinanceColors.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            PrivacyAmountText(
                amount = amount,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val tone = when {
                    subtitle.contains("+") || subtitle.contains("ganancia") -> finance.success
                    subtitle.contains("-") || subtitle.contains("pérdida") -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                StatusPill(text = subtitle, color = tone)
            }
        }
    }
}

@Composable
fun CategoryChip(
    categoryName: String,
    colorHex: String,
    iconName: String,
    modifier: Modifier = Modifier
) {
    val categoryColor = try {
        Color(colorHex.toColorInt())
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(categoryColor.copy(alpha = 0.14f))
            .border(width = 1.dp, color = categoryColor.copy(alpha = 0.3f), shape = RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = IconMapper.mapToIcon(iconName),
            contentDescription = null,
            tint = categoryColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = categoryName,
            style = MaterialTheme.typography.labelSmall.copy(
                color = categoryColor,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    message: String,
    icon: ImageVector
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal
            ),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Estado de error con acción de recuperación. Color semántico acompañado de ícono y texto
 * (el color nunca es el único portador de significado).
 */
@Composable
fun ErrorState(
    modifier: Modifier = Modifier,
    message: String,
    icon: ImageVector,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Reintentar",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Normal
            ),
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry, shape = MaterialTheme.shapes.small) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(retryLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Barra superior del sistema. Por defecto es casi-negra (fondo de pantalla) con un acento
 * vertical y título en texto principal, separada del contenido por un hairline. Las [actions]
 * se alinean a la derecha.
 */
@Composable
fun MainTopBar(
    title: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.background,
    elevated: Boolean = false,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    Surface(
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = 22.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (actions != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        actions()
                    }
                }
            }
            // El hairline inferior se refuerza al hacer scroll (separa la barra del contenido).
            val reduced = LocalReducedMotion.current
            val base = MaterialTheme.colorScheme.outlineVariant
            val dividerColor by animateColorAsState(
                targetValue = base.copy(alpha = if (elevated) 1f else 0.4f),
                animationSpec = Motion.medium(reduced),
                label = "topBarHairline",
            )
            HorizontalDivider(color = dividerColor)
        }
    }
}
