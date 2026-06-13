package com.example.data.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.FinanceDao
import com.example.data.entity.BudgetEntity
import com.example.data.entity.CategoryEntity
import com.example.data.entity.InvestmentEntity
import com.example.data.entity.TransactionEntity
import com.example.security.DatabaseKeyProvider
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        InvestmentEntity::class,
    ],
    // C3/A3: version subida a 2 al añadir el índice único en investments.ticker.
    // ARQ2-05: version 3 añade índices únicos en budgets(categoryName,month,year) y
    // categories(name,type), con MIGRATION_2_3 explícita (dedupe + create index).
    // POLÍTICA (A1/SEC-09): todo incremento futuro de version DEBE traer su Migration
    // explícita test-eada, para no perder silenciosamente la base financiera del usuario.
    // ARQ2-07: exportSchema=true + room.schemaLocation para versionar los esquemas JSON
    // y poder testear migraciones con MigrationTestHelper.
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun financeDao(): FinanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val DB_NAME = "finance_database"
        private const val TAG = "AppDatabase"

        /**
         * ARQ2-05: índices únicos para impedir duplicados lógicos.
         * - budgets: una fila por (categoryName, month, year). Se deduplica conservando la fila
         *   de MENOR id (la que muestra la pantalla de Presupuesto vía firstOrNull) antes de
         *   crear el índice, o la migración fallaría con los duplicados existentes.
         * - categories: una fila por (name, type), deduplicada con el mismo criterio.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM budgets WHERE id NOT IN (" +
                        "SELECT MIN(id) FROM budgets GROUP BY categoryName, month, year)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_budgets_categoryName_month_year " +
                        "ON budgets (categoryName, month, year)"
                )
                db.execSQL(
                    "DELETE FROM categories WHERE id NOT IN (" +
                        "SELECT MIN(id) FROM categories GROUP BY name, type)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_categories_name_type " +
                        "ON categories (name, type)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    // El artefacto net.zetetic:sqlcipher-android NO auto-carga su librería nativa:
                    // hay que cargarla explícitamente antes de abrir la base de datos.
                    System.loadLibrary("sqlcipher")
                    // Clave de cifrado protegida por el Android Keystore (cifrado en reposo).
                    // Si el material de clave no está disponible por un fallo TRANSITORIO,
                    // getOrCreate lanza DatabaseKeyUnavailableException SIN tocar nada: preferimos
                    // fallar el arranque (reintentable) antes que perder datos (SEC2-01/ARQ2-01).
                    val key = DatabaseKeyProvider.getOrCreate(context)
                    if (key.wasInvalidated) {
                        // Keystore invalidado de verdad: la BD anterior es irrecuperable con la
                        // passphrase nueva, pero NO se borra en silencio: se preserva como .bak
                        // (SEC-09/A1: el borrado nunca debe ser silencioso).
                        preserveUnreadableDatabase(context)
                    } else if (key.isNew) {
                        // Primera vez con cifrado: una BD en claro previa no se puede abrir cifrada,
                        // así que se elimina. Aceptable: la app arranca vacía por diseño.
                        deleteStalePlaintextDatabase(context)
                    }
                    val factory = SupportOpenHelperFactory(key.passphrase)
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        DB_NAME
                    )
                        .openHelperFactory(factory)
                        .addMigrations(MIGRATION_2_3)
                        // SEC-09/A1: este fallback puede borrar TODA la base financiera ante un
                        // cambio de esquema sin Migration. Se conserva como última red mientras
                        // las migraciones explícitas (addMigrations) cubren los saltos conocidos;
                        // el caso "Keystore invalidado" ya NO pasa por aquí (se preserva .bak).
                        .fallbackToDestructiveMigration(dropAllTables = true)
                        .build()
                    INSTANCE = instance
                    instance
                }
            }
        }

        /** Elimina una base de datos en claro anterior (y sus journals) al migrar a cifrado. */
        private fun deleteStalePlaintextDatabase(context: Context) {
            listOf(DB_NAME, "$DB_NAME-wal", "$DB_NAME-shm", "$DB_NAME-journal").forEach { name ->
                runCatching {
                    val f = context.getDatabasePath(name)
                    if (f.exists()) f.delete()
                }
            }
        }

        /**
         * SEC2-01/ARQ2-01: ante invalidación real del Keystore la BD vieja queda cifrada con una
         * passphrase perdida. Se renombra a `.bak` (no se borra) y se deja constancia en el log;
         * los sidecars -wal/-shm/-journal sí se eliminan (sin la BD principal no sirven y
         * bloquearían la creación de la nueva).
         */
        private fun preserveUnreadableDatabase(context: Context) {
            runCatching {
                val f = context.getDatabasePath(DB_NAME)
                if (f.exists()) {
                    val backup = context.getDatabasePath("$DB_NAME.bak")
                    if (backup.exists()) backup.delete()
                    f.renameTo(backup)
                    Log.w(TAG, "Keystore invalidado: BD anterior preservada como $DB_NAME.bak")
                }
            }
            listOf("$DB_NAME-wal", "$DB_NAME-shm", "$DB_NAME-journal").forEach { name ->
                runCatching {
                    val f = context.getDatabasePath(name)
                    if (f.exists()) f.delete()
                }
            }
        }
    }
}
