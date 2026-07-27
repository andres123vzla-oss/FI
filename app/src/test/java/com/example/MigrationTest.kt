package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.RecurringRuleEntity
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Política A1/SEC-09: toda migración de Room es explícita Y testeada.
 *
 * Estrategia (sin MigrationTestHelper: AGP 9 no fusiona assets de host tests): la base v3 se
 * construye ejecutando el DDL EXACTO del esquema exportado `app/schemas/.../3.json` (entidades,
 * índices, setupQueries con el identity hash real — ARQ2-07 versiona esos JSON justo para
 * esto). Luego se abre con Room + MIGRATION_3_4: Room ejecuta la migración y VALIDA el esquema
 * resultante contra el 4.json generado (la misma validación de producción; un CREATE desalineado
 * en la migración falla aquí con "Migration didn't properly handle…").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test-db"

    @Before
    fun clean() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    /** Localiza el esquema exportado tanto si el working dir es `app/` como la raíz del repo. */
    private fun schemaFile(version: Int): File = listOf(
        File("schemas/com.example.data.database.AppDatabase/$version.json"),
        File("app/schemas/com.example.data.database.AppDatabase/$version.json"),
    ).firstOrNull { it.exists() }
        ?: error("No se encontró el esquema exportado $version.json (¿se movió app/schemas?)")

    /** Crea una BD REAL en [version]: DDL de entidades e índices + setupQueries (identity hash). */
    private fun createDatabase(version: Int) {
        val database = JSONObject(schemaFile(version).readText(Charsets.UTF_8)).getJSONObject("database")
        val file = context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            val entities = database.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        db.execSQL(
                            indices.getJSONObject(j).getString("createSql")
                                .replace("\${TABLE_NAME}", table),
                        )
                    }
                }
            }
            val setup = database.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) {
                db.execSQL(setup.getString(i))
            }
            db.version = version
        } finally {
            db.close()
        }
    }

    @Test
    fun `migracion en cadena 3 a 5 conserva los datos y deja recurring_rules utilizable`() = runBlocking {
        createDatabase(3)

        // Datos del usuario en la v3 (SQL crudo: la app v3 real escribía estas columnas).
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE,
        ).use { raw ->
            raw.execSQL(
                "INSERT INTO transactions (type, date, categoryName, description, amount, createdAt, updatedAt) " +
                    "VALUES ('INCOME','2026-05-01','Sueldo','Salario mensual',421000.0,1,1)",
            )
            raw.execSQL(
                "INSERT INTO investments (ticker, companyName, quantity, purchasePrice, currentPrice, currency, createdAt, updatedAt) " +
                    "VALUES ('PLTR','Palantir',5.0,146.4,156.5,'USD',1,1)",
            )
        }

        // Abrir con Room + la migración: Room valida el esquema v4 completo al abrir (identity
        // hash + estructura). SIN fallback destructivo: si la migración fuera incorrecta, este
        // build() lanza en vez de borrar datos.
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.financeDao()

            // Los datos del usuario sobreviven a la migración.
            val txs = dao.getAllTransactions().first()
            assertEquals(1, txs.size)
            assertEquals(421_000.0, txs.first().amount, 0.001)
            assertNull(txs.first().notionPageId) // v5: la columna nueva llega en null
            assertEquals(1, dao.getAllInvestments().first().size)

            // La tabla nueva arranca vacía y es utilizable vía DAO real.
            assertTrue(dao.getAllRecurringOnce().isEmpty())
            dao.insertRecurring(
                RecurringRuleEntity(
                    type = "EXPENSE", categoryName = "Vivienda", description = "Arriendo",
                    amount = 458_000.0, dayOfMonth = 1, lastYear = 2026, lastMonth = 6,
                ),
            )
            assertEquals(1, dao.getAllRecurringOnce().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `migracion 4 a 5 agrega notionPageId a las tablas sin tocar datos`() = runBlocking {
        createDatabase(4)
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE,
        ).use { raw ->
            raw.execSQL(
                "INSERT INTO transactions (type, date, categoryName, description, amount, createdAt, updatedAt) " +
                    "VALUES ('EXPENSE','2026-07-01','Vivienda','Arriendo',458000.0,1,1)",
            )
            raw.execSQL(
                "INSERT INTO recurring_rules (type, categoryName, description, amount, dayOfMonth, active, lastYear, lastMonth, createdAt, updatedAt) " +
                    "VALUES ('EXPENSE','Vivienda','Arriendo',458000.0,1,1,2026,6,1,1)",
            )
        }

        // Abrir con Room a la v5: valida el esquema migrado (identity hash + estructura).
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.financeDao()
            val txs = dao.getAllTransactions().first()
            assertEquals(1, txs.size)
            assertNull(txs.first().notionPageId)
            // La columna es utilizable vía el update PARCIAL real de la sync.
            dao.setTransactionNotionId(txs.first().id, "pg-123")
            assertEquals("pg-123", dao.getAllTransactions().first().first().notionPageId)
            assertEquals(1, dao.getAllRecurringOnce().size)
        } finally {
            db.close()
        }
    }
}
