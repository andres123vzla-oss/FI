package com.example.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.security.SecurityViewModel

/** Filtra a solo dígitos y limita la longitud máxima del PIN. */
private fun sanitizePin(input: String): String =
    input.filter { it.isDigit() }.take(SecurityViewModel.MAX_PIN_LENGTH)

/**
 * Campo de PIN basado en [OutlinedTextField].
 *
 * SEC2-08 (residuo aceptado): estos diálogos mantienen el PIN como `String` en estado Compose
 * (`mutableStateOf("")` + campo de texto). Los `String` de la UI y del IME son inmutables y no
 * borrables, por lo que pueden quedar copias en memoria hasta el GC; no es alcanzable una
 * garantía de borrado de extremo a extremo desde estas superficies. La vía `CharArray`
 * (`.toCharArray()` hacia el ViewModel) cubre la copia que viaja al hasher, que sí se borra
 * tras su uso.
 */
@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(sanitizePin(it)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().testTag(testTag)
    )
}

/** Diálogo para configurar el PIN por primera vez (pide confirmación). */
@Composable
fun SetupPinDialog(
    onDismiss: () -> Unit,
    onConfirm: (pin: String, onError: (String) -> Unit) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar PIN", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Define un PIN de ${SecurityViewModel.MIN_PIN_LENGTH} a ${SecurityViewModel.MAX_PIN_LENGTH} dígitos para proteger tus datos financieros.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                // UX2-08: error siempre compuesto + liveRegion para que TalkBack lo anuncie.
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                PinField(pin, { pin = it }, "Nuevo PIN", "setup_pin_field")
                PinField(confirm, { confirm = it }, "Confirmar PIN", "setup_pin_confirm_field")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin != confirm) {
                        error = "Los PIN no coinciden."
                        return@Button
                    }
                    onConfirm(pin) { error = it }
                },
                modifier = Modifier.testTag("setup_pin_confirm")
            ) { Text("Guardar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Diálogo para cambiar el PIN: requiere el PIN actual y el nuevo (con confirmación). */
@Composable
fun ChangePinDialog(
    onDismiss: () -> Unit,
    onConfirm: (current: String, new: String, onError: (String) -> Unit) -> Unit
) {
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar PIN", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // UX2-08: error siempre compuesto + liveRegion para que TalkBack lo anuncie.
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                PinField(current, { current = it }, "PIN actual", "change_current_pin_field")
                PinField(newPin, { newPin = it }, "Nuevo PIN", "change_new_pin_field")
                PinField(confirm, { confirm = it }, "Confirmar nuevo PIN", "change_confirm_pin_field")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPin != confirm) {
                        error = "Los PIN nuevos no coinciden."
                        return@Button
                    }
                    onConfirm(current, newPin) { error = it }
                },
                modifier = Modifier.testTag("change_pin_confirm")
            ) { Text("Actualizar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/**
 * Diálogo genérico de reautenticación: pide el PIN actual para confirmar una acción sensible.
 * Reutilizable para "quitar PIN", "borrar datos", "restaurar semilla", etc.
 */
@Composable
fun ConfirmPinDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (pin: String, onError: (String) -> Unit) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                // UX2-08: error siempre compuesto + liveRegion para que TalkBack lo anuncie.
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                PinField(pin, { pin = it }, "PIN", "confirm_pin_field")
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pin) { error = it } },
                modifier = Modifier.testTag("confirm_pin_button")
            ) { Text(confirmText, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
