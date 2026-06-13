package com.example.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * El material de clave de la BD no está disponible por un error TRANSITORIO (p. ej. fallo
 * esporádico del daemon del Keystore). NO implica invalidación: el ciphertext persistido se
 * conserva intacto y un reintento posterior puede recuperarlo. Nunca debe responderse a esta
 * excepción destruyendo datos (SEC2-01/ARQ2-01).
 */
class DatabaseKeyUnavailableException(cause: Throwable) :
    RuntimeException("Database key temporarily unavailable", cause)

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

    // SEC2-01: reintentos breves ante fallos transitorios del Keystore antes de propagar.
    private const val DECRYPT_ATTEMPTS = 3
    private const val DECRYPT_RETRY_DELAY_MS = 150L

    /**
     * Passphrase de la BD junto con su origen:
     * - [isNew]: la passphrase se acaba de generar (primera instalación o invalidación real).
     * - [wasInvalidated]: existía un ciphertext previo que resultó criptográficamente
     *   inválido (Keystore invalidado de verdad). La BD anterior es irrecuperable; el caller
     *   debe PRESERVARLA (renombrar, no borrar) y dejar constancia (SEC2-01/ARQ2-01).
     */
    data class Result(
        val passphrase: ByteArray,
        val isNew: Boolean,
        val wasInvalidated: Boolean = false,
    )

    /**
     * Obtiene la passphrase existente o crea una nueva la primera vez.
     *
     * Robustez (SEC2-01/ARQ2-01): solo se trata como invalidación PERMANENTE un
     * [AEADBadTagException] (el ciphertext no corresponde a la clave: clave perdida de verdad).
     * Cualquier otra excepción (fallos esporádicos del daemon del Keystore, dispositivo
     * bloqueado con setUnlockedDeviceRequired, etc.) se reintenta brevemente y, si persiste,
     * se propaga como [DatabaseKeyUnavailableException] SIN tocar el ciphertext persistido:
     * preferimos fallar el arranque (reintentable) antes que destruir la base financiera.
     */
    @Synchronized
    fun getOrCreate(context: Context): Result {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ctB64 = prefs.getString(KEY_CIPHERTEXT, null)
        val ivB64 = prefs.getString(KEY_IV, null)
        val hasCiphertext = ctB64 != null && ivB64 != null

        var invalidated = false
        if (hasCiphertext) {
            val ct = Base64.decode(ctB64, Base64.NO_WRAP)
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            var lastError: Exception? = null
            for (attempt in 1..DECRYPT_ATTEMPTS) {
                try {
                    return Result(decrypt(ct, iv, hasCiphertext = true), isNew = false)
                } catch (e: AEADBadTagException) {
                    // Invalidación criptográfica real: la clave maestra ya no descifra este
                    // ciphertext. Único caso en que se regenera la passphrase.
                    invalidated = true
                    lastError = e
                    break
                } catch (e: DatabaseKeyUnavailableException) {
                    throw e // masterKey() ya clasificó el fallo como transitorio.
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < DECRYPT_ATTEMPTS) {
                        try {
                            Thread.sleep(DECRYPT_RETRY_DELAY_MS * attempt)
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }
                    }
                }
            }
            if (!invalidated) {
                // Error transitorio persistente: NO sobreescribir ct/iv ni abrir la BD con otra
                // clave. El caller puede reintentar más tarde sin pérdida de datos.
                throw DatabaseKeyUnavailableException(lastError ?: IllegalStateException("decrypt failed"))
            }
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val (ct, iv) = encrypt(passphrase)
        // SEC2-05: escritura SÍNCRONA (commit) para material de claves. Con apply(), un kill del
        // proceso antes del flush dejaría una BD cifrada con una passphrase nunca persistida
        // (pérdida garantizada en el siguiente arranque). Si no se pudo persistir, NO abrir la BD.
        val committed = prefs.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ct, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .commit()
        check(committed) { "No se pudo persistir el material de clave de la BD" }
        return Result(passphrase, isNew = true, wasInvalidated = invalidated)
    }

    private fun masterKey(hasCiphertext: Boolean): SecretKey {
        val keystore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keystore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        if (hasCiphertext) {
            // SEC2-01: existe un ciphertext pero el Keystore no entrega la clave. Puede ser un
            // glitch transitorio del daemon; generar una clave nueva bajo el MISMO alias
            // destruiría la anterior y convertiría el glitch en pérdida total e irreversible.
            // Se trata como no-disponible (reintentable) en lugar de regenerar en silencio.
            throw DatabaseKeyUnavailableException(
                IllegalStateException("Keystore no devolvió la clave maestra existente"),
            )
        }
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
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, masterKey(hasCiphertext = false))
        }
        val ct = cipher.doFinal(plain)
        return ct to cipher.iv
    }

    private fun decrypt(ct: ByteArray, iv: ByteArray, hasCiphertext: Boolean): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, masterKey(hasCiphertext), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return cipher.doFinal(ct)
    }
}
