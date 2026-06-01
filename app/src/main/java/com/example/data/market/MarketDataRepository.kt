package com.example.data.market

/** Resultado de una consulta de precio de mercado. */
sealed class PriceResult {
    data class Success(val ticker: String, val price: Double) : PriceResult()
    data class Failure(val ticker: String, val message: String) : PriceResult()
}

/** Coincidencia de la búsqueda de símbolos (Finnhub /search → result[]). */
data class SymbolMatch(
    val symbol: String,        // se pasa a /quote?symbol= (result[].symbol)
    val description: String,   // nombre legible (result[].description)
    val type: String,          // "Common Stock", "ETF", ... (result[].type)
)

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

    /**
     * Busca activos por nombre o ticker. Devuelve lista vacía si no hay fuente remota o si no hay
     * coincidencias. Nunca lanza: ante error de red devuelve `emptyList()` (offline-first).
     */
    suspend fun searchSymbols(query: String): List<SymbolMatch>
}
