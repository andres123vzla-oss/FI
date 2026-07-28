package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cartola.CartolaFormatException
import com.example.data.cartola.CartolaImporter
import com.example.data.cartola.CartolaMapping
import com.example.data.cartola.CartolaParser
import com.example.data.database.AppDatabase
import com.example.data.repository.FinanceRepository
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado del flujo de importación de cartola (P1-2), patrón de [BackupUiState]. */
sealed class CartolaUiState {
    object Idle : CartolaUiState()
    data class Working(val label: String) : CartolaUiState()

    /** Archivo leído y analizado: falta que el usuario confirme el mapeo de columnas. */
    data class Preview(
        val parsed: CartolaParser.Parsed,
        val guess: CartolaMapping,
    ) : CartolaUiState()

    data class Success(val message: String) : CartolaUiState()
    data class Error(val message: String) : CartolaUiState()
}

/**
 * ViewModel de la importación de cartola bancaria CSV (P1-2). Separado (patrón BackupViewModel).
 *
 * Flujo: [loadFile] (SAF → decode → parse → adivinar mapeo) deja el estado en [Preview];
 * la UI muestra el diálogo de mapeo; [confirmImport] arma el plan (dedup incluido) e inserta
 * SOLO los nuevos en una transacción atómica. Jamás borra ni modifica movimientos existentes.
 */
class CartolaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FinanceRepository(AppDatabase.getDatabase(application).financeDao())

    val state = MutableStateFlow<CartolaUiState>(CartolaUiState.Idle)

    /** Lee y analiza el archivo elegido; deja la vista previa lista para el mapeo. */
    fun loadFile(uri: Uri) {
        if (state.value is CartolaUiState.Working) return
        state.value = CartolaUiState.Working("Leyendo cartola…")
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw CartolaFormatException("No se pudo abrir el archivo seleccionado.")
                    input.use { readBounded(it) }
                        ?: throw CartolaFormatException("El archivo supera el tamaño máximo (10 MB).")
                }
                val parsed = CartolaParser.parse(CartolaParser.decode(bytes))
                if (parsed.rows.isEmpty()) {
                    throw CartolaFormatException("La cartola no tiene filas de movimientos.")
                }
                state.value = CartolaUiState.Preview(parsed, CartolaParser.guessMapping(parsed.headers))
            } catch (e: CancellationException) {
                throw e
            } catch (e: CartolaFormatException) {
                state.value = CartolaUiState.Error(e.message ?: "Archivo no válido.")
            } catch (e: Exception) {
                // Genérico: sin detalles del sistema ni contenido del archivo (montos).
                state.value = CartolaUiState.Error("No se pudo leer el archivo. ¿Es el CSV de tu banco?")
            }
        }
    }

    /** Importa con el mapeo confirmado. Solo agrega nuevos; duplicados e inválidos se reportan. */
    fun confirmImport(mapping: CartolaMapping) {
        val preview = state.value as? CartolaUiState.Preview ?: return
        state.value = CartolaUiState.Working("Importando movimientos…")
        viewModelScope.launch {
            try {
                val existing = repository.allTransactions.first()
                val plan = CartolaImporter.buildPlan(preview.parsed, mapping, existing)
                if (plan.nuevos.isNotEmpty()) {
                    repository.importTransactions(plan.nuevos)
                }
                val duplicados = plan.duplicadosExistentes + plan.duplicadosEnArchivo
                state.value = CartolaUiState.Success(
                    buildString {
                        if (plan.nuevos.isEmpty()) {
                            append("Nada nuevo que importar")
                        } else {
                            append("Cartola importada: ${plan.nuevos.size} movimientos nuevos")
                        }
                        if (duplicados > 0) append("; $duplicados duplicados omitidos")
                        if (plan.filasIgnoradas > 0) append("; ${plan.filasIgnoradas} filas ignoradas")
                        append(".")
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: CartolaFormatException) {
                state.value = CartolaUiState.Error(e.message ?: "Mapeo incompleto.")
            } catch (e: Exception) {
                // La inserción es atómica: ante un fallo no queda nada a medias.
                state.value = CartolaUiState.Error("No se pudo importar la cartola. Nada se modificó.")
            }
        }
    }

    /** El usuario canceló el diálogo de mapeo. */
    fun dismissPreview() {
        if (state.value is CartolaUiState.Preview) state.value = CartolaUiState.Idle
    }

    /** La UI consumió el resultado (snackbar mostrado): volver a Idle. */
    fun acknowledgeResult() {
        val s = state.value
        if (s is CartolaUiState.Success || s is CartolaUiState.Error) {
            state.value = CartolaUiState.Idle
        }
    }

    /** Lectura acotada (minSdk 24): null si supera [MAX_FILE_BYTES]. */
    private fun readBounded(input: InputStream): ByteArray? {
        val buf = ByteArray(8192)
        val out = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n == -1) break
            total += n
            if (total > MAX_FILE_BYTES) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        /** Muy por encima de cualquier cartola real (que pesa unos KB). */
        const val MAX_FILE_BYTES = 10 * 1024 * 1024
    }
}
