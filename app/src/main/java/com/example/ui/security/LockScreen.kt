package com.example.ui.security

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.SecurityViewModel
import com.example.ui.theme.Motion
import com.example.ui.theme.rememberReducedMotion
import kotlin.math.roundToInt

/**
 * Pantalla de bloqueo. Se muestra cuando hay PIN configurado y la app está bloqueada,
 * impidiendo el acceso a cualquier dato financiero antes de autenticarse.
 *
 * @param onBiometricRequested invoca el BiometricPrompt (cableado en la Activity, que es
 *        la dueña del [androidx.fragment.app.FragmentActivity]).
 */
@Composable
fun LockScreen(
    securityViewModel: SecurityViewModel,
    onBiometricRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    // SEC-08: en ESTA pantalla el PIN se acumula en un buffer de caracteres borrable (no un String
    // inmutable) que se entrega como CharArray a la verificación y se limpia. Nota: las superficies
    // de setup/cambio de PIN usan campos de texto (String/IME), por lo que esta propiedad aplica
    // solo al desbloqueo y no es una garantía de extremo a extremo (residuo aceptado, ver SEC2-08
    // en SecurityDialogs.kt).
    val pin = remember { mutableStateListOf<Char>() }
    var error by remember { mutableStateOf<String?>(null) }

    // Movimiento: scale-in de los dots y shake en PIN incorrecto (respeta "reducir movimiento").
    val reducedMotion = rememberReducedMotion()
    // V1: respuesta táctil del desbloqueo. No se liga a "reducir movimiento": la háptica tiene su
    // propio control a nivel de sistema y no provoca mareo visual.
    val haptics = LocalHapticFeedback.current
    var errorNonce by remember { mutableStateOf(0) }
    val shakeX = remember { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(errorNonce) {
        if (errorNonce > 0 && !reducedMotion) {
            val amp = with(density) { 12.dp.toPx() }
            shakeX.snapTo(0f)
            listOf(-1f, 1f, -0.7f, 0.7f, -0.35f, 0.35f, 0f).forEach { factor ->
                shakeX.animateTo(factor * amp, tween(durationMillis = 40, easing = LinearEasing))
            }
        }
    }

    val biometricEnabled by securityViewModel.biometricEnabled.collectAsState()
    val biometricAvailable = remember { securityViewModel.biometricAvailable() }
    val showBiometric = biometricEnabled && biometricAvailable

    // Auto-lanza el prompt biométrico al aparecer la pantalla, si está activo.
    LaunchedEffect(showBiometric) {
        if (showBiometric) onBiometricRequested()
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("lock_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Mi Panel Financiero",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Ingresa tu PIN para continuar",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            // Indicadores de dígitos (no se muestra el PIN en claro).
            // UX2-08: clearAndSetSemantics para que TalkBack NO lea el placeholder "• • • •";
            // se anuncia solo la CANTIDAD de dígitos, nunca valores.
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .offset { IntOffset(shakeX.value.roundToInt(), 0) }
                    .clearAndSetSemantics {
                        contentDescription = "${pin.size} dígitos ingresados"
                        liveRegion = LiveRegionMode.Polite
                    }
            ) {
                if (pin.isEmpty()) {
                    Text(
                        "• • • •",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
                        fontSize = 18.sp
                    )
                } else {
                    val visibleDots = pin.size.coerceAtMost(SecurityViewModel.MAX_PIN_LENGTH)
                    repeat(visibleDots) {
                        PinDot(reduced = reducedMotion)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // UX2-08: el Text de error queda SIEMPRE compuesto (text vacío si no hay error) para
            // que el liveRegion exista antes del cambio y TalkBack anuncie el mensaje.
            Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .testTag("lock_error")
                        .semantics { liveRegion = LiveRegionMode.Assertive }
                )
            }

            Spacer(Modifier.height(8.dp))

            PinPad(
                modifier = Modifier.widthIn(max = 320.dp),
                onDigit = { d ->
                    if (pin.size < SecurityViewModel.MAX_PIN_LENGTH) {
                        pin.add(d)
                        error = null
                    }
                },
                onBackspace = {
                    if (pin.isNotEmpty()) pin.removeAt(pin.size - 1)
                },
                onConfirm = {
                    // Copia efímera del buffer; el ViewModel la borra tras verificar (SEC-08).
                    val entered = CharArray(pin.size) { pin[it] }
                    pin.clear()
                    securityViewModel.verifyPinForUnlock(entered) { success, err ->
                        if (success) {
                            // V1: confirmación táctil del desbloqueo (complementa la transición).
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        } else {
                            // V1: rechazo táctil junto con el shake visual y el mensaje.
                            haptics.performHapticFeedback(HapticFeedbackType.Reject)
                            error = err
                            errorNonce++
                        }
                    }
                },
                confirmEnabled = pin.size >= SecurityViewModel.MIN_PIN_LENGTH
            )

            if (showBiometric) {
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = onBiometricRequested,
                    modifier = Modifier.testTag("biometric_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Usar biometría", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/**
 * Punto del PIN con entrada "scale-in" suave al aparecer. Solo anima la escala (no el tamaño),
 * por lo que el ancho del layout es estable. No revela el valor ni la magnitud del PIN.
 */
@Composable
private fun PinDot(reduced: Boolean) {
    val scale = remember { Animatable(if (reduced) 1f else 0.6f) }
    LaunchedEffect(Unit) {
        if (!reduced) scale.animateTo(1f, Motion.fast())
    }
    Box(
        modifier = Modifier
            .size(14.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
    )
}
