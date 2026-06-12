package com.example.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Provee la clave (passphrase) que cifra la base de datos financiera con SQLCipher.
 *
 * Diseño de seguridad:
 * - La passphrase es de 32 bytes aleatorios (SecureRandom). NO deriva del PIN, para no acoplar la
 *   apertura de la BD al desbloqueo ni filtrar información del PIN.
 * - La passphrase se guarda CIFRADA (AES/GCM) con una clave maestra que vive en el
 *   **Android Keystore** (respaldada por hardware/TEE cuando está disponible) y que nunca sale de
 *   él. En SharedPreferences solo queda el texto cifrado + IV, inútiles sin el Keystore.
 * - Si el Keystore se invalida (p. ej. cambio de credenciales del dispositivo) la passphrase no se
 *   puede descifrar: la BD anterior queda inaccesible (comportamiento seguro) y se regenera vacía.
 */
object DatabaseKeyProvider {

    private const val PREFS = "db_key_prefs"
    private const val KEY_CIPHERTEXT = "db_pass_ct"
    private const val KEY_IV = "db_pass_iv"
    private const val KEYSTORE_ALIAS = "fi_db_master_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val PASSPHRASE_BYTES = 32

    /** Passphrase de la BD junto con si se acaba de crear (para migrar BD en claro previa). */
    data class Result(val passphrase: ByteArray, val isNew: Boolean)

    /** Obtiene la passphrase existente o crea una nueva la primera vez. */
    @Synchronized
    fun getOrCreate(context: Context): Result {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ctB64 = prefs.getString(KEY_CIPHERTEXT, null)
        val ivB64 = prefs.getString(KEY_IV, null)

        if (ctB64 != null && ivB64 != null) {
            runCatching {
                val plain = decrypt(
                    Base64.decode(ctB64, Base64.NO_WRAP),
                    Base64.decode(ivB64, Base64.NO_WRAP),
                )
                return Result(plain, isNew = false)
            }
            // No se pudo descifrar (Keystore invalidado): se regenera una passphrase nueva.
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val (ct, iv) = encrypt(passphrase)
        prefs.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ct, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
        return Result(passphrase, isNew = true)
    }

    private fun masterKey(): SecretKey {
        val keystore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keystore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        // Clave nueva (solo instalaciones nuevas): las existentes conservan su clave previa.
        return generateMasterKey(strongBox = true)
    }

    /**
     * Crea la clave maestra del Keystore.
     *
     * Endurecimiento (SEC-02):
     * - setUnlockedDeviceRequired(true) (API >= 28): la clave solo es utilizable con el
     *   dispositivo desbloqueado. La BD solo se abre desde la Activity en foreground (equipo
     *   ya desbloqueado), por lo que no rompe el arranque.
     * - setIsStrongBoxBacked(true) (API >= 28): respaldo en elemento seguro si existe; si no,
     *   StrongBoxUnavailableException provoca un reintento sin StrongBox.
     *
     * NO se usa setUserAuthenticationRequired(true): la BD se abre de forma lazy sin un flujo
     * de re-autenticación ligado y lanzaría UserNotAuthenticatedException rompiendo el arranque.
     */
    private fun generateMasterKey(strongBox: Boolean): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(true)
            if (strongBox) builder.setIsStrongBoxBacked(true)
        }
        generator.init(builder.build())
        return try {
            generator.generateKey()
        } catch (e: Exception) {
            // StrongBoxUnavailableException (API 28+, subtipo de ProviderException): el equipo
            // declara StrongBox pero no puede aprovisionarlo. Se referencia por nombre para no
            // cargar la clase en API < 28 (minSdk 24). Si fue por StrongBox, reintentar sin él.
            if (strongBox &&
                e.javaClass.simpleName == "StrongBoxUnavailableException"
            ) {
                generateMasterKey(strongBox = false)
            } else {
                throw e
            }
        }
    }

    private fun encrypt(plain: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, masterKey()) }
        val ct = cipher.doFinal(plain)
        return ct to cipher.iv
    }

    private fun decrypt(ct: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return cipher.doFinal(ct)
    }
}
