package com.example.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.Motion

/**
 * Estado global de privacidad: cuando es `true`, los montos sensibles se enmascaran en la UI.
 *
 * Arranca en `true` (oculto por defecto) según el handoff: ningún monto se revela hasta que el
 * usuario toca el ojo. El valor efectivo se provee en la raíz (AppRoot) desde la preferencia
 * persistida. La lógica financiera sigue calculando con los valores reales; esto solo afecta
 * la presentación.
 */
val LocalAmountsHidden = compositionLocalOf { true }

/**
 * Texto fijo para enmascarar un monto sensible. Es un literal de longitud constante: no refleja
 * la magnitud real del valor, para no filtrar información.
 */
const val MASK = "•••••"

/** Devuelve el texto original o la máscara según el estado de privacidad recibido. */
fun maskAmount(value: String, hidden: Boolean): String = if (hidden) MASK else value

/**
 * Texto de monto sensible reutilizable. Si la privacidad global está activa, muestra [MASK]
 * en lugar del valor real. Usa números tabulares ("tnum") para que las cifras se alineen y no
 * "salten" al cambiar de valor, y se mantiene en una sola línea por defecto para no romper el
 * layout (los montos nunca se parten en dos líneas).
 */
@Composable
fun PrivacyAmountText(
    amount: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    softWrap: Boolean = false,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    textAlign: TextAlign? = null,
    prefix: String = "",
) {
    val hidden = LocalAmountsHidden.current
    val reduced = LocalReducedMotion.current

    // Text reutilizable para ambos estados del Crossfade: MISMO style/maxLines/softWrap/tnum,
    // de modo que el layout sea idéntico oculto o visible (sin reflow ni cambio de tamaño).
    val content: @Composable (Boolean) -> Unit = { isHidden ->
        Text(
            text = if (isHidden) MASK else (prefix + amount),
            // Números tabulares: columnas de cifras alineadas y estables.
            modifier = modifier.semantics {
                // El contentDescription "Monto oculto" se mantiene durante toda la transición
                // del estado oculto, para no filtrar el valor real a TalkBack.
                if (isHidden) contentDescription = "Monto oculto"
            },
            style = style.copy(fontFeatureSettings = "tnum"),
            color = color,
            maxLines = maxLines,
            softWrap = softWrap,
            overflow = overflow,
            textAlign = textAlign,
        )
    }

    if (reduced) {
        // Reduce movimiento: sin Crossfade, snap directo al estado actual.
        content(hidden)
    } else {
        Crossfade(
            targetState = hidden,
            // MITIGACIÓN B1: solo se anima oculto→visible. Al pasar a oculto (target = true)
            // el cambio es instantáneo (snap) para que el valor desaparezca sin diluirse.
            animationSpec = if (hidden) snap() else Motion.fast(),
            label = "reveal",
        ) { isHidden ->
            content(isHidden)
        }
    }
}

/**
 * Botón de ojo para alternar la visibilidad global de los montos. Hit target de 48dp,
 * [contentDescription] dinámico y [stateDescription] para accesibilidad (TalkBack).
 *
 * @param hidden estado actual (true = montos ocultos).
 * @param onToggle invoca el cambio de estado.
 * @param tint color del ícono (por defecto, el acento del tema sobre barras oscuras).
 */
@Composable
fun AmountVisibilityToggle(
    hidden: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier
            .semantics {
                contentDescription = if (hidden) "Mostrar montos" else "Ocultar montos"
                stateDescription = if (hidden) "Montos ocultos" else "Montos visibles"
            },
    ) {
        // Crossfade entre los dos íconos (NO AnimatedContent). La semántica vive en el
        // IconButton padre; cada Icon expone contentDescription = null para no duplicarla.
        val reduced = LocalReducedMotion.current
        Crossfade(
            targetState = hidden,
            animationSpec = if (reduced) snap() else Motion.fast(),
            label = "toggleIcon",
        ) { isHidden ->
            Icon(
                imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = null,
                tint = tint,
            )
        }
    }
}
