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
 * su propio IV, en el formato `enc:iv_b64:ct_b64` (SEC2-07: el prefijo distingue inequívocamente
 * un valor cifrado de uno legado en claro; un valor cifrado que no descifra se reporta como
 * [VerifierResult.Unavailable] en vez de fingir un "PIN incorrecto" eterno).
 */
class SecurityPreferences(private val context: Context) {

    private object Keys {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val AUTO_LOCK_MINUTES = intPreferencesKey("auto_lock_minutes")
        val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")

        // SEC2-02: lockout con DOBLE anclaje. La clave histórica "lockout_until_elapsed" guarda
        // (y siempre guardó) reloj de pared; se mantiene el nombre por compatibilidad con datos
        // persistidos. Se añade un ancla en elapsedRealtime + marcador de arranque para que
        // adelantar el reloj del sistema no salte la espera dentro de la misma sesión de boot.
        val LOCKOUT_UNTIL_WALL = longPreferencesKey("lockout_until_elapsed")
        val LOCKOUT_UNTIL_ELAPSED = longPreferencesKey("lockout_until_elapsed_real")
        val LOCKOUT_BOOT_ANCHOR = longPreferencesKey("lockout_boot_anchor")

        val LOCKOUT_COUNT = intPreferencesKey("lockout_count")
        val HIDE_AMOUNTS = booleanPreferencesKey("hide_amounts")

        // SEC2-04: token del gate biométrico (cifrado con fi_bio_gate_key, NO con la wrap key:
        // su seguridad la da el auth-binding del Keystore, no la envoltura).
        val BIO_GATE_IV = stringPreferencesKey("bio_gate_iv")
        val BIO_GATE_CT = stringPreferencesKey("bio_gate_ct")
    }

    companion object {
        const val DEFAULT_AUTO_LOCK_MINUTES = 1

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val WRAP_KEY_ALIAS = "fi_pin_wrap_key"
        private const val WRAP_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        // SEC2-07: prefijo inequívoco de valores envueltos. Los valores legados (SEC-05 inicial)
        // no lo llevan; unwrap mantiene compatibilidad de tres capas (ver KDoc de unwrap).
        private const val ENC_PREFIX = "enc:"

        // Formatos de verificador en claro pre-SEC-05 (hash "v1:<hex>"/"v2:<hex>"/"<hex>", salt
        // "<hex>"): permiten distinguir "legado en claro" de "ciphertext indescifrable".
        private val LEGACY_VERIFIER_REGEX = Regex("^(v[0-9]+:)?[0-9a-fA-F]+$")
    }

    /** SEC2-07: resultado de la lectura del verificador del PIN. */
    sealed interface VerifierResult {
        /** Verificador disponible. */
        data class Available(val hash: String, val salt: String) : VerifierResult

        /** No hay PIN configurado. */
        data object NotSet : VerifierResult

        /**
         * El verificador existe pero NO se pudo descifrar (wrap key del Keystore invalidada o
         * dato corrupto). NUNCA debe tratarse como "PIN incorrecto" ni consumir intentos: la UI
         * debe explicar el problema y ofrecer restablecer la app.
         */
        data object Unavailable : VerifierResult
    }

    val isPinSet: Flow<Boolean> =
        context.securityDataStore.data.map { it[Keys.PIN_HASH] != null }

    val biometricEnabled: Flow<Boolean> =
        context.securityDataStore.data.map { it[Keys.BIOMETRIC_ENABLED] ?: false }

    val autoLockMinutes: Flow<Int> =
        context.securityDataStore.data.map { it[Keys.AUTO_LOCK_MINUTES] ?: DEFAULT_AUTO_LOCK_MINUTES }

    val failedAttempts: Flow<Int> =
        context.securityDataStore.data.map { it[Keys.FAILED_ATTEMPTS] ?: 0 }

    val lockoutUntilWall: Flow<Long> =
        context.securityDataStore.data.map { it[Keys.LOCKOUT_UNTIL_WALL] ?: 0L }

    val lockoutUntilElapsed: Flow<Long> =
        context.securityDataStore.data.map { it[Keys.LOCKOUT_UNTIL_ELAPSED] ?: 0L }

    /** SEC2-02: `wall - elapsedRealtime` en el momento de fijar el lockout (identifica el boot). */
    val lockoutBootAnchor: Flow<Long> =
        context.securityDataStore.data.map { it[Keys.LOCKOUT_BOOT_ANCHOR] ?: 0L }

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

    /**
     * Lee el verificador del PIN distinguiendo "no hay PIN", "disponible" y "no descifrable"
     * (SEC2-07). Si se leyó con éxito un valor cifrado SIN prefijo (escrituras del SEC-05
     * inicial), se re-envuelve oportunistamente con el prefijo `enc:` para futuras lecturas.
     */
    suspend fun getVerifier(): VerifierResult {
        val prefs = context.securityDataStore.data.first()
        val storedHash = prefs[Keys.PIN_HASH] ?: return VerifierResult.NotSet
        val storedSalt = prefs[Keys.PIN_SALT] ?: return VerifierResult.NotSet

        val hash = unwrap(storedHash) ?: return VerifierResult.Unavailable
        val salt = unwrap(storedSalt) ?: return VerifierResult.Unavailable

        // Re-envoltura oportunista con prefijo para valores cifrados legados sin él.
        if (needsPrefixRewrap(storedHash) || needsPrefixRewrap(storedSalt)) {
            runCatching { updatePinHash(hash, salt) }
        }
        return VerifierResult.Available(hash, salt)
    }

    /**
     * ¿Es un valor cifrado persistido sin el prefijo `enc:`? (Solo se invoca tras un unwrap
     * exitoso: si no es legado en claro y tiene ':' sin prefijo, fue descifrado.)
     */
    private fun needsPrefixRewrap(stored: String): Boolean =
        !stored.startsWith(ENC_PREFIX) && stored.contains(':') &&
            !LEGACY_VERIFIER_REGEX.matches(stored)

