package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun financeDao(): FinanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val DB_NAME = "finance_database"

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    // El artefacto net.zetetic:sqlcipher-android NO auto-carga su librería nativa:
                    // hay que cargarla explícitamente antes de abrir la base de datos.
                    System.loadLibrary("sqlcipher")
                    // Clave de cifrado protegida por el Android Keystore (cifrado en reposo).
                    val key = DatabaseKeyProvider.getOrCreate(context)
                    if (key.isNew) {
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
    }
}
