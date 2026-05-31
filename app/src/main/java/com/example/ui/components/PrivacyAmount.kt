package com.example.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * Estado global de privacidad: cuando es `true`, los montos sensibles se enmascaran en la UI.
 *
 * Se provee en la raíz de la app (MainAppShell) a partir de la preferencia persistida.
 * La lógica financiera sigue calculando con los valores reales; esto solo afecta la presentación.
 */
val LocalAmountsHidden = compositionLocalOf { false }

/** Texto usado para enmascarar un monto sensible cuando la privacidad está activada. */
const val MASK = "••••••"

/** Devuelve el texto original o la máscara según el estado de privacidad recibido. */
fun maskAmount(value: String, hidden: Boolean): String = if (hidden) MASK else value

/**
 * Texto de monto sensible reutilizable. Si la privacidad global está activa, muestra [MASK]
 * en lugar del valor real. Mantiene una sola línea por defecto para no romper el layout.
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
    Text(
        text = if (hidden) MASK else (prefix + amount),
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = overflow,
        textAlign = textAlign,
    )
}
