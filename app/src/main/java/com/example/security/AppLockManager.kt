package com.example.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado de bloqueo de la app en memoria (no se persiste: un reinicio del proceso vuelve a
 * dejar la app bloqueada).
 *
 * Responsable de:
 * - Exponer si la app está bloqueada ([isLocked]).
 * - Bloqueo automático por inactividad al volver de segundo plano según el timeout.
 * - Bloqueo manual.
 *
 * La decisión de mostrar la pantalla de bloqueo combina este estado con "¿hay PIN configurado?"
 * (ver SecurityViewModel): sin PIN configurado nunca se bloquea.
 */
object AppLockManager {

    private val _isLocked = MutableStateFlow(value = true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    /** Marca de tiempo (elapsedRealtime) del último paso a segundo plano. */
    @Volatile
    private var lastBackgroundedAt: Long = 0L

    /** Solo aplicamos auto-lock por inactividad si el usuario ya se autenticó en esta sesión. */
    @Volatile
    private var unlockedThisSession: Boolean = false

    fun lock() {
        _isLocked.value = true
    }

    fun unlock() {
        unlockedThisSession = true
        _isLocked.value = false
    }

    fun onAppBackgrounded() {
        lastBackgroundedAt = SystemClock.elapsedRealtime()
    }

    /**
     * Evalúa al volver a primer plano si corresponde bloquear.
     * @param autoLockMillis 0 = bloquear inmediatamente al pasar a segundo plano.
     */
    fun onAppForegrounded(autoLockMillis: Long) {
        if (!unlockedThisSession) return
        if (_isLocked.value) return
        if (autoLockMillis <= 0L) {
            lock()
            return
        }
        val elapsed = SystemClock.elapsedRealtime() - lastBackgroundedAt
        if (elapsed >= autoLockMillis) {
            lock()
        }
    }


}
