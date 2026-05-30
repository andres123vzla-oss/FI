package com.example

import com.example.security.PinHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica el manejo seguro del PIN:
 * - El hash nunca coincide con el PIN en claro.
 * - Salt único por configuración (SecureRandom).
 * - Verificación correcta y rechazo de PIN incorrecto.
 * - Determinismo dado el mismo salt.
 */
class PinHasherTest {

    @Test
    fun `el hash no es el pin en texto plano`() {
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash("1234", salt)
        assertNotEquals("1234", hash)
        assertFalse(hash.contains("1234"))
    }

    @Test
    fun `verify acepta el pin correcto`() {
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash("4821", salt)
        assertTrue(PinHasher.verify("4821", salt, hash))
    }

    @Test
    fun `verify rechaza pin incorrecto`() {
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash("4821", salt)
        assertFalse(PinHasher.verify("0000", salt, hash))
    }

    @Test
    fun `mismo pin y salt produce el mismo hash`() {
        val salt = PinHasher.generateSalt()
        assertEquals(PinHasher.hash("9999", salt), PinHasher.hash("9999", salt))
    }

    @Test
    fun `salts distintos producen hashes distintos para el mismo pin`() {
        val salt1 = PinHasher.generateSalt()
        val salt2 = PinHasher.generateSalt()
        assertNotEquals(salt1, salt2)
        assertNotEquals(PinHasher.hash("1234", salt1), PinHasher.hash("1234", salt2))
    }
}
