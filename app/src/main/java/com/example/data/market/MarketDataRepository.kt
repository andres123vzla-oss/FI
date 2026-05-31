package com.example.data.market

/** Resultado de una consulta de precio de mercado. */
sealed class PriceResult {
    data class Success(val ticker: String, val price: Double) : PriceResult()
    data class Failure(val ticker: String, val message: String) : PriceResult()
}

/**
 * Fuente de precios de mercado. Abstracción que permite alternar entre precios manuales
 * (ingresados por el usuario) y una fuente remota (API externa) sin acoplar la UI.
 */
interface MarketDataRepository {
    /** ¿Esta fuente puede actualizar precios automáticamente? */
    val canRefresh: Boolean

    /** Etiqueta corta del modo actual para mostrar al usuario. */
    val modeLabel: String

    /** Obtiene el precio actual del ticker. */
    suspend fun fetchPrice(ticker: String): PriceResult
}
