package com.example.data.market

/**
 * Fuente de precios MANUAL: los precios los ingresa el usuario. No realiza consultas de red.
 *
 * Es la implementación por defecto y garantiza que la app funcione completamente sin API key
 * ni conexión. La actualización automática no está disponible en este modo.
 */
object ManualMarketDataRepository : MarketDataRepository {

    override val canRefresh: Boolean = false

    override val modeLabel: String = "Precios manuales"

    override suspend fun fetchPrice(ticker: String): PriceResult =
        PriceResult.Failure(
            ticker,
            "Modo manual: edita el precio actual a mano o configura una API de mercado.",
        )

    /** Sin fuente remota: la búsqueda no devuelve resultados (la entrada manual sigue disponible). */
    override suspend fun searchSymbols(query: String): List<SymbolMatch> = emptyList()
}
