package com.example.security

import android.os.Build
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
 * - Estiramiento de clave con PBKDF2. Se prefiere HMAC-SHA256 cuando el dispositivo lo
 *   soporta (API >= 26); en API 24-25 `PBKDF2WithHmacSHA256` puede no existir y se cae a
 *   HMAC-SHA1 para garantizar compatibilidad con minSdk 24. El factor de coste lo aporta
 *   el número de iteraciones.
 * - Comparación en tiempo constante para evitar timing attacks.
 *
 * Versionado del hash (SEC-07): el hash persistido lleva una etiqueta de algoritmo, p.ej.
 * `v2:<hex>` (SHA256) o `v1:<hex>` (SHA1). Un hash SIN etiqueta (hex puro) se interpreta como
 * legacy v1 (SHA1). [verify] selecciona el factory según la etiqueta para validar hashes
 * antiguos sin invalidarlos; [needsRehash] permite re-hashear transparentemente tras un login
 * exitoso. Así no se bloquea el acceso a usuarios existentes.
 *
 * Codificación en hexadecimal (puro Kotlin) para que la lógica sea testeable en JVM
 * sin depender de APIs exclusivas de Android.
 */
object PinHasher {

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    private const val ALG_SHA1 = "PBKDF2WithHmacSHA1"
    private const val ALG_SHA256 = "PBKDF2WithHmacSHA256"

    // Etiquetas de versión persistidas junto al hash.
    private const val TAG_V1 = "v1" // PBKDF2 HMAC-SHA1 (legacy / fallback API 24-25)
    private const val TAG_V2 = "v2" // PBKDF2 HMAC-SHA256 (preferido, API >= 26)

    /** Algoritmo preferido para nuevos hashes según el SDK disponible. */
    private fun preferredTag(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) TAG_V2 else TAG_V1

    private fun algorithmFor(tag: String): String = when (tag) {
        TAG_V2 -> ALG_SHA256
        else -> ALG_SHA1
    }

    /** Genera un salt aleatorio y lo devuelve en hexadecimal. */
    fun generateSalt(): String {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        return salt.toHex()
    }

    /**
     * Calcula el hash etiquetado del [pin] usando el [saltHex] dado. El resultado tiene la forma
     * `vN:<hex>` con la mejor versión disponible en el dispositivo.
     */
    fun hash(pin: String, saltHex: String): String {
        val tag = preferredTag()
        return "$tag:${rawHash(pin, saltHex, algorithmFor(tag))}"
    }

    /** Cálculo PBKDF2 puro (sin etiqueta) para un algoritmo concreto. Devuelve hex. */
    private fun rawHash(pin: String, saltHex: String, algorithm: String): String {
        val salt = saltHex.hexToBytes()
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(algorithm)
        return try {
            factory.generateSecret(spec).encoded.toHex()
        } finally {
            // No conservar el valor original en memoria más de lo necesario.
            spec.clearPassword()
        }
    }

    /** Separa la etiqueta de versión y el hex de un hash persistido (legacy = sin etiqueta -> v1). */
    private fun parse(stored: String): Pair<String, String> {
        val idx = stored.indexOf(':')
        return if (idx in 1..2 && (stored.startsWith("$TAG_V1:") || stored.startsWith("$TAG_V2:"))) {
            stored.substring(0, idx) to stored.substring(idx + 1)
        } else {
            // Hash heredado sin etiqueta: era PBKDF2 HMAC-SHA1.
            TAG_V1 to stored
        }
    }

    /**
     * ¿El hash almacenado conviene re-hashearlo al algoritmo preferido?
     * Sirve para re-hash transparente tras un login exitoso (no invalida nada).
     */
    fun needsRehash(storedHash: String): Boolean = parse(storedHash).first != preferredTag()

    /** Verifica el [pin] contra el [expectedHashHex] usando el [saltHex] persistido. */
    fun verify(pin: String, saltHex: String, expectedHashHex: String): Boolean {
        val (tag, hex) = parse(expectedHashHex)
        val computed = rawHash(pin, saltHex, algorithmFor(tag))
        return constantTimeEquals(computed, hex)
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
