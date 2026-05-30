package com.example.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Derivación y verificación del PIN/contraseña local.
 *
 * Reglas de seguridad:
 * - El PIN nunca se guarda en texto plano: solo se persiste el hash + salt.
 * - Salt único por usuario generado con [SecureRandom].
 * - Estiramiento de clave con PBKDF2 (HMAC-SHA1, disponible desde API 19) en lugar de
 *   un hash rápido sin sal. Se usa SHA1 como HMAC para garantizar compatibilidad con
 *   minSdk 24; el factor de coste lo aporta el número de iteraciones.
 * - Comparación en tiempo constante para evitar timing attacks.
 *
 * Codificación en hexadecimal (puro Kotlin) para que la lógica sea testeable en JVM
 * sin depender de APIs exclusivas de Android.
 */
object PinHasher {

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val ALGORITHM = "PBKDF2WithHmacSHA1"

    /** Genera un salt aleatorio y lo devuelve en hexadecimal. */
    fun generateSalt(): String {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        return salt.toHex()
    }

    /** Calcula el hash del [pin] usando el [saltHex] dado. Devuelve el hash en hexadecimal. */
    fun hash(pin: String, saltHex: String): String {
        val salt = saltHex.hexToBytes()
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return try {
            factory.generateSecret(spec).encoded.toHex()
        } finally {
            // No conservar el valor original en memoria más de lo necesario.
            spec.clearPassword()
        }
    }

    /** Verifica el [pin] contra el [expectedHashHex] usando el [saltHex] persistido. */
    fun verify(pin: String, saltHex: String, expectedHashHex: String): Boolean {
        val computed = hash(pin, saltHex)
        return constantTimeEquals(computed, expectedHashHex)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val ba = a.toByteArray(Charsets.UTF_8)
        val bb = b.toByteArray(Charsets.UTF_8)
        // MessageDigest.isEqual realiza comparación en tiempo constante.
        return MessageDigest.isEqual(ba, bb)
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) sb.append(String.format(Locale.US, "%02x", b))
        return sb.toString()
    }

    private fun String.hexToBytes(): ByteArray {
        require((length % 2) == 0) { "Longitud hexadecimal inválida" }
        val out = ByteArray(length / 2)
        var i = 0
        while (i < length) {
            out[i / 2] = ((Character.digit(this[i], 16) shl 4) +
                Character.digit(this[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }
}
