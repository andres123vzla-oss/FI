package com.example.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Criptografía del RESPALDO exportable (P0 del backlog): sella/abre bytes con una clave derivada
 * de una passphrase que ELIGE el usuario.
 *
 * Diferencia clave con [DatabaseKeyProvider]: aquí la clave NO depende del Android Keystore.
 * El respaldo debe poder restaurarse en OTRO dispositivo (o tras un reset de fábrica), por lo que
 * todo lo necesario para descifrarlo viaja en el propio archivo (algoritmo KDF, iteraciones,
 * salt, IV) MENOS la passphrase, que solo conoce el usuario y NUNCA se persiste.
 *
 * Diseño (mismos patrones AES/GCM + SecureRandom de [DatabaseKeyProvider]):
 *  - KDF: PBKDF2 (SHA-256 si la plataforma lo ofrece; SHA-1 como fallback para API 24/25 —
 *    HMAC-SHA1 dentro de PBKDF2 sigue siendo seguro; las colisiones de SHA-1 no aplican a HMAC).
 *    El algoritmo usado queda registrado en [KdfSpec] y el import usa el que diga el archivo.
 *  - Cifrado: AES-256/GCM (tag 128): confidencialidad + integridad. Una passphrase incorrecta o
 *    un archivo manipulado terminan en [javax.crypto.AEADBadTagException] — jamás en datos basura.
 *  - Salt (16 B) e IV (12 B) aleatorios por respaldo ([SecureRandom]); nunca se reutilizan.
 *
 * OBJETO PURO DE JVM: cero imports de Android → testeable con JUnit plano
 * (BackupCryptoTest). La passphrase entra como CharArray y las copias intermedias del material
 * de clave se ponen a cero tras usarse (convención SEC-08).
 */
object BackupCrypto {

    const val KDF_SHA256 = "PBKDF2WithHmacSHA256"
    const val KDF_SHA1 = "PBKDF2WithHmacSHA1"
    const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** Iteraciones por defecto (OWASP 2023+ para PBKDF2-SHA256; registradas en el archivo). */
    const val DEFAULT_ITERATIONS = 210_000

    /** Longitud mínima exigida a la passphrase del respaldo (validada también en la UI). */
    const val MIN_PASSPHRASE_LENGTH = 8

    const val SALT_BYTES = 16
    const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val KEY_BITS = 256

    /** Parámetros de derivación que viajan (en claro) dentro del archivo de respaldo. */
    class KdfSpec(
        val algorithm: String,
        val iterations: Int,
        val salt: ByteArray,
    ) {
        init {
            require(algorithm == KDF_SHA256 || algorithm == KDF_SHA1) {
                "Algoritmo KDF no soportado: $algorithm"
            }
            require(iterations >= 1_000) { "Iteraciones KDF insuficientes: $iterations" }
            require(salt.isNotEmpty()) { "Salt vacío" }
        }
    }

    /** Resultado sellado: KDF + IV + ciphertext (todo lo que va al archivo salvo la passphrase). */
    class Sealed(
        val kdf: KdfSpec,
        val iv: ByteArray,
        val ciphertext: ByteArray,
    ) {
        init {
            require(iv.isNotEmpty()) { "IV vacío" }
            require(ciphertext.isNotEmpty()) { "Ciphertext vacío" }
        }
    }

    /**
     * SHA-256 si el proveedor lo ofrece (API >= 26 y cualquier JVM de escritorio); si no, SHA-1
     * (API 24/25). Decisión por capacidad real del runtime, no por versión de SO, para mantener
     * este objeto puro de JVM.
     */
    fun preferredAlgorithm(): String = try {
        SecretKeyFactory.getInstance(KDF_SHA256)
        KDF_SHA256
    } catch (_: Exception) {
        KDF_SHA1
    }

    /**
     * Sella [plain] con una clave derivada de [passphrase]. Genera salt e IV nuevos.
     * El caller conserva la propiedad de [passphrase] (y debe limpiarla tras el flujo completo).
     */
    fun seal(
        plain: ByteArray,
        passphrase: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        algorithm: String = preferredAlgorithm(),
    ): Sealed {
        require(passphrase.size >= MIN_PASSPHRASE_LENGTH) {
            "La passphrase debe tener al menos $MIN_PASSPHRASE_LENGTH caracteres."
        }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val spec = KdfSpec(algorithm = algorithm, iterations = iterations, salt = salt)
        val key = deriveKey(passphrase, spec)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            // GCM: se genera el IV explícito para que el tamaño (12 B) quede fijado por contrato.
            val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            return Sealed(kdf = spec, iv = iv, ciphertext = cipher.doFinal(plain))
        } finally {
            // La SecretKeySpec copia el material; la copia local se pone a cero (SEC-08).
            zero(key)
        }
    }

    /**
     * Abre un [Sealed] con la [passphrase]. Propaga [javax.crypto.AEADBadTagException] si la
     * passphrase no corresponde o el contenido fue manipulado (el caller la traduce a mensaje).
     */
    fun open(sealed: Sealed, passphrase: CharArray): ByteArray {
        require(passphrase.isNotEmpty()) { "Passphrase vacía." }
        val key = deriveKey(passphrase, sealed.kdf)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, sealed.iv))
            return cipher.doFinal(sealed.ciphertext)
        } finally {
            zero(key)
        }
    }

    /** Deriva la clave AES-256 vía PBKDF2 según [spec]; limpia el material intermedio. */
    private fun deriveKey(passphrase: CharArray, spec: KdfSpec): SecretKeySpec {
        val pbeSpec = PBEKeySpec(passphrase, spec.salt, spec.iterations, KEY_BITS)
        try {
            val factory = SecretKeyFactory.getInstance(spec.algorithm)
            val keyBytes = factory.generateSecret(pbeSpec).encoded
            val key = SecretKeySpec(keyBytes, "AES")
            keyBytes.fill(0) // SecretKeySpec ya copió el material; se borra la copia local.
            return key
        } finally {
            pbeSpec.clearPassword()
        }
    }

    /** Borrado de mejor esfuerzo del material de una SecretKeySpec local (residuo tipo SEC2-08). */
    private fun zero(key: SecretKeySpec) {
        // SecretKeySpec no expone su buffer interno de forma borrable; getEncoded devuelve copia.
        // El borrado real cubierto es el de keyBytes/PBEKeySpec en deriveKey.
        key.encoded?.fill(0)
    }
}
