package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupFormatException
import com.example.data.backup.BackupManager
import com.example.data.database.AppDatabase
import com.example.data.repository.FinanceRepository
import com.example.security.SecurityPreferences
import java.io.OutputStream
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado del flujo de respaldo mostrado en Ajustes (patrón de [PriceUpdateState]). */
sealed class BackupUiState {
    object Idle : BackupUiState()
    data class Working(val label: String) : BackupUiState()
    data class Success(val message: String) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}

/**
 * ViewModel del RESPALDO cifrado (P0). Vive separado de [FinanceViewModel] a propósito
 * (decisión C6 del análisis: no seguir engordando un VM de ~950 líneas con otro dominio).
 *
 * Contratos de seguridad:
 *  - La passphrase llega como CharArray y se pone a cero al terminar cada operación (SEC-08).
 *  - Nunca se loguea nada del contenido ni de la passphrase.
 *  - Los errores de import dejan SIEMPRE explícito que los datos actuales no se tocaron
 *    (garantía real del orden de operaciones de [BackupManager.importFrom]).
 */
class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FinanceRepository(AppDatabase.getDatabase(application).financeDao())
    private val manager = BackupManager(repository)
    private val prefs = SecurityPreferences(application)

    /** Último export exitoso (epoch ms); null si nunca. Para "Último respaldo: hace N días". */
    val lastBackupAt: StateFlow<Long?> = prefs.lastBackupAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)

    /** Exporta todo al documento SAF elegido por el usuario, sellado con su passphrase. */
    fun exportTo(uri: Uri, passphrase: CharArray) {
        if (state.value is BackupUiState.Working) return
        state.value = BackupUiState.Working("Exportando respaldo…")
        viewModelScope.launch {
            try {
                val snapshot = openTruncatingOutput(uri).use { out ->
                    manager.exportTo(out, passphrase)
                }
                prefs.setLastBackupAt(System.currentTimeMillis())
                state.value = BackupUiState.Success(
                    "Respaldo exportado (${snapshot.transactions.size} movimientos, " +
                        "${snapshot.investments.size} activos). Guarda el archivo y tu " +
                        "passphrase en un lugar seguro.",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Mensaje genérico: no se filtran detalles del sistema ni datos financieros.
                state.value = BackupUiState.Error(
                    "No se pudo exportar el respaldo. Revisa el destino e inténtalo de nuevo.",
                )
            } finally {
                passphrase.fill(' ') // SEC-08: borrar la copia que viajó al VM.
            }
        }
    }

    /** Importa (REEMPLAZA todo) desde el documento SAF; la UI ya confirmó y reautenticó. */
    fun importFrom(uri: Uri, passphrase: CharArray) {
        if (state.value is BackupUiState.Working) return
        state.value = BackupUiState.Working("Importando respaldo…")
        viewModelScope.launch {
            try {
                val resolver = getApplication<Application>().contentResolver
                val input = resolver.openInputStream(uri)
                    ?: throw BackupFormatException("No se pudo abrir el archivo seleccionado.")
                val snapshot = input.use { manager.importFrom(it, passphrase) }
                state.value = BackupUiState.Success(
                    "Respaldo importado: ${snapshot.transactions.size} movimientos, " +
                        "${snapshot.categories.size} categorías, " +
                        "${snapshot.investments.size} activos.",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: AEADBadTagException) {
                state.value = BackupUiState.Error(
                    "Passphrase incorrecta o archivo dañado. Tus datos actuales no se modificaron.",
                )
            } catch (e: BackupFormatException) {
                state.value = BackupUiState.Error(
                    (e.message ?: "El archivo no es un respaldo válido.") +
                        " Tus datos actuales no se modificaron.",
                )
            } catch (e: Exception) {
                state.value = BackupUiState.Error(
                    "No se pudo importar el respaldo. Tus datos actuales no se modificaron.",
                )
            } finally {
                passphrase.fill(' ') // SEC-08
            }
        }
    }

    /** La UI consumió el resultado (snackbar mostrado): volver a Idle. */
    fun acknowledgeResult() {
        if (state.value !is BackupUiState.Working) state.value = BackupUiState.Idle
    }

    /**
     * Abre el destino TRUNCANDO contenido previo ("wt"): si el usuario sobrescribe un respaldo
     * más largo, no deben quedar bytes viejos colgando. Algunos providers no soportan "wt";
     * se degrada a "w" (el JSON resultante sigue siendo parseable: org.json ignora bytes tras
     * el objeto raíz).
     */
    private fun openTruncatingOutput(uri: Uri): OutputStream {
        val resolver = getApplication<Application>().contentResolver
        val stream = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "w")
        return stream ?: throw IllegalStateException("No se pudo abrir el destino del respaldo.")
    }
}
