package com.example.domain

/**
 * Modelos del módulo de Renta (Operación Renta — Chile) del proyecto "Rinde".
 *
 * Todo en CLP. Las cifras tributarias (UTA, IPC, tasas, umbrales) llegan SIEMPRE desde un seed
 * parametrizado por año ([RentaParams] + tabla IPC); NUNCA se hardcodean en la lógica. Así un
 * error tributario es un dato corregible, no un bug enterrado en el código.
 */

/** Año-mes simple (sin java.time, para correr en cualquier JVM/Android sin desugaring). */
data class YearMonth(val year: Int, val month: Int) {
    init { require(month in 1..12) { "mes inválido: $month" } }

    /** Mes calendario anterior (para el reajuste IPC, que usa el mes previo a compra/venta). */
    fun prev(): YearMonth = if (month == 1) YearMonth(year - 1, 12) else YearMonth(year, month - 1)

    /** Clave "YYYY-MM" para indexar la tabla IPC. */
    val key: String get() = "%04d-%02d".format(year, month)
}

/** Lote de COMPRA de un instrumento (acción/ETF). Fuente de verdad del costo por lote (FIFO). */
data class BuyLot(
    val ticker: String,
    val date: YearMonth,
    val quantity: Double,
    val unitPrice: Double,
    val commission: Double = 0.0,
)

/** Evento de VENTA (enajenación) a conciliar tributariamente. */
data class SaleEvent(
    val ticker: String,
    val date: YearMonth,
    val quantity: Double,
    val unitPrice: Double,
    val commission: Double = 0.0,
    /** true = acción/ETF con presencia bursátil acogida al Art. 107 LIR (impuesto único). */
    val isArt107: Boolean = false,
)

/** Parámetros tributarios del año, desde seed citado (SII/INE). */
data class RentaParams(
    val utaClp: Double,
    val art107Rate: Double = 0.10,
    val noRentaThresholdUta: Double = 10.0,
) {
    val noRentaThresholdClp: Double get() = noRentaThresholdUta * utaClp
}

enum class RentaRegime { ART_107, INGRESO_NO_RENTA, REGIMEN_GENERAL, SIN_COSTO }

/** Resultado del cálculo de mayor valor de UNA enajenación. */
data class RentaResult(
    val ticker: String,
    val proceeds: Double,        // precio de venta neto de comisión
    val adjustedCost: Double,    // costo tributario reajustado por IPC
    val capitalGain: Double,     // mayor valor (puede ser negativo)
    val regime: RentaRegime,
    val taxEstimate: Double?,    // null = requiere IGC (no estimable sin el tramo del contribuyente)
    val belowThreshold: Boolean,
    val matchedQuantity: Double,
    val note: String = "",
)

/** Item de conciliación contra lo informado por un tercero (corredora / propuesta del SII). */
data class ReconItem(
    val ticker: String,
    val ours: Double,
    val reported: Double,
) {
    val difference: Double get() = ours - reported
    /** Coincide si la diferencia es menor a 1 peso (tolerancia de redondeo). */
    val matches: Boolean get() = kotlin.math.abs(difference) < 1.0
}
