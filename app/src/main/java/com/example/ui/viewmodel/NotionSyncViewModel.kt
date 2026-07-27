package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.notion.NotionApi
import com.example.data.notion.NotionApiException
import com.example.data.notion.NotionSyncManager
import com.example.data.repository.FinanceRepository
import com.example.security.SecurityPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado del flujo de sync con Notion mostrado en Ajustes (patrón de [BackupUiState]). */
sealed class NotionSyncUiState {
    object Idle : NotionSyncUiState()
    data class Working(val label: String) : NotionSyncUiState()
    data class Success(val message: String) : NotionSyncUiState()
    data class Error(val message: String) : NotionSyncUiState()
}

/**
 * ViewModel de la SYNC one-way app → Notion. Separado (como [BackupViewModel]): otro dominio.
 *
 * Contratos de seguridad:
 *  - El token vive ENVUELTO con el Keystore en [SecurityPreferences]; aquí solo se desenvuelve
 *    para el uso inmediato del cliente y jamás se retiene ni se loguea.
 *  - Sin sync silenciosa: solo corre cuando el usuario toca "Sincronizar".
 *  - Los errores llegan tipados desde [NotionApiException] con mensajes aptos para UI.
 */
class NotionSyncViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FinanceRepository(AppDatabase.getDatabase(application).financeDao())
    private val prefs = SecurityPreferences(application)

    /** ¿Hay token configurado? (Nunca expone el token.) */
    val hasToken: StateFlow<Boolean> = prefs.hasNotionToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Última sync exitosa (epoch ms); null si nunca. */
    val lastSyncAt: StateFlow<Long?> = prefs.lastNotionSyncAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val state = MutableStateFlow<NotionSyncUiState>(NotionSyncUiState.Idle)

    /** Guarda el token (cifrado con el Keystore). El String de la UI es residuo SEC2-08. */
    fun saveToken(token: String) {
        viewModelScope.launch {
            val trimmed = token.trim()
            if (trimmed.isEmpty()) {
                state.value = NotionSyncUiState.Error("El token no puede estar vacío.")
            } else {
                prefs.setNotionToken(trimmed)
                state.value = NotionSyncUiState.Success("Token guardado cifrado en el dispositivo.")
            }
        }
    }

    fun clearToken() {
        viewModelScope.launch {
            prefs.clearNotionToken()
            state.value = NotionSyncUiState.Success("Token de Notion eliminado.")
        }
    }

    /** Sincroniza TODO ahora (manual). Reanudable: un fallo a mitad nunca duplica páginas. */
    fun syncNow() {
        if (state.value is NotionSyncUiState.Working) return
        state.value = NotionSyncUiState.Working("Sincronizando con Notion…")
        viewModelScope.launch {
            try {
                val token = prefs.getNotionToken()
                    ?: throw NotionApiException("Configura primero tu token de Notion.")
                val summary = NotionSyncManager(repository, NotionApi(token)).syncAll()
                prefs.setLastNotionSyncAt(System.currentTimeMillis())
                state.value = NotionSyncUiState.Success(
                    "Notion actualizado: ${summary.created} páginas nuevas, ${summary.updated} actualizadas.",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: NotionApiException) {
                state.value = NotionSyncUiState.Error(e.message ?: "Error de Notion.")
            } catch (e: Exception) {
                // Genérico: jamás se filtran detalles con montos ni el token.
                state.value = NotionSyncUiState.Error("No se pudo sincronizar con Notion.")
            }
        }
    }

    /** La UI consumió el resultado (snackbar mostrado): volver a Idle. */
    fun acknowledgeResult() {
        if (state.value !is NotionSyncUiState.Working) state.value = NotionSyncUiState.Idle
    }
}
