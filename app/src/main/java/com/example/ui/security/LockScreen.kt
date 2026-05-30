package com.example.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.SecurityViewModel

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
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

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
                    .size(72.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pin.isEmpty()) {
                    Text(
                        "• • • •",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
                        fontSize = 18.sp
                    )
                } else {
                    val visibleDots = pin.length.coerceAtMost(SecurityViewModel.MAX_PIN_LENGTH)
                    repeat(visibleDots) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("lock_error")
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            PinPad(
                modifier = Modifier.widthIn(max = 320.dp),
                onDigit = { d ->
                    if (pin.length < SecurityViewModel.MAX_PIN_LENGTH) {
                        pin += d
                        error = null
                    }
                },
                onBackspace = {
                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                },
                onConfirm = {
                    val entered = pin
                    securityViewModel.verifyPinForUnlock(entered) { success, err ->
                        if (!success) {
                            error = err
                            pin = ""
                        }
                    }
                },
                confirmEnabled = pin.length >= SecurityViewModel.MIN_PIN_LENGTH
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
