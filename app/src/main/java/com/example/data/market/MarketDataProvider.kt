package com.example.data.market

/**
 * Selecciona la fuente de precios según la configuración de compilación:
 * - Si hay una `MARKET_API_KEY` con valor ⇒ fuente remota (actualización automática).
 * - Si está vacía o ausente ⇒ fuente manual (la app funciona igual, con precios a mano).
 *
 * La key se inyecta en `BuildConfig` desde `.env` / `.env.example` mediante el plugin de secrets;
 * nunca se hardcodea en el código fuente. Se lee por reflexión para que el módulo compile tanto si
 * el campo existe como si no (cuando no se ha configurado ninguna key).
 */
object MarketDataProvider {

    val repository: MarketDataRepository by lazy {
        val key = readMarketApiKey()
        if (key.isNotBlank()) RemoteMarketDataRepository(apiKey = key) else ManualMarketDataRepository
    }

    /** Lee `BuildConfig.MARKET_API_KEY` sin acoplarse en compilación a su existencia. */
    private fun readMarketApiKey(): String = runCatching {
        val buildConfig = Class.forName("com.example.BuildConfig")
        (buildConfig.getField("MARKET_API_KEY").get(null) as? String).orEmpty()
    }.getOrDefault("")
}
