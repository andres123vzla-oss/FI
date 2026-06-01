package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.Motion

/**
 * Wrapper privacy-aware que anima un "count-up" (0 → valor) la PRIMERA vez que un monto se
 * revela. Pensado SOLO para los montos "hero" (balance del Dashboard, valor del Portafolio):
 * NO debe usarse en KPIs ni listas (mitigación A1: evita jank en muchos sitios a la vez).
 *
 * Mitigaciones aplicadas:
 *  - Se lee [LocalAmountsHidden] ANTES de crear el LaunchedEffect. Si está oculto, se muestra
 *    [MASK] y NO se instancia ninguna animación (B2).
 *  - Si el estado pasa a oculto a mitad de la animación, se hace snap y se cancela.
 *  - El string visible se deriva con [derivedStateOf] cacheando el formateador, de modo que
 *    solo se reformatea cuando el ENTERO formateado cambia (no por frame) — evita jank A1.
 *  - [maxLines] = 1, [softWrap] = false, números tabulares ("tnum"); nunca se anima fontSize.
 *  - Reduce movimiento → snap directo al valor final.
 *  - NaN/Infinity → no se anima; se muestra el valor saneado (0 / valor finito).
 *
 * @param value valor numérico real (sin formatear).
 * @param formatter convierte el valor redondeado a su representación (p. ej. [com.example.util.FormatUtils.formatCLP]).
 * @param prefix prefijo opcional antepuesto al texto formateado (cuando es visible).
 */
@Composable
fun CountUpAmountText(
    value: Double,
    formatter: (Double) -> String,
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

    // Sanea NaN/Infinity → 0; estos valores nunca se animan.
    val target = if (value.isNaN() || value.isInfinite()) 0.0 else value
    val nonFinite = value.isNaN() || value.isInfinite()

    // Estado del count-up. Se anima un Float 0→target; el progreso vive aquí.
    val anim = remember { Animatable(0f) }

    // Sentinel "no emitido": controla que el count-up solo ocurra en el PRIMER reveal.
    var revealed by rememberSaveable { mutableStateOf(false) }

    // rememberUpdatedState para leer el target más reciente dentro de la corrutina sin reiniciarla.
    val currentTarget by rememberUpdatedState(target)

    if (hidden) {
        // MITIGACIÓN B2: oculto → MASK y NO se instancia la animación.
        Text(
            text = MASK,
            modifier = modifier.semantics { contentDescription = "Monto oculto" },
            style = style.copy(fontFeatureSettings = "tnum"),
            color = color,
            maxLines = maxLines,
            softWrap = softWrap,
            overflow = overflow,
            textAlign = textAlign,
        )
        return
    }

    // Visible. Decide qué valor mostrar: animado (primer reveal) o final.
    // El LaunchedEffect se descarta cuando `hidden` vuelve a true (early-return de arriba),
    // lo que cancela la corrutina; el bloque finally hace el snapTo de mitigación (b).
    LaunchedEffect(Unit) {
        if (nonFinite || reduced || revealed) {
            // No animar: salta al valor final (o 0 saneado) y marca como revelado.
            anim.snapTo(currentTarget.toFloat())
            revealed = true
        } else {
            // Primer reveal: count-up 0→target con tween(Long, EmphasizedEasing).
            anim.snapTo(0f)
            try {
                anim.animateTo(
                    targetValue = currentTarget.toFloat(),
                    animationSpec = Motion.long(),
                )
            } finally {
                // Mitigación (b): al cancelar (hidden a mitad) o al terminar, fija el valor final.
                anim.snapTo(currentTarget.toFloat())
                revealed = true
            }
        }
    }

    // El string solo se recalcula cuando el ENTERO formateado cambia (no por frame),
    // porque formatCLP/formatUSD redondean a entero/2 decimales y derivedStateOf compara
    // el resultado (String): los frames intermedios con el mismo entero no recomponen Text.
    val displayText by remember(formatter, prefix, target) {
        derivedStateOf {
            // Si ya se reveló por completo, formatea el target exacto; si no, el valor animado.
            val raw = if (revealed) currentTarget else anim.value.toDouble()
            prefix + formatter(raw)
        }
    }

    Text(
        text = displayText,
        modifier = modifier,
        style = style.copy(fontFeatureSettings = "tnum"),
        color = color,
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = overflow,
        textAlign = textAlign,
    )
}
