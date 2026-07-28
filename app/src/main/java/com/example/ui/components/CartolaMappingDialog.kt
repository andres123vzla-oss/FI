package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.cartola.CartolaMapping
import com.example.data.cartola.CartolaParser
import com.example.util.FormatUtils

/**
 * Diálogo de MAPEO de la cartola (P1-2): confirma/corrige qué columna es qué, con una vista
 * previa interpretada en vivo de las primeras filas para que el usuario vea exactamente qué se
 * va a importar antes de confirmar. El estado NO usa rememberSaveable: si el proceso muere, el
 * flujo se reabre desde el archivo (los índices dependen del Parsed en memoria).
 */
@Composable
fun CartolaMappingDialog(
    parsed: CartolaParser.Parsed,
    initialMapping: CartolaMapping,
    categoryOptions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (CartolaMapping) -> Unit,
) {
    var fechaIdx by remember { mutableStateOf(initialMapping.fechaIndex) }
    var descIdx by remember { mutableStateOf(initialMapping.descripcionIndex) }
    var montoIdx by remember { mutableStateOf(initialMapping.montoIndex) }
    var cargoIdx by remember { mutableStateOf(initialMapping.cargoIndex) }
    var abonoIdx by remember { mutableStateOf(initialMapping.abonoIndex) }
    var usaCargoAbono by remember {
        mutableStateOf(initialMapping.cargoIndex != null || initialMapping.abonoIndex != null)
    }
    var category by remember {
        mutableStateOf(
            initialMapping.categoryName.takeIf { it in categoryOptions }
                ?: categoryOptions.firstOrNull { it == "Otros" }
                ?: categoryOptions.firstOrNull()
                ?: "Otros",
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mapear columnas de la cartola", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Revisa qué columna es cada cosa (ya viene adivinado). Solo se AGREGAN " +
                        "movimientos: los duplicados se detectan y se omiten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                // UX2-08: error siempre compuesto + liveRegion.
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )

                ColumnSelector("Fecha", parsed.headers, fechaIdx, allowNone = false) {
                    fechaIdx = it; error = null
                }
                ColumnSelector("Descripción (opcional)", parsed.headers, descIdx, allowNone = true) {
                    descIdx = it
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = usaCargoAbono,
                        onClick = { usaCargoAbono = true; error = null },
                        label = { Text("Cargo y Abono") },
                        modifier = Modifier.testTag("cartola_mode_cargoabono"),
                    )
                    FilterChip(
                        selected = !usaCargoAbono,
                        onClick = { usaCargoAbono = false; error = null },
                        label = { Text("Monto con signo") },
                        modifier = Modifier.testTag("cartola_mode_monto"),
                    )
                }
                if (usaCargoAbono) {
                    ColumnSelector("Cargo (gastos)", parsed.headers, cargoIdx, allowNone = true) {
                        cargoIdx = it; error = null
                    }
                    ColumnSelector("Abono (ingresos)", parsed.headers, abonoIdx, allowNone = true) {
                        abonoIdx = it; error = null
                    }
                } else {
                    ColumnSelector("Monto (negativo = gasto)", parsed.headers, montoIdx, allowNone = true) {
                        montoIdx = it; error = null
                    }
                }

                OptionSelector("Categoría destino", categoryOptions, category) { category = it }

                // Vista previa interpretada en vivo de las primeras filas.
                Text(
                    "Vista previa (${parsed.rows.size} filas en el archivo):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
                        .testTag("cartola_preview"),
                ) {
                    parsed.rows.take(3).forEach { row ->
                        Text(
                            previewLine(row, fechaIdx, descIdx, usaCargoAbono, montoIdx, cargoIdx, abonoIdx),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        fechaIdx == null -> error = "Indica cuál columna es la Fecha."
                        usaCargoAbono && cargoIdx == null && abonoIdx == null ->
                            error = "Indica la columna de Cargo o la de Abono."
                        !usaCargoAbono && montoIdx == null ->
                            error = "Indica la columna del Monto."
                        else -> onConfirm(
                            CartolaMapping(
                                fechaIndex = fechaIdx,
                                descripcionIndex = descIdx,
                                montoIndex = if (usaCargoAbono) null else montoIdx,
                                cargoIndex = if (usaCargoAbono) cargoIdx else null,
                                abonoIndex = if (usaCargoAbono) abonoIdx else null,
                                categoryName = category,
                            ),
                        )
                    }
                },
                modifier = Modifier.testTag("cartola_confirm"),
            ) { Text("Importar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Línea de vista previa: "✓ fecha · ±monto · descripción" o "✗ fila ignorada". */
private fun previewLine(
    row: List<String>,
    fechaIdx: Int?,
    descIdx: Int?,
    usaCargoAbono: Boolean,
    montoIdx: Int?,
    cargoIdx: Int?,
    abonoIdx: Int?,
): String {
    val fecha = fechaIdx?.let { CartolaParser.parseDate(row.getOrNull(it).orEmpty()) }
        ?: return "✗ fila ignorada (sin fecha válida)"
    val desc = descIdx?.let { row.getOrNull(it)?.trim() }.orEmpty().ifEmpty { "(sin descripción)" }
    val montoTxt = if (usaCargoAbono) {
        val cargo = cargoIdx?.let { CartolaParser.parseAmountClp(row.getOrNull(it).orEmpty()) }
        val abono = abonoIdx?.let { CartolaParser.parseAmountClp(row.getOrNull(it).orEmpty()) }
        when {
            cargo != null && cargo != 0.0 -> "-" + FormatUtils.formatCLP(kotlin.math.abs(cargo))
            abono != null && abono != 0.0 -> "+" + FormatUtils.formatCLP(kotlin.math.abs(abono))
            else -> return "✗ fila ignorada (sin monto)"
        }
    } else {
        val v = montoIdx?.let { CartolaParser.parseAmountClp(row.getOrNull(it).orEmpty()) }
        if (v == null || v == 0.0) return "✗ fila ignorada (sin monto)"
        (if (v < 0) "-" else "+") + FormatUtils.formatCLP(kotlin.math.abs(v))
    }
    return "✓ $fecha · $montoTxt · ${desc.take(28)}"
}

/** Selector de columna: TextField readOnly + overlay clicable + DropdownMenu (API estable). */
@Composable
private fun ColumnSelector(
    label: String,
    headers: List<String>,
    selected: Int?,
    allowNone: Boolean,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = selected?.let { headers.getOrNull(it) } ?: "(ninguna)",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowNone) {
                DropdownMenuItem(
                    text = { Text("(ninguna)") },
                    onClick = { onSelect(null); expanded = false },
                )
            }
            headers.forEachIndexed { index, header ->
                DropdownMenuItem(
                    text = { Text(header.ifBlank { "(columna ${index + 1})" }) },
                    onClick = { onSelect(index); expanded = false },
                )
            }
        }
    }
}

/** Selector simple de texto (categoría destino). */
@Composable
private fun OptionSelector(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}
