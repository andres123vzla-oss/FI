package com.example.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.securityDataStore by preferencesDataStore(name = "security_prefs")

/**
 * Persistencia de la configuración de seguridad mediante DataStore.
 *
 * Solo se almacenan el hash y el salt del PIN (nunca el PIN en claro), el flag de biometría,
 * el timeout de bloqueo automático y el control de intentos fallidos / lockout.
 */
class SecurityPreferences(private val context: Context) {

    private object Keys {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val AUTO_LOCK_MINUTES = intPreferencesKey("auto_lock_minutes")
        val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        val LOCKOUT_UNTIL = longPreferencesKey("lockout_until_elapsed")
    }

    companion object {
        const val DEFAULT_AUTO_LOCK_MINUTES = 1
    }

    val isPinSet: Flow<Boolean> =
        context.securityDataStore.data.map { it[Keys.PIN_HASH] != null }

    val biometricEnabled: Flow<Boolean> =
        context.securityDataStore.data.map { it[Keys.BIOMETRIC_ENABLED] ?: false }

    val autoLockMinutes: Flow<Int> =
        context.securityDataStore.data.map { it[Keys.AUTO_LOCK_MINUTES] ?: DEFAULT_AUTO_LOCK_MINUTES }

    val failedAttempts: Flow<Int> =
        context.securityDataStore.data.map { it[Keys.FAILED_ATTEMPTS] ?: 0 }

    val lockoutUntilElapsed: Flow<Long> =
        context.securityDataStore.data.map { it[Keys.LOCKOUT_UNTIL] ?: 0L }

    /** Devuelve (hash, salt) o null si no hay PIN configurado. */
    suspend fun getHashAndSalt(): Pair<String, String>? {
        val prefs = context.securityDataStore.data.first()
        val hash = prefs[Keys.PIN_HASH]
        val salt = prefs[Keys.PIN_SALT]
        return if ((hash != null) && (salt != null)) hash to salt else null
    }

    suspend fun setPin(hash: String, salt: String) {
        context.securityDataStore.edit {
            it[Keys.PIN_HASH] = hash
            it[Keys.PIN_SALT] = salt
            it[Keys.FAILED_ATTEMPTS] = 0
            it[Keys.LOCKOUT_UNTIL] = 0L
        }
    }

    /** Elimina el PIN y, por seguridad, desactiva la biometría asociada. */
    suspend fun clearPin() {
        context.securityDataStore.edit {
            it.remove(Keys.PIN_HASH)
            it.remove(Keys.PIN_SALT)
            it[Keys.BIOMETRIC_ENABLED] = false
            it[Keys.FAILED_ATTEMPTS] = 0
            it[Keys.LOCKOUT_UNTIL] = 0L
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setAutoLockMinutes(minutes: Int) {
        context.securityDataStore.edit { it[Keys.AUTO_LOCK_MINUTES] = minutes }
    }

    suspend fun setFailedAttempts(value: Int) {
        context.securityDataStore.edit { it[Keys.FAILED_ATTEMPTS] = value }
    }

    suspend fun setLockoutUntil(elapsedMillis: Long) {
        context.securityDataStore.edit { it[Keys.LOCKOUT_UNTIL] = elapsedMillis }
    }
}
