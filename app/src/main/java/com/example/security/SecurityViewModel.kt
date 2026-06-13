package com.example.security

import android.app.Application
import android.os.SystemClock
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Expone el estado de seguridad a la UI y centraliza las operaciones de App Lock:
 * configurar/cambiar/quitar PIN, verificar PIN (con control de intentos y lockout),
 * biometría, timeout de bloqueo y bloqueo manual.
 *
 * SEC2-03: TODA verificación de PIN (desbloqueo, cambiar/quitar PIN, reautenticación de
 * acciones sensibles) pasa por [attemptVerify], que comprueba el lockout, cuenta intentos
 * fallidos y aplica el backoff exponencial. Antes, solo el desbloqueo contaba intentos y los
 * diálogos de reautenticación permitían fuerza bruta ilimitada.
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

    /**
     * Configura el PIN por primera vez. Devuelve mensaje de error o null si fue exitoso.
     * SEC2-08: recibe [CharArray] y lo borra al terminar (la copia; el campo de la UI que lo
     * originó es responsabilidad de la pantalla).
     */
    fun setupPin(pin: CharArray, onResult: (error: String?) -> Unit) {
        val validation = validatePin(pin)
        if (validation != null) {
            pin.fill(' ')
            onResult(validation)
            return
        }
        viewModelScope.launch {
            try {
                // SEC2-09: PBKDF2 (120k iteraciones) fuera del hilo Main.
                val (hash, salt) = withContext(Dispatchers.Default) {
                    val salt = PinHasher.generateSalt()
                    PinHasher.hash(pin, salt) to salt
                }
                prefs.setPin(hash, salt)
                AppLockManager.unlock()
                onResult(null)
            } finally {
                pin.fill(' ')
            }
        }
    }

    fun changePin(currentPin: CharArray, newPin: CharArray, onResult: (error: String?) -> Unit) {
        val validation = validatePin(newPin)
        if (validation != null) {
            currentPin.fill(' ')
            newPin.fill(' ')
            onResult(validation)
            return
        }
        viewModelScope.launch {
            try {
                when (val outcome = attemptVerify(currentPin)) {
                    is VerifyOutcome.Success -> {
                        val (hash, salt) = withContext(Dispatchers.Default) {
                            val salt = PinHasher.generateSalt()
                            PinHasher.hash(newPin, salt) to salt
                        }
                        prefs.setPin(hash, salt)
                        onResult(null)
                    }
                    else -> onResult(outcome.errorMessage("El PIN actual es incorrecto."))
                }
            } finally {
                currentPin.fill(' ')
                newPin.fill(' ')
            }
        }
    }

    fun removePin(currentPin: CharArray, onResult: (error: String?) -> Unit) {
        viewModelScope.launch {
            try {
                when (val outcome = attemptVerify(currentPin)) {
                    is VerifyOutcome.Success -> {
                        BiometricGate.deleteKey()
                        prefs.clearPin()
                        // Sin PIN ya no hay bloqueo; aseguramos estado desbloqueado.
                        AppLockManager.unlock()
                        onResult(null)
                    }
                    else -> onResult(outcome.errorMessage("El PIN actual es incorrecto."))
                }
            } finally {
                currentPin.fill(' ')
            }
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
                when (val outcome = attemptVerify(pin)) {
                    is VerifyOutcome.Success -> {
                        AppLockManager.unlock()
                        onResult(true, null)
                    }
                    is VerifyOutcome.WrongPin ->
                        onResult(false, "PIN incorrecto. Te quedan ${outcome.remaining} intento(s).")
                    is VerifyOutcome.LockedOut ->
                        onResult(false, "Demasiados intentos. Espera ${outcome.waitSeconds} s.")
                    is VerifyOutcome.VerifierUnavailable ->
                        onResult(false, VERIFIER_UNAVAILABLE_MSG)
                }
            } finally {
                // SEC-08: borrar el PIN de memoria en cuanto se usó (éxito o error).
                pin.fill(' ')
            }
        }
    }

    /**
     * Reautenticación de acciones sensibles (SEC2-03: ahora con lockout/intentos compartidos
     * con el desbloqueo; ya no es una vía de fuerza bruta ilimitada). NO desbloquea la app.
     * @param onResult (éxito, mensajeError)
     */
    fun verifyPin(pin: CharArray, onResult: (success: Boolean, error: String?) -> Unit) {
        viewModelScope.launch {
            try {
                when (val outcome = attemptVerify(pin)) {
                    is VerifyOutcome.Success -> onResult(true, null)
                    is VerifyOutcome.WrongPin ->
                        onResult(false, "PIN incorrecto. Te quedan ${outcome.remaining} intento(s).")
                    is VerifyOutcome.LockedOut ->
                        onResult(false, "Demasiados intentos. Espera ${outcome.waitSeconds} s.")
                    is VerifyOutcome.VerifierUnavailable -> onResult(false, VERIFIER_UNAVAILABLE_MSG)
                }
            } finally {
                pin.fill(' ')
            }
        }
    }

    // --- Biometría / timeout / bloqueo manual ---

    /**
     * SEC2-04: desbloqueo biométrico con binding criptográfico. El éxito del prompt NO basta:
     * el cipher del CryptoObject debe descifrar (autenticar) el token sellado al activar la
     * biometría. Una biometría enrolada a posteriori invalida la clave del gate
     * ([BiometricGate.CipherResult.Invalidated]) y degrada a PIN desactivando la biometría.
     *
     * Migración: instalaciones que activaron biometría antes del gate no tienen token sellado;
     * en su primer desbloqueo se sella uno nuevo en el mismo prompt (cipher de cifrado), de modo
     * que el flujo es transparente y queda vinculado desde entonces.
     */
    fun requestBiometricUnlock(activity: FragmentActivity, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            val gateData = prefs.biometricGate()
            if (gateData == null) {
                // Sin token sellado (activación previa al gate): autenticar con cipher de
                // CIFRADO y sellar el token ahora.
                if (runCatching { BiometricGate.createKeyIfAbsent() }.isFailure) {
                    onError("Biometría no disponible en este momento.")
                    return@launch
                }
                when (val c = BiometricGate.encryptCipher()) {
                    is BiometricGate.CipherResult.Ready -> BiometricHelper.authenticate(
                        activity = activity,
                        cryptoObject = BiometricPrompt.CryptoObject(c.cipher),
                        onSuccess = { result ->
                            val cipher = result.cryptoObject?.cipher
                            if (cipher != null) {
                                viewModelScope.launch {
                                    val (iv, ct) = BiometricGate.sealToken(cipher)
                                    prefs.setBiometricGate(iv, ct)
                                    onBiometricUnlockSucceeded()
                                }
                            } else {
                                onError("No se pudo vincular la biometría. Usa tu PIN.")
                            }
                        },
                        onError = onError,
                        onFallbackToPin = { /* el teclado de PIN ya está visible */ },
                    )
                    is BiometricGate.CipherResult.Invalidated -> handleGateInvalidated(onError)
                    is BiometricGate.CipherResult.Error ->
                        onError("Biometría no disponible en este momento. Usa tu PIN.")
                }
                return@launch
            }
            when (val c = BiometricGate.decryptCipher(gateData.first)) {
                is BiometricGate.CipherResult.Ready -> BiometricHelper.authenticate(
                    activity = activity,
                    cryptoObject = BiometricPrompt.CryptoObject(c.cipher),
                    onSuccess = { result ->
                        val cipher = result.cryptoObject?.cipher
                        if (cipher != null && BiometricGate.openToken(cipher, gateData.second)) {
                            onBiometricUnlockSucceeded()
                        } else {
                            // Sin cipher o token inválido: NO desbloquear.
                            onError("No se pudo verificar la biometría. Usa tu PIN.")
                        }
                    },
                    onError = onError,
                    onFallbackToPin = { /* el teclado de PIN ya está visible */ },
                )
                is BiometricGate.CipherResult.Invalidated -> handleGateInvalidated(onError)
                is BiometricGate.CipherResult.Error ->
                    onError("Biometría no disponible en este momento. Usa tu PIN.")
            }
        }
    }

    /**
     * SEC2-04: activa la biometría sellando el token del gate en el mismo prompt de confirmación
     * (SEC-04). Solo persiste biometric_enabled=true si el sellado fue exitoso.
     */
    fun enableBiometric(activity: FragmentActivity, onResult: (error: String?) -> Unit) {
        if (runCatching { BiometricGate.createKey() }.isFailure) {
            onResult("No se pudo preparar la clave biométrica.")
            return
        }
        when (val c = BiometricGate.encryptCipher()) {
            is BiometricGate.CipherResult.Ready -> BiometricHelper.authenticate(
                activity = activity,
                title = "Confirmar biometría",
                subtitle = "Autentícate para activar el desbloqueo biométrico",
                cryptoObject = BiometricPrompt.CryptoObject(c.cipher),
                onSuccess = { result ->
                    val cipher = result.cryptoObject?.cipher
                    if (cipher != null) {
                        viewModelScope.launch {
                            val (iv, ct) = BiometricGate.sealToken(cipher)
                            prefs.setBiometricGate(iv, ct)
                            prefs.setBiometricEnabled(true)
                            onResult(null)
                        }
                    } else {
                        onResult("No se pudo vincular la biometría.")
                    }
                },
                onError = { onResult(it) },
                onFallbackToPin = { onResult("Activación cancelada.") },
            )
            else -> onResult("Biometría no disponible en este momento.")
        }
    }

    /** Desactiva la biometría y elimina el gate (requiere PIN confirmado por la UI, SEC-04). */
    fun disableBiometric() {
        viewModelScope.launch {
            prefs.setBiometricEnabled(false)
            prefs.clearBiometricGate()
            BiometricGate.deleteKey()
        }
    }

    private fun handleGateInvalidated(onError: (String) -> Unit) {
        viewModelScope.launch {
            // Biometría re-enrolada: el gate ya no es de confianza. Se exige PIN y una
            // re-activación explícita desde Ajustes.
            prefs.setBiometricEnabled(false)
            prefs.clearBiometricGate()
            BiometricGate.deleteKey()
            onError("La biometría del equipo cambió. Ingresa tu PIN y vuelve a activarla en Ajustes.")
        }
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
            prefs.setLockout(0L, 0L, 0L)
            prefs.setLockoutCount(0) // SEC-06: el desbloqueo legítimo reinicia el backoff.
            AppLockManager.unlock()
        }
    }

    // --- Verificación unificada (SEC2-03) ---

    private sealed interface VerifyOutcome {
        data object Success : VerifyOutcome
        data class WrongPin(val remaining: Int) : VerifyOutcome
        data class LockedOut(val waitSeconds: Long) : VerifyOutcome
        data object VerifierUnavailable : VerifyOutcome

        fun errorMessage(wrongPinMsg: String): String? = when (this) {
            is Success -> null
            is WrongPin -> wrongPinMsg
            is LockedOut -> "Demasiados intentos. Espera $waitSeconds s."
            is VerifierUnavailable -> VERIFIER_UNAVAILABLE_MSG
        }
    }

    /**
     * Única puerta de verificación del PIN: lockout (SEC2-02, doble anclaje), conteo de intentos
     * y backoff (SEC-06) compartidos por TODOS los flujos (SEC2-03). NO toca AppLockManager:
     * desbloquear es decisión del caller (solo verifyPinForUnlock y la biometría desbloquean).
     */
    private suspend fun attemptVerify(pin: CharArray): VerifyOutcome {
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()

        // SEC2-02: el lockout sigue activo si NO expiró en el reloj de pared O, dentro de la
        // misma sesión de arranque (ancla de boot coincidente), tampoco expiró en
        // elapsedRealtime. Adelantar el reloj ya no salta la espera sin reiniciar el equipo
        // (y reiniciar también consume tiempo real del atacante).
        val lockoutWall = prefs.lockoutUntilWall.first()
        val lockoutElapsed = prefs.lockoutUntilElapsed.first()
        val bootAnchor = prefs.lockoutBootAnchor.first()
        val currentAnchor = nowWall - nowElapsed
        val sameBoot = bootAnchor != 0L && abs(currentAnchor - bootAnchor) < BOOT_ANCHOR_TOLERANCE_MS
        val wallRemaining = lockoutWall - nowWall
        val elapsedRemaining = if (sameBoot) lockoutElapsed - nowElapsed else 0L
        val remainingMs = maxOf(wallRemaining, elapsedRemaining)
        if (remainingMs > 0) {
            return VerifyOutcome.LockedOut((remainingMs / 1000) + 1)
        }

        return when (verifyAgainstStored(pin)) {
            StoredVerification.MATCH -> {
                prefs.setFailedAttempts(0)
                prefs.setLockout(0L, 0L, 0L)
                prefs.setLockoutCount(0)
                VerifyOutcome.Success
            }
            StoredVerification.UNAVAILABLE ->
                // SEC2-07: verificador ilegible ≠ PIN incorrecto. No consume intentos.
                VerifyOutcome.VerifierUnavailable
            StoredVerification.NO_MATCH -> {
                val attempts = prefs.failedAttempts.first() + 1
                if (attempts >= MAX_ATTEMPTS) {
                    // Backoff exponencial (SEC-06): el lockout crece con cada ronda fallida
                    // encadenada. lockout_count NO se reinicia aquí, solo tras una verificación
                    // exitosa, de modo que reintentar no devuelve 5 intentos cada 30 s.
                    val count = prefs.lockoutCount.first() + 1
                    val lockoutMs = (BASE_LOCKOUT_MS shl (count - 1).coerceIn(0, MAX_BACKOFF_SHIFT))
                        .coerceAtMost(MAX_LOCKOUT_MS)
                    prefs.setLockoutCount(count)
                    prefs.setFailedAttempts(0)
                    prefs.setLockout(
                        wallUntil = nowWall + lockoutMs,
                        elapsedUntil = nowElapsed + lockoutMs,
                        bootAnchor = currentAnchor,
                    )
                    VerifyOutcome.LockedOut(lockoutMs / 1000)
                } else {
                    prefs.setFailedAttempts(attempts)
                    VerifyOutcome.WrongPin(MAX_ATTEMPTS - attempts)
                }
            }
        }
    }

    // --- Helpers ---

    private enum class StoredVerification { MATCH, NO_MATCH, UNAVAILABLE }

    private suspend fun verifyAgainstStored(pin: CharArray): StoredVerification =
        // SEC2-09: PBKDF2 (~100-500 ms en gama media) y Keystore fuera del hilo Main. withContext
        // vuelve a Main al terminar, así que los onResult de los callers no cambian de hilo.
        withContext(Dispatchers.Default) {
            val verifier = when (val v = prefs.getVerifier()) {
                is SecurityPreferences.VerifierResult.Available -> v
                is SecurityPreferences.VerifierResult.NotSet -> return@withContext StoredVerification.NO_MATCH
                is SecurityPreferences.VerifierResult.Unavailable ->
                    return@withContext StoredVerification.UNAVAILABLE
            }
            // SEC2-07: un verificador corrupto (salt no-hex, etc.) lanza en PinHasher; nunca debe
            // tumbar el proceso — se trata como verificador no disponible.
            val ok = runCatching { PinHasher.verify(pin, verifier.salt, verifier.hash) }
                .getOrElse { return@withContext StoredVerification.UNAVAILABLE }
            if (ok && PinHasher.needsRehash(verifier.hash)) {
                // Re-hash transparente (SEC-07): tras un login válido con un hash legacy (p.ej.
                // PBKDF2 HMAC-SHA1) se reescribe con el algoritmo preferido del dispositivo.
                // No invalida el acceso: ya validamos el PIN antes de regenerar.
                runCatching {
                    val newSalt = PinHasher.generateSalt()
                    val newHash = PinHasher.hash(pin, newSalt)
                    prefs.updatePinHash(newHash, newSalt)
                }
            }
            if (ok) StoredVerification.MATCH else StoredVerification.NO_MATCH
        }

    private fun validatePin(pin: CharArray): String? = when {
        pin.size < MIN_PIN_LENGTH -> "El PIN debe tener al menos $MIN_PIN_LENGTH dígitos."
        pin.size > MAX_PIN_LENGTH -> "El PIN no puede superar $MAX_PIN_LENGTH dígitos."
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

        // SEC2-02: tolerancia de deriva entre relojes para considerar "mismo arranque".
        private const val BOOT_ANCHOR_TOLERANCE_MS = 5_000L

        const val VERIFIER_UNAVAILABLE_MSG =
            "No se pudo leer la configuración de seguridad de este dispositivo. " +
                "Si persiste, deberás restablecer los datos de la app."
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
