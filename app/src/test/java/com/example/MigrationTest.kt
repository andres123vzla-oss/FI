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
    private fun schemaV3(): File = listOf(
        File("schemas/com.example.data.database.AppDatabase/3.json"),
        File("app/schemas/com.example.data.database.AppDatabase/3.json"),
    ).firstOrNull { it.exists() }
        ?: error("No se encontró el esquema exportado 3.json (¿se movió app/schemas?)")

    /** Crea la BD v3 REAL: DDL de entidades e índices + setupQueries (identity hash incluido). */
    private fun createV3Database() {
        val database = JSONObject(schemaV3().readText(Charsets.UTF_8)).getJSONObject("database")
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
            db.version = 3
        } finally {
            db.close()
        }
    }

    @Test
    fun `migracion 3 a 4 conserva los datos y deja recurring_rules utilizable`() = runBlocking {
        createV3Database()

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
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.financeDao()

            // Los datos del usuario sobreviven a la migración.
            val txs = dao.getAllTransactions().first()
            assertEquals(1, txs.size)
            assertEquals(421_000.0, txs.first().amount, 0.001)
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
}
