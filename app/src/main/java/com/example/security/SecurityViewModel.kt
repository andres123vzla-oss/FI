package com.example.security

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Expone el estado de seguridad a la UI y centraliza las operaciones de App Lock:
 * configurar/cambiar/quitar PIN, verificar PIN (con control de intentos y lockout),
 * biometría, timeout de bloqueo y bloqueo manual.
 */
class SecurityViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = SecurityPreferences(application)

    val isPinSet: StateFlow<Boolean> =
        prefs.isPinSet.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialValue = false)

    val biometricEnabled: StateFlow<Boolean> =
        prefs.biometricEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialValue = false)

    val autoLockMinutes: StateFlow<Int> =
        prefs.autoLockMinutes.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = SecurityPreferences.DEFAULT_AUTO_LOCK_MINUTES,
        )

    /** Preferencia de privacidad: ocultar montos sensibles (solo afecta presentación). Oculto por defecto. */
    val hideAmounts: StateFlow<Boolean> =
        prefs.hideAmounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialValue = true)

    /**
     * Estado del candado para la raíz de la app.
     *
     * Se combina el flujo CRUDO de DataStore (no el StateFlow con valor inicial) para que el
     * estado permanezca en [LockGate.LOADING] hasta saber si hay PIN: así no se muestra ningún
     * dato financiero antes de resolver el bloqueo en el arranque en frío.
     *
     * La seguridad es OBLIGATORIA: si no hay PIN configurado, el estado es [LockGate.SETUP] y la
     * app fuerza la configuración del PIN antes de mostrar cualquier dato financiero.
     */
    val gate: StateFlow<LockGate> =
        combine(prefs.isPinSet, AppLockManager.isLocked) { pinSet, locked ->
            when {
                !pinSet -> LockGate.SETUP
                locked -> LockGate.LOCKED
                else -> LockGate.UNLOCKED
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LockGate.LOADING)

    fun autoLockMillis(): Long {
        val minutes = autoLockMinutes.value
        return if (minutes <= 0) 0L else minutes * 60_000L
    }

    fun biometricAvailable(): Boolean = BiometricHelper.isAvailable(getApplication())

    // --- Configuración del PIN ---

    /** Configura el PIN por primera vez. Devuelve mensaje de error o null si fue exitoso. */
    fun setupPin(pin: String, onResult: (error: String?) -> Unit) {
        val validation = validatePin(pin)
        if (validation != null) {
            onResult(validation)
            return
        }
        viewModelScope.launch {
            val salt = PinHasher.generateSalt()
            val hash = PinHasher.hash(pin, salt)
            prefs.setPin(hash, salt)
            AppLockManager.unlock()
            onResult(null)
        }
    }

    fun changePin(currentPin: String, newPin: String, onResult: (error: String?) -> Unit) {
        val validation = validatePin(newPin)
        if (validation != null) {
            onResult(validation)
            return
        }
        viewModelScope.launch {
            if (!verifyAgainstStored(currentPin)) {
                onResult("El PIN actual es incorrecto.")
                return@launch
            }
            val salt = PinHasher.generateSalt()
            val hash = PinHasher.hash(newPin, salt)
            prefs.setPin(hash, salt)
            onResult(null)
        }
    }

    fun removePin(currentPin: String, onResult: (error: String?) -> Unit) {
        viewModelScope.launch {
            if (!verifyAgainstStored(currentPin)) {
                onResult("El PIN actual es incorrecto.")
                return@launch
            }
            prefs.clearPin()
            // Sin PIN ya no hay bloqueo; aseguramos estado desbloqueado.
            AppLockManager.unlock()
            onResult(null)
        }
    }

    // --- Desbloqueo ---

    /**
     * Verifica el PIN al desbloquear, controlando intentos fallidos y lockout temporal.
     * @param onResult (éxito, mensajeError)
     */
    fun verifyPinForUnlock(pin: CharArray, onResult: (success: Boolean, error: String?) -> Unit) {
        viewModelScope.launch {
            try {
                // Reloj de pared: el lockout se persiste y debe sobrevivir a reinicios del proceso
                // sin verse afectado por el reinicio de elapsedRealtime tras reiniciar el equipo.
                val now = System.currentTimeMillis()
                val lockoutUntil = prefs.lockoutUntilElapsed.first()
                if (lockoutUntil > now) {
                    val secs = ((lockoutUntil - now) / 1000) + 1
                    onResult(false, "Demasiados intentos. Espera $secs s.")
                    return@launch
                }
                if (verifyAgainstStored(pin)) {
                    prefs.setFailedAttempts(0)
                    prefs.setLockoutUntil(0L)
                    prefs.setLockoutCount(0)
                    AppLockManager.unlock()
                    onResult(true, null)
                } else {
                    val attempts = (failedAttemptsSnapshot()) + 1
                    if (attempts >= MAX_ATTEMPTS) {
                        // Backoff exponencial (SEC-06): el lockout crece con cada ronda fallida
                        // encadenada. lockout_count NO se reinicia aquí, solo tras un desbloqueo
                        // exitoso (PIN o biometría), de modo que reintentar no devuelve 5 intentos
                        // cada 30 s indefinidamente.
                        val count = prefs.lockoutCount.first() + 1
                        val lockoutMs = (BASE_LOCKOUT_MS shl (count - 1).coerceIn(0, MAX_BACKOFF_SHIFT))
                            .coerceAtMost(MAX_LOCKOUT_MS)
                        prefs.setLockoutCount(count)
                        prefs.setFailedAttempts(0)
                        prefs.setLockoutUntil(now + lockoutMs)
                        onResult(false, "Demasiados intentos. Espera ${lockoutMs / 1000} s.")
                    } else {
                        prefs.setFailedAttempts(attempts)
                        val remaining = MAX_ATTEMPTS - attempts
                        onResult(false, "PIN incorrecto. Te quedan $remaining intento(s).")
                    }
                }
            } finally {
                // SEC-08: borrar el PIN de memoria en cuanto se usó (éxito o error).
                pin.fill(' ')
            }
        }
    }

    /** Verificación simple para reautenticación de acciones sensibles. */
    fun verifyPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(verifyAgainstStored(pin)) }
    }

    // --- Biometría / timeout / bloqueo manual ---

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setBiometricEnabled(enabled) }
    }

    fun setAutoLockMinutes(minutes: Int) {
        viewModelScope.launch { prefs.setAutoLockMinutes(minutes) }
    }

    fun setHideAmounts(hidden: Boolean) {
        viewModelScope.launch { prefs.setHideAmounts(hidden) }
    }

    fun toggleHideAmounts() {
        viewModelScope.launch { prefs.setHideAmounts(!hideAmounts.value) }
    }

    fun lockNow() = AppLockManager.lock()

    fun onBiometricUnlockSucceeded() {
        viewModelScope.launch {
            prefs.setFailedAttempts(0)
            prefs.setLockoutUntil(0L)
            prefs.setLockoutCount(0) // SEC-06: el desbloqueo legítimo reinicia el backoff.
            AppLockManager.unlock()
        }
    }

    // --- Helpers ---

    private suspend fun verifyAgainstStored(pin: CharArray): Boolean {
        val pair = prefs.getHashAndSalt() ?: return false
        val (hash, salt) = pair
        val ok = PinHasher.verify(pin, salt, hash)
        if (ok && PinHasher.needsRehash(hash)) {
            // Re-hash transparente (SEC-07): tras un login válido con un hash legacy (p.ej.
            // PBKDF2 HMAC-SHA1) se reescribe con el algoritmo preferido del dispositivo.
            // No invalida el acceso: ya validamos el PIN antes de regenerar.
            runCatching {
                val newSalt = PinHasher.generateSalt()
                val newHash = PinHasher.hash(pin, newSalt)
                prefs.updatePinHash(newHash, newSalt)
            }
        }
        return ok
    }

    /** Sobrecarga [String] (setup/cambio/reautenticación): copia efímera a [CharArray] y se borra. */
    private suspend fun verifyAgainstStored(pin: String): Boolean {
        val chars = pin.toCharArray()
        return try {
            verifyAgainstStored(chars)
        } finally {
            chars.fill(' ')
        }
    }

    private suspend fun failedAttemptsSnapshot(): Int = prefs.failedAttempts.first()

    private fun validatePin(pin: String): String? = when {
        pin.length < MIN_PIN_LENGTH -> "El PIN debe tener al menos $MIN_PIN_LENGTH dígitos."
        pin.length > MAX_PIN_LENGTH -> "El PIN no puede superar $MAX_PIN_LENGTH dígitos."
        !pin.all { it.isDigit() } -> "El PIN solo puede contener dígitos."
        else -> null
    }

    companion object {
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 8
        const val MAX_ATTEMPTS = 5

        // Backoff exponencial del lockout (SEC-06): base 30 s, duplicándose por cada lockout
        // encadenado hasta un tope de 15 min. count=1 -> 30s, 2 -> 1min, 3 -> 2min, ... (capado).
        const val BASE_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 15 * 60_000L
        const val MAX_BACKOFF_SHIFT = 6 // 30s << 6 = 32 min, ya capado por MAX_LOCKOUT_MS
    }
}

/**
 * Estado del candado de la app evaluado en la raíz antes de mostrar datos financieros.
 *
 * - [LOADING]: aún no se sabe si hay PIN (arranque en frío).
 * - [SETUP]: no hay PIN configurado; se fuerza la configuración inicial obligatoria.
 * - [LOCKED]: hay PIN y la app está bloqueada; se exige desbloqueo.
 * - [UNLOCKED]: acceso concedido.
 */
enum class LockGate { LOADING, SETUP, LOCKED, UNLOCKED }
