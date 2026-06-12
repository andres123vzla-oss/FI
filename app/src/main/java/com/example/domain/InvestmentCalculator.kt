package com.example.domain

import com.example.data.entity.InvestmentEntity
import com.example.util.FinanceCalculator

/** Métricas calculadas por acción/activo. */
data class StockMetrics(
    val id: Int,
    val ticker: String,
    val companyName: String,
    val quantity: Double,
    val purchasePrice: Double,
    val currentPrice: Double,
    val invested: Double,
    val currentValue: Double,
    val gainLoss: Double,
    val yieldPercent: Double,
    val weight: Double,
)

/** Métricas agregadas del portafolio. */
data class PortfolioMetrics(
    val totalInvested: Double = 0.0,
    val totalCurrent: Double = 0.0,
    val totalGainLoss: Double = 0.0,
    val totalYieldPercent: Double = 0.0,
    val best: StockMetrics? = null,
    val worst: StockMetrics? = null,
    val stocks: List<StockMetrics> = emptyList(),
)

/**
 * Cálculos de inversión: valor invertido/actual, ganancia/pérdida, rendimiento, peso en el
 * portafolio y mejor/peor activo. Protegido contra división por cero y listas vacías.
 *
 * CALC-02: el portafolio es USD-only. Todos los montos (invertido, actual, ganancia/pérdida y
 * pesos) se agregan asumiendo una única moneda en dólares. El campo [InvestmentEntity.currency]
 * existe en el esquema pero NO se usa en estos cálculos ni en el formateo de la UI (que muestra
 * siempre USD); por eso se mantiene fijado a "USD". No hay conversión de tipo de cambio: registrar
 * un activo en otra moneda produciría totales numéricamente incorrectos. Soportar multimoneda
 * requeriría tasas de cambio y, eventualmente, una migración de esquema, fuera del alcance actual.
 */
object InvestmentCalculator {

    fun analyze(stocks: List<InvestmentEntity>): PortfolioMetrics {
        if (stocks.isEmpty()) return PortfolioMetrics()

        val totalCurrent = FinanceCalculator.sum(stocks.map { it.quantity * it.currentPrice })
        val totalInvested = FinanceCalculator.sum(stocks.map { it.quantity * it.purchasePrice })

        val metrics = stocks.map { s ->
            val invested = s.quantity * s.purchasePrice
            val currentValue = s.quantity * s.currentPrice
            val gainLoss = FinanceCalculator.gainLoss(currentValue, invested)
            StockMetrics(
                id = s.id,
                ticker = s.ticker,
                companyName = s.companyName,
                quantity = s.quantity,
                purchasePrice = s.purchasePrice,
                currentPrice = s.currentPrice,
                invested = invested,
                currentValue = currentValue,
                gainLoss = gainLoss,
                yieldPercent = FinanceCalculator.yieldPercent(gainLoss, invested),
                weight = FinanceCalculator.ratio(currentValue, totalCurrent),
            )
        }

        // Mejor/peor por rendimiento, solo entre activos con costo > 0.
        val ranked = metrics.filter { it.invested > 0.0 }
        return PortfolioMetrics(
            totalInvested = totalInvested,
            totalCurrent = totalCurrent,
            totalGainLoss = FinanceCalculator.gainLoss(totalCurrent, totalInvested),
            totalYieldPercent = FinanceCalculator.yieldPercent(
                FinanceCalculator.gainLoss(totalCurrent, totalInvested),
                totalInvested,
            ),
            best = ranked.maxByOrNull { it.yieldPercent },
            worst = ranked.minByOrNull { it.yieldPercent },
            stocks = metrics.sortedByDescending { it.currentValue },
        )
    }
}