    suspend fun setPin(hash: String, salt: String) {
        val wrappedHash = wrap(hash)
        val wrappedSalt = wrap(salt)
        context.securityDataStore.edit {
            it[Keys.PIN_HASH] = wrappedHash
            it[Keys.PIN_SALT] = wrappedSalt
            it[Keys.FAILED_ATTEMPTS] = 0
            it[Keys.LOCKOUT_UNTIL_WALL] = 0L
            it[Keys.LOCKOUT_UNTIL_ELAPSED] = 0L
            it[Keys.LOCKOUT_BOOT_ANCHOR] = 0L
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

    /** Elimina el PIN y, por seguridad, desactiva la biometría asociada (incluido su gate). */
    suspend fun clearPin() {
        context.securityDataStore.edit {
            it.remove(Keys.PIN_HASH)
            it.remove(Keys.PIN_SALT)
            it[Keys.BIOMETRIC_ENABLED] = false
            it.remove(Keys.BIO_GATE_IV)
            it.remove(Keys.BIO_GATE_CT)
            it[Keys.FAILED_ATTEMPTS] = 0
            it[Keys.LOCKOUT_UNTIL_WALL] = 0L
            it[Keys.LOCKOUT_UNTIL_ELAPSED] = 0L
            it[Keys.LOCKOUT_BOOT_ANCHOR] = 0L
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

    /**
     * SEC2-02: persiste el lockout con doble anclaje (reloj de pared + elapsedRealtime + marcador
     * de boot). Pasar todo en 0 para limpiarlo.
     */
    suspend fun setLockout(wallUntil: Long, elapsedUntil: Long, bootAnchor: Long) {
        context.securityDataStore.edit {
            it[Keys.LOCKOUT_UNTIL_WALL] = wallUntil
            it[Keys.LOCKOUT_UNTIL_ELAPSED] = elapsedUntil
            it[Keys.LOCKOUT_BOOT_ANCHOR] = bootAnchor
        }
    }

    /** SEC2-04: persiste el token del gate biométrico (IV + ciphertext, ya cifrado por la gate key). */
    suspend fun setBiometricGate(ivB64: String, ctB64: String) {
        context.securityDataStore.edit {
            it[Keys.BIO_GATE_IV] = ivB64
            it[Keys.BIO_GATE_CT] = ctB64
        }
    }

    suspend fun clearBiometricGate() {
        context.securityDataStore.edit {
            it.remove(Keys.BIO_GATE_IV)
            it.remove(Keys.BIO_GATE_CT)
        }
    }

    /** Devuelve (ivB64, ctB64) del gate biométrico, o null si no hay token sellado. */
    suspend fun biometricGate(): Pair<String, String>? {
        val prefs = context.securityDataStore.data.first()
        val iv = prefs[Keys.BIO_GATE_IV]
        val ct = prefs[Keys.BIO_GATE_CT]
        return if (iv != null && ct != null) iv to ct else null
    }

    suspend fun setLockoutCount(value: Int) {
        context.securityDataStore.edit { it[Keys.LOCKOUT_COUNT] = value }
    }

    suspend fun setHideAmounts(hidden: Boolean) {
        context.securityDataStore.edit { it[Keys.HIDE_AMOUNTS] = hidden }
    }

    // --- Envoltura AES/GCM del verificador del PIN con clave del Android Keystore (SEC-05) ---

    /** Cifra [plain] y devuelve `enc:iv_b64:ct_b64` (prefijo inequívoco, SEC2-07). */
    private fun wrap(plain: String): String {
        val cipher = Cipher.getInstance(WRAP_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey())
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val ctB64 = Base64.encodeToString(ct, Base64.NO_WRAP)
        return "$ENC_PREFIX$ivB64:$ctB64"
    }

    /**
     * Descifra un valor persistido, con compatibilidad de TRES capas (SEC2-07):
     *
     * 1. Prefijo `enc:` (escrituras nuevas): descifrar; si falla → `null` (verificador NO
     *    disponible). Antes, un fallo devolvía el ciphertext como si fuera el hash, dejando al
     *    usuario bloqueado para siempre con un "PIN incorrecto" engañoso (o crasheando en
     *    hexToBytes con el salt corrupto).
     * 2. Sin prefijo pero con ':' (escrituras del SEC-05 inicial): intentar descifrar; si falla,
     *    distinguir: formato de verificador legado en claro ("v1:<hex>"/"<hex>") → devolver tal
     *    cual; cualquier otra cosa es ciphertext indescifrable → `null`.
     * 3. Sin ':' : verificador legado en claro pre-SEC-05 → devolver tal cual.
     *
     * @return el valor en claro, o `null` si existe pero no se puede descifrar.
     */
    private fun unwrap(stored: String): String? {
        if (stored.startsWith(ENC_PREFIX)) {
            return decryptPayload(stored.removePrefix(ENC_PREFIX))
        }
        if (!stored.contains(':')) return stored // legado en claro (capa 3)
        decryptPayload(stored)?.let { return it } // capa 2: valor SEC-05 sin prefijo
        return if (LEGACY_VERIFIER_REGEX.matches(stored)) stored else null
    }

    /** Descifra `iv_b64:ct_b64` o devuelve `null` si no es descifrable. */
    private fun decryptPayload(payload: String): String? {
        val sep = payload.indexOf(':')
        if (sep <= 0) return null
        return runCatching {
            val iv = Base64.decode(payload.substring(0, sep), Base64.NO_WRAP)
            val ct = Base64.decode(payload.substring(sep + 1), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(WRAP_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        }.getOrNull()
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
