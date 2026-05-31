package com.example.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.security.SecurityViewModel
import com.example.ui.theme.ExcelDarkBlue
import com.example.ui.theme.ExcelGreen
import com.example.ui.theme.ExcelRed

/**
 * Tarjeta de configuración de seguridad para la pantalla de Ajustes.
 * No contiene lógica de negocio financiera; solo observa estado de seguridad y emite acciones.
 */
@Composable
fun SecuritySettingsCard(
    securityViewModel: SecurityViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val isPinSet by securityViewModel.isPinSet.collectAsState()
    val biometricEnabled by securityViewModel.biometricEnabled.collectAsState()
    val autoLockMinutes by securityViewModel.autoLockMinutes.collectAsState()
    val hideAmounts by securityViewModel.hideAmounts.collectAsState()
    val biometricAvailable = remember { securityViewModel.biometricAvailable() }

    var showSetup by remember { mutableStateOf(false) }
    var showChange by remember { mutableStateOf(false) }
    var showRemove by remember { mutableStateOf(false) }
    var lockExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("security_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ExcelDarkBlue.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = ExcelDarkBlue)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Bloqueo de la aplicación",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        if (isPinSet) "Protegido con PIN" else "Sin protección configurada",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isPinSet) ExcelGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Privacidad: ocultar montos sensibles (independiente del PIN, solo presentación).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (hideAmounts) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = ExcelDarkBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Ocultar montos",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Enmascara saldos e importes en toda la app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = hideAmounts,
                    onCheckedChange = { securityViewModel.setHideAmounts(it) },
                    modifier = Modifier.testTag("hide_amounts_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            if (!isPinSet) {
                Button(
                    onClick = { showSetup = true },
                    modifier = Modifier.fillMaxWidth().testTag("setup_pin_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ExcelDarkBlue)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Configurar PIN de acceso", fontWeight = FontWeight.Bold)
                }
            } else {
                // Cambiar PIN
                SettingRow(
                    icon = Icons.Default.LockReset,
                    title = "Cambiar PIN",
                    subtitle = "Actualiza tu código de acceso",
                    onClick = { showChange = true },
                    testTag = "change_pin_row"
                )

                // Biometría
                if (biometricAvailable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = ExcelDarkBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Desbloqueo con biometría",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "Usa huella o rostro como acceso rápido",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = { securityViewModel.setBiometricEnabled(it) },
                            modifier = Modifier.testTag("biometric_switch")
                        )
                    }
                }

                // Bloqueo automático
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LockClock,
                        contentDescription = null,
                        tint = ExcelDarkBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Bloqueo automático",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Tras inactividad en segundo plano",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Box {
                        TextButton(
                            onClick = { lockExpanded = true },
                            modifier = Modifier.testTag("autolock_selector")
                        ) {
                            Text(autoLockLabel(autoLockMinutes), fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = lockExpanded, onDismissRequest = { lockExpanded = false }) {
                            autoLockOptions.forEach { (minutes, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        securityViewModel.setAutoLockMinutes(minutes)
                                        lockExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Bloquear ahora
                SettingRow(
                    icon = Icons.Default.Lock,
                    title = "Bloquear ahora",
                    subtitle = "Cierra la sesión inmediatamente",
                    onClick = { securityViewModel.lockNow() },
                    testTag = "lock_now_row"
                )

                // Quitar PIN (acción sensible → requiere reautenticación)
                SettingRow(
                    icon = Icons.Default.LockReset,
                    title = "Quitar PIN",
                    subtitle = "Desactiva el bloqueo (requiere PIN actual)",
                    tint = ExcelRed,
                    onClick = { showRemove = true },
                    testTag = "remove_pin_row"
                )
            }
        }
    }

    if (showSetup) {
        SetupPinDialog(
            onDismiss = { showSetup = false },
            onConfirm = { pin, onError ->
                securityViewModel.setupPin(pin) { error ->
                    if (error == null) showSetup = false else onError(error)
                }
            }
        )
    }

    if (showChange) {
        ChangePinDialog(
            onDismiss = { showChange = false },
            onConfirm = { current, new, onError ->
                securityViewModel.changePin(current, new) { error ->
                    if (error == null) showChange = false else onError(error)
                }
            }
        )
    }

    if (showRemove) {
        ConfirmPinDialog(
            title = "Quitar PIN",
            message = "Ingresa tu PIN actual para desactivar el bloqueo de la app.",
            confirmText = "Quitar PIN",
            onDismiss = { showRemove = false },
            onConfirm = { pin, onError ->
                securityViewModel.removePin(pin) { error ->
                    if (error == null) showRemove = false else onError(error)
                }
            }
        )
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = ExcelDarkBlue,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = tint)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

private val autoLockOptions = listOf(
    0 to "Inmediato",
    1 to "1 min",
    5 to "5 min",
    15 to "15 min"
)

private fun autoLockLabel(minutes: Int): String =
    autoLockOptions.firstOrNull { it.first == minutes }?.second ?: "$minutes min"
