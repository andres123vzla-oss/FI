package com.example.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.example.security.BackupCrypto

/**
 * Diálogos de passphrase del RESPALDO cifrado (P0), espejo de [SetupPinDialog]/[ConfirmPinDialog].
 *
 * SEC2-08 (residuo aceptado, igual que los diálogos de PIN): el campo de texto mantiene la
 * passphrase como `String` en estado Compose; la copia que viaja al ViewModel se convierte a
 * `CharArray` y ESA sí se borra tras usarse (SEC-08 en BackupViewModel).
 */
@Composable
private fun PassphraseField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
}

/** Texto de error con liveRegion (UX2-08): TalkBack lo anuncia al aparecer. */
@Composable
private fun ErrorLine(error: String?) {
    Text(
        error ?: "",
        color = MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * Passphrase para EXPORTAR: campo + confirmación + advertencia de irrecuperabilidad.
 * Valida largo mínimo ([BackupCrypto.MIN_PASSPHRASE_LENGTH]) y coincidencia antes de confirmar.
 */
@Composable
fun ExportBackupDialog(
    onDismiss: () -> Unit,
    onConfirm: (passphrase: String) -> Unit,
) {
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar respaldo cifrado", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Define una passphrase (mínimo ${BackupCrypto.MIN_PASSPHRASE_LENGTH} " +
                        "caracteres, distinta de tu PIN). El archivo solo podrá abrirse con ella: " +
                        "si la olvidas, el respaldo será IRRECUPERABLE.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                ErrorLine(error)
                PassphraseField(pass, { pass = it }, "Passphrase", "backup_export_pass_field")
                PassphraseField(confirm, { confirm = it }, "Confirmar passphrase", "backup_export_confirm_field")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        pass.length < BackupCrypto.MIN_PASSPHRASE_LENGTH ->
                            error = "La passphrase debe tener al menos " +
                                "${BackupCrypto.MIN_PASSPHRASE_LENGTH} caracteres."
                        pass != confirm -> error = "Las passphrases no coinciden."
                        else -> onConfirm(pass)
                    }
                },
                modifier = Modifier.testTag("backup_export_confirm"),
            ) { Text("Exportar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * Passphrase para IMPORTAR, con la advertencia de reemplazo total (patrón del diálogo de
 * borrado). El botón usa los tokens error/onError: la acción es destructiva para los datos
 * actuales. Tras confirmar aquí, el flujo aún exige reautenticación por PIN si está configurado.
 */
@Composable
fun ImportBackupDialog(
    onDismiss: () -> Unit,
    onConfirm: (passphrase: String) -> Unit,
) {
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar respaldo", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Esta acción REEMPLAZA todos tus datos actuales (movimientos, categorías, " +
                        "presupuestos y portafolio) por los del respaldo. Ingresa la passphrase " +
                        "con la que lo exportaste.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                ErrorLine(error)
                PassphraseField(pass, { pass = it }, "Passphrase del respaldo", "backup_import_pass_field")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pass.isEmpty()) {
                        error = "Ingresa la passphrase del respaldo."
                    } else {
                        onConfirm(pass)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.testTag("backup_import_confirm"),
            ) { Text("Importar y reemplazar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
