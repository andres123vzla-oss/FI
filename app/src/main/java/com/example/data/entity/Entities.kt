package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "INCOME" or "EXPENSE"
    val date: String, // "YYYY-MM-DD"
    val categoryName: String,
    val description: String,
    val amount: Double,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

// ARQ2-05: índice único en (name, type) — sin él, addCategory permitía dos "Comida" idénticas
// y todo el modelo referencia categorías por nombre. El insert usa ABORT + manejo en el VM.
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name", "type"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // "INCOME" or "EXPENSE"
    val colorHex: String,
    val iconName: String,
    val isDefault: Boolean = false
)

// ARQ2-05: índice único en (categoryName, month, year) — con REPLACE en el insert, esto da la
// semántica upsert deseada (una sola fila de presupuesto por categoría y mes), eliminando las
// filas fantasma que budgetReportFlow ocultaba con firstOrNull.
@Entity(
    tableName = "budgets",
    indices = [Index(value = ["categoryName", "month", "year"], unique = true)],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val categoryName: String, // Links to CategoryEntity.name
    val month: Int, // 1 to 12
    val year: Int,
    val plannedAmount: Double
)

// A3: índice único en ticker como red de seguridad contra duplicados. La fusión/dedupe real
// la realiza FinanceRepository.addOrMergeInvestment (consulta + update ponderado), por lo que en
// el DAO se evita OnConflictStrategy.REPLACE en investments para no destruir posiciones existentes.
@Entity(
    tableName = "investments",
    indices = [Index(value = ["ticker"], unique = true)],
)
data class InvestmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ticker: String,
    val companyName: String,
    val quantity: Double,
    val purchasePrice: Double,
    val currentPrice: Double,
    val currency: String = "USD",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
