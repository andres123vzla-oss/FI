package com.example

import com.example.data.backup.BackupCodec
import com.example.data.backup.BackupFormatException
import com.example.data.backup.BackupSnapshot
import com.example.data.entity.BudgetEntity
import com.example.data.entity.CategoryEntity
import com.example.data.entity.InvestmentEntity
import com.example.data.entity.TransactionEntity
import com.example.security.BackupCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests del códec del respaldo (Robolectric por `org.json` + `android.util.Base64`, mismo
 * patrón que SeedDataRegressionTest). Blindan: round-trip campo a campo (incluidos ids y
 * timestamps), y rechazo estricto de archivos que no son respaldos válidos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupCodecTest {

    // Valores con trampas de serialización: comillas, comas, acentos, montos CLP grandes.
    private val snapshot = BackupSnapshot(
        transactions = listOf(
            TransactionEntity(
                id = 7, type = "EXPENSE", date = "2026-05-03",
                categoryName = "Alimentación",
                description = "Harina, jamón y \"queso\"",
                amount = 12_000_000.55, createdAt = 111L, updatedAt = 222L,
            ),
            TransactionEntity(
                id = 9, type = "INCOME", date = "2026-05-01",
                categoryName = "Sueldo", description = "Salario mensual",
                amount = 1_090_094.0, createdAt = 333L, updatedAt = 444L,
            ),
        ),
        categories = listOf(
            CategoryEntity(
                id = 3, name = "Alimentación", type = "EXPENSE",
                colorHex = "#2E7D32", iconName = "Restaurant", isDefault = true,
            ),
        ),
        budgets = listOf(
            BudgetEntity(id = 5, categoryName = "Alimentación", month = 5, year = 2026, plannedAmount = 280_000.0),
        ),
        investments = listOf(
            InvestmentEntity(
                id = 2, ticker = "PLTR", companyName = "Palantir Technologies",
                quantity = 5.085598192, purchasePrice = 146.40, currentPrice = 156.54,
                currency = "USD", createdAt = 555L, updatedAt = 666L,
            ),
        ),
    )

    @Test
    fun `payload round-trip conserva cada campo de las 4 tablas`() {
        val decoded = BackupCodec.decodePayload(BackupCodec.encodePayload(snapshot))
        // data classes → equals estructural campo a campo (ids y timestamps incluidos).
        assertEquals(snapshot.transactions, decoded.transactions)
        assertEquals(snapshot.categories, decoded.categories)
        assertEquals(snapshot.budgets, decoded.budgets)
        assertEquals(snapshot.investments, decoded.investments)
    }

    @Test
    fun `container round-trip conserva header y bloque sellado`() {
        val pass = "passphrase-de-prueba".toCharArray()
        val sealed = BackupCrypto.seal(BackupCodec.encodePayload(snapshot), pass)
        val text = BackupCodec.encodeContainer(sealed, createdAtEpochMs = 1_753_500_000_000L, schemaVersion = 3)

        val container = BackupCodec.decodeContainer(text)
        assertEquals(1, container.formatVersion)
        assertEquals(1_753_500_000_000L, container.createdAtEpochMs)
        assertEquals(3, container.schemaVersion)
        assertEquals(sealed.kdf.algorithm, container.sealed.kdf.algorithm)
        assertEquals(sealed.kdf.iterations, container.sealed.kdf.iterations)
        assertTrue(sealed.kdf.salt.contentEquals(container.sealed.kdf.salt))
        assertTrue(sealed.iv.contentEquals(container.sealed.iv))
        assertTrue(sealed.ciphertext.contentEquals(container.sealed.ciphertext))

        // Y el ciclo completo descifra al snapshot original.
        val reopened = BackupCodec.decodePayload(BackupCrypto.open(container.sealed, pass))
        assertEquals(snapshot.transactions, reopened.transactions)
    }

    @Test
    fun `archivo que no es un respaldo se rechaza`() {
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decodeContainer("esto no es json")
        }
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decodeContainer("""{"magic":"OTRA_APP","formatVersion":1}""")
        }
    }

    @Test
    fun `version de formato mas nueva se rechaza con mensaje de actualizar`() {
        val pass = "passphrase-de-prueba".toCharArray()
        val sealed = BackupCrypto.seal(BackupCodec.encodePayload(snapshot), pass)
        val futuro = BackupCodec.encodeContainer(sealed, 0L, 3)
            .replace("\"formatVersion\": 1", "\"formatVersion\": 99")
        val e = assertThrows(BackupFormatException::class.java) {
            BackupCodec.decodeContainer(futuro)
        }
        assertTrue(e.message!!.contains("versión más nueva"))
    }

    @Test
    fun `payload con campos faltantes se rechaza (parseo estricto)`() {
        val sinAmount = """{"transactions":[{"id":1,"type":"INCOME","date":"2026-01-01",
            "categoryName":"Sueldo","description":"x","createdAt":1,"updatedAt":1}],
            "categories":[],"budgets":[],"investments":[]}"""
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decodePayload(sinAmount.toByteArray(Charsets.UTF_8))
        }
    }

    @Test
    fun `parametros invalidos del header se rechazan`() {
        val pass = "passphrase-de-prueba".toCharArray()
        val sealed = BackupCrypto.seal(BackupCodec.encodePayload(snapshot), pass)
        val algoDesconocido = BackupCodec.encodeContainer(sealed, 0L, 3)
            .replace(sealed.kdf.algorithm, "KDF-INVENTADO")
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decodeContainer(algoDesconocido)
        }
    }
}
