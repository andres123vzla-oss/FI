package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.entity.CategoryEntity

/**
 * Diálogo de creación de una regla de movimiento RECURRENTE (P1-1).
 *
 * Patrones reutilizados: validación con mensaje en `liveRegion` (UX2-08, SecurityDialogs),
 * estado en `rememberSaveable` (UX2-09) y selector de categoría con `DropdownMenu` estable
 * (TextField readOnly + overlay clicable, sin APIs experimentales de menuAnchor).
 *
 * El monto acepta formato chileno ("458.000" o "458000"); la coma actúa como decimal.
 */
@Composable
fun RecurringRuleDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (type: String, categoryName: String, description: String, amount: Double, dayOfMonth: Int) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf("EXPENSE") }
    var categoryName by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var dayText by rememberSaveable { mutableStateOf("1") }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val options = categories.filter { it.type == type }.map { it.name }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva regla recurrente", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Se registrará un movimiento automático cada mes en el día elegido " +
                        "(en meses cortos, el último día del mes).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                // UX2-08: error siempre compuesto + liveRegion para que TalkBack lo anuncie.
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "EXPENSE",
                        onClick = { type = "EXPENSE"; categoryName = "" },
                        label = { Text("Gasto") },
                        modifier = Modifier.testTag("recurring_type_expense"),
                    )
                    FilterChip(
                        selected = type == "INCOME",
                        onClick = { type = "INCOME"; categoryName = "" },
                        label = { Text("Ingreso") },
                        modifier = Modifier.testTag("recurring_type_income"),
                    )
                }
                // Selector de categoría (DropdownMenu estable, sin menuAnchor experimental).
                Box {
                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("recurring_category_field"),
                    )
                    // Overlay transparente: un TextField readOnly no propaga el click al Box.
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable { expanded = true },
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        if (options.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No hay categorías de este tipo") },
                                onClick = { expanded = false },
                            )
                        } else {
                            options.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        categoryName = name
                                        expanded = false
                                        error = null
                                    },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (p. ej. Arriendo)") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("recurring_description_field"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { input ->
                            amountText = input.filter { it.isDigit() || it == '.' || it == ',' }
                        },
                        label = { Text("Monto CLP") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("recurring_amount_field"),
                    )
                    OutlinedTextField(
                        value = dayText,
                        onValueChange = { input -> dayText = input.filter(Char::isDigit).take(2) },
                        label = { Text("Día") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.width(96.dp).testTag("recurring_day_field"),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // CLP: puntos de miles se descartan; coma = separador decimal.
                    val amount = amountText.replace(".", "").replace(',', '.').toDoubleOrNull()
                    val day = dayText.toIntOrNull()
                    when {
                        categoryName.isEmpty() -> error = "Elige una categoría."
                        amount == null || !amount.isFinite() || amount <= 0.0 ->
                            error = "Ingresa un monto válido mayor que 0."
                        day == null || day !in 1..31 -> error = "El día debe estar entre 1 y 31."
                        else -> onConfirm(type, categoryName, description, amount, day)
                    }
                },
                modifier = Modifier.testTag("recurring_confirm"),
            ) { Text("Crear", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
