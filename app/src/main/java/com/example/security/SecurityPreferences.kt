package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.securityDataStore by preferencesDataStore(name = "security_prefs")

/**
 * Persistencia de la configuración de seguridad mediante DataStore.
 *
 * Solo se almacenan el hash y el salt del PIN (nunca el PIN en claro), el flag de biometría,
 * el timeout de bloqueo automático y el control de intentos fallidos / lockout.
 *
 * Seguridad (SEC-05): DataStore Preferences NO cifra su contenido en disco. El hash y el salt
 * del PIN se envuelven con AES/GCM usando una clave del Android Keystore antes de persistirlos,
 * de modo que el fichero `security_prefs` solo contiene texto cifrado + IV (inútiles sin el
 * Keystore). Esto derrota el ataque forense en frío (imagen del disco). Sigue sin guardarse el
 * PIN en claro. No se usa setUserAuthenticationRequired en la clave que envuelve el verificador
 * para evitar el problema huevo-gallina con el desbloqueo por PIN.
 *
 * El verificador se persiste como dos campos cifrados independientes (hash y salt), cada uno con
 * su propio IV, en el formato `iv_b64:ct_b64`.
 */
class SecurityPreferences(private val context: Context) {

    private object Keys {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val AUTO_LOCK_MINUTES = intPreferencesKey("auto_lock_minutes")
        val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        val LOCKOUT_UNTIL = longPreferencesKey("lockout_until_elapsed")
        val LOCKOUT_COUNT = intPreferencesKey("lockout_count")
        val HIDE_AMOUNTS = booleanPreferencesKey("hide_amounts")
    }

    companion object {
        const val DEFAULT_AUTO_LOCK_MINUTES = 1

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val WRAP_KEY_ALIAS = "fi_pin_wrap_key"
        private const val WRAP_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
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

    /**
     * Número de lockouts encadenados (SEC-06). NO se reinicia al disparar un lockout; solo se
     * reinicia tras un desbloqueo exitoso (PIN o biometría). Sirve para el backoff exponencial.
     */
    val lockoutCount: Flow<Int> =
        context.securityDataStore.data.map { it[Keys.LOCKOUT_COUNT] ?: 0 }

    /**
     * Preferencia de privacidad: ocultar montos sensibles en la UI (no afecta los cálculos).
     * Arranca en `true` (oculto por defecto): ningún monto se revela hasta que el usuario lo pide.
     */
    val hideAmounts: Flow<Boolean> =
        context.securityDataStore.data.map { it[Keys.HIDE_AMOUNTS] ?: true }

    /** Devuelve (hash, salt) o null si no hay PIN configurado. */
    suspend fun getHashAndSalt(): Pair<String, String>? {
        val prefs = context.securityDataStore.data.first()
        val hash = prefs[Keys.PIN_HASH]?.let { unwrap(it) }
        val salt = prefs[Keys.PIN_SALT]?.let { unwrap(it) }
        return if ((hash != null) && (salt != null)) hash to salt else null
    }

    suspend fun setPin(hash: String, salt: String) {
        val wrappedHash = wrap(hash)
        val wrappedSalt = wrap(salt)
        context.securityDataStore.edit {
            it[Keys.PIN_HASH] = wrappedHash
            it[Keys.PIN_SALT] = wrappedSalt
            it[Keys.FAILED_ATTEMPTS] = 0
            it[Keys.LOCKOUT_UNTIL] = 0L
            it[Keys.LOCKOUT_COUNT] = 0
        }
    }

    /**
     * Actualiza únicamente el verificador (hash + salt) sin tocar intentos/lockout.
     * Usado para el re-hash transparente (SEC-07) tras un login válido.
     */
    suspend fun updatePinHash(hash: String, salt: String) {
        val wrappedHash = wrap(hash)
        val wrappedSalt = wrap(salt)
        context.securityDataStore.edit {
            it[Keys.PIN_HASH] = wrappedHash
            it[Keys.PIN_SALT] = wrappedSalt
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
            it[Keys.LOCKOUT_COUNT] = 0
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

    suspend fun setLockoutCount(value: Int) {
        context.securityDataStore.edit { it[Keys.LOCKOUT_COUNT] = value }
    }

    suspend fun setHideAmounts(hidden: Boolean) {
        context.securityDataStore.edit { it[Keys.HIDE_AMOUNTS] = hidden }
    }

    // --- Envoltura AES/GCM del verificador del PIN con clave del Android Keystore (SEC-05) ---

    /** Cifra [plain] y devuelve `iv_b64:ct_b64`. */
    private fun wrap(plain: String): String {
        val cipher = Cipher.getInstance(WRAP_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey())
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val ctB64 = Base64.encodeToString(ct, Base64.NO_WRAP)
        return "$ivB64:$ctB64"
    }

    /**
     * Descifra un valor con formato `iv_b64:ct_b64`. Por compatibilidad, si [stored] NO tiene ese
     * formato (instalaciones previas a SEC-05 con hash/salt en claro), se devuelve tal cual para no
     * invalidar el PIN existente; en el próximo [setPin]/[updatePinHash] se reescribirá cifrado.
     */
    private fun unwrap(stored: String): String {
        val sep = stored.indexOf(':')
        if (sep <= 0) return stored
        val ivB64 = stored.substring(0, sep)
        val ctB64 = stored.substring(sep + 1)
        return runCatching {
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val ct = Base64.decode(ctB64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(WRAP_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        }.getOrDefault(stored)
    }

    private fun wrapKey(): SecretKey {
        val keystore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keystore.getEntry(WRAP_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                WRAP_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
