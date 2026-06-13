package com.example.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Binding criptográfico del desbloqueo biométrico (SEC-01 paso 2 / SEC2-04).
 *
 * Sin esto, el éxito de BiometricPrompt era un booleano: CUALQUIER huella enrolada en el
 * dispositivo (incluida una registrada a posteriori por un atacante que conozca el credencial
 * del equipo) desbloqueaba la app. Con el gate:
 *
 * - Existe una clave AES dedicada (`fi_bio_gate_key`) en el Android Keystore con
 *   `setUserAuthenticationRequired(true)` + `setInvalidatedByBiometricEnrollment(true)`:
 *   solo es usable tras una autenticación biométrica FUERTE y se INVALIDA si se enrola una
 *   biometría nueva.
 * - Al activar la biometría se cifra un token aleatorio y se persisten IV+ciphertext.
 * - Al desbloquear, el cipher de descifrado viaja dentro del `CryptoObject` del prompt y el
 *   desbloqueo solo se concede si `doFinal` autentica el token (GCM verifica el tag, por lo que
 *   el éxito demuestra posesión de la clave y autenticación real).
 * - Una huella enrolada después de la activación lanza [KeyPermanentlyInvalidatedException] al
 *   inicializar el cipher (ANTES de mostrar el prompt): la app desactiva la biometría y exige
 *   el PIN, cerrando el vector de enrolamiento a posteriori.
 *
 * NOTA de diseño: la clave de la BD (`fi_db_master_key`) sigue SIN auth-binding a propósito
 * (arranque lazy de Room). Este gate NO pretende mitigar hooking en dispositivos comprometidos
 * (ahí el atacante puede hookear AppLockManager directamente); su beneficio es la invalidación
 * por re-enrolamiento.
 */
object BiometricGate {

    private const val ALIAS = "fi_bio_gate_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val TOKEN_BYTES = 32

    sealed interface CipherResult {
        /** Cipher listo para envolver en un CryptoObject. */
        data class Ready(val cipher: Cipher) : CipherResult

        /** La clave fue invalidada (biometría re-enrolada): desactivar biometría y exigir PIN. */
        data object Invalidated : CipherResult

        /** Error transitorio o de aprovisionamiento: degradar a PIN sin tocar el estado. */
        data class Error(val cause: Exception) : CipherResult
    }

    /** (Re)crea la clave del gate. Se invoca al ACTIVAR la biometría. */
    fun createKey() {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            // Invalida la clave si se enrola una huella/rostro NUEVO (default true en API 24+,
            // explícito por claridad). Es el corazón del fix.
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+: autenticación BIOMÉTRICA fuerte por cada uso de la clave.
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            // API 24-29: -1 = autenticación por uso (solo biometría). setUserAuthenticationParameters
            // no existe; este es el equivalente soportado.
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        generator.init(builder.build())
        generator.generateKey()
    }

    /** Crea la clave solo si no existe (migración de instalaciones previas al gate). */
    fun createKeyIfAbsent() {
        val keystore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keystore.getEntry(ALIAS, null) == null) createKey()
    }

    fun deleteKey() {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(ALIAS)
        }
    }

    /** Cipher de cifrado para el flujo de ACTIVACIÓN (sella el token tras autenticar). */
    fun encryptCipher(): CipherResult = initCipher { cipher, key ->
        cipher.init(Cipher.ENCRYPT_MODE, key)
    }

    /** Cipher de descifrado para el DESBLOQUEO. [ivB64] es el IV persistido al activar. */
    fun decryptCipher(ivB64: String): CipherResult = initCipher { cipher, key ->
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
    }

    private inline fun initCipher(init: (Cipher, SecretKey) -> Unit): CipherResult {
        return try {
            val keystore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val key = (keystore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
                ?: return CipherResult.Error(IllegalStateException("Gate key ausente"))
            val cipher = Cipher.getInstance(TRANSFORMATION)
            init(cipher, key)
            CipherResult.Ready(cipher)
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Biometría re-enrolada después de activar: se exige PIN y re-vinculación explícita.
            CipherResult.Invalidated
        } catch (e: Exception) {
            CipherResult.Error(e)
        }
    }

    /**
     * Sella un token aleatorio con el cipher YA AUTENTICADO (devuelto por el CryptoObject).
     * @return (ivB64, ctB64) para persistir.
     */
    fun sealToken(cipher: Cipher): Pair<String, String> {
        val token = ByteArray(TOKEN_BYTES).also { java.security.SecureRandom().nextBytes(it) }
        val ct = cipher.doFinal(token)
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) to
            Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    /**
     * Verifica el token con el cipher YA AUTENTICADO. GCM valida el tag de autenticación:
     * un doFinal exitoso demuestra que la clave es la original y la autenticación fue real.
     */
    fun openToken(cipher: Cipher, ctB64: String): Boolean = runCatching {
        cipher.doFinal(Base64.decode(ctB64, Base64.NO_WRAP))
    }.isSuccess
}
