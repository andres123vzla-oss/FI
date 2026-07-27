package com.example

import com.example.security.BackupCrypto
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros (sin Android) del núcleo criptográfico del respaldo exportable.
 *
 * Garantías que blindan: round-trip exacto, fallo AEAD (no datos basura) ante passphrase
 * incorrecta o contenido manipulado, unicidad de salt/IV entre respaldos y respeto de los
 * parámetros KDF registrados en el archivo.
 */
class BackupCryptoTest {

    private val plain = "{\"transactions\":[{\"amount\":1090094.0}]}".toByteArray(Charsets.UTF_8)
    private val passphrase = "correcto-caballo-batería".toCharArray()

    @Test
    fun `round trip devuelve exactamente los bytes originales`() {
        val sealed = BackupCrypto.seal(plain, passphrase)
        val opened = BackupCrypto.open(sealed, passphrase)
        assertArrayEquals(plain, opened)
    }

    @Test
    fun `passphrase incorrecta falla con AEADBadTagException, nunca datos basura`() {
        val sealed = BackupCrypto.seal(plain, passphrase)
        assertThrows(AEADBadTagException::class.java) {
            BackupCrypto.open(sealed, "otra-passphrase-1".toCharArray())
        }
    }

    @Test
    fun `ciphertext manipulado falla con AEADBadTagException`() {
        val sealed = BackupCrypto.seal(plain, passphrase)
        val tampered = sealed.ciphertext.copyOf().also {
            it[it.size / 2] = (it[it.size / 2].toInt() xor 0x01).toByte()
        }
        val forged = BackupCrypto.Sealed(kdf = sealed.kdf, iv = sealed.iv, ciphertext = tampered)
        assertThrows(AEADBadTagException::class.java) {
            BackupCrypto.open(forged, passphrase)
        }
    }

    @Test
    fun `cada sellado usa salt e IV nuevos`() {
        val a = BackupCrypto.seal(plain, passphrase)
        val b = BackupCrypto.seal(plain, passphrase)
        assertFalse(a.kdf.salt.contentEquals(b.kdf.salt))
        assertFalse(a.iv.contentEquals(b.iv))
        // Mismo plaintext + misma passphrase pero salt/IV distintos → ciphertext distinto.
        assertFalse(a.ciphertext.contentEquals(b.ciphertext))
        assertEquals(BackupCrypto.SALT_BYTES, a.kdf.salt.size)
        assertEquals(BackupCrypto.IV_BYTES, a.iv.size)
    }

    @Test
    fun `open respeta los parametros KDF del archivo (algoritmo e iteraciones no default)`() {
        // Simula un respaldo creado con otros parámetros (p. ej. en un equipo API 24 con SHA-1
        // y menos iteraciones): open debe derivar con LO QUE DIGA el archivo, no con defaults.
        val sealed = BackupCrypto.seal(
            plain,
            passphrase,
            iterations = 50_000,
            algorithm = BackupCrypto.KDF_SHA1,
        )
        assertEquals(BackupCrypto.KDF_SHA1, sealed.kdf.algorithm)
        assertEquals(50_000, sealed.kdf.iterations)
        assertArrayEquals(plain, BackupCrypto.open(sealed, passphrase))
    }

    @Test
    fun `passphrase mas corta que el minimo se rechaza al sellar`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.seal(plain, "corta12".toCharArray()) // 7 < 8
        }
        assertTrue(BackupCrypto.MIN_PASSPHRASE_LENGTH >= 8)
    }
}
