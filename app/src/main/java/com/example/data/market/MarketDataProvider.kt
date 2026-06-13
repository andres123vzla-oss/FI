package com.example.data.market

import com.example.BuildConfig

/**
 * Selecciona la fuente de precios según la configuración de compilación:
 * - Si hay una `MARKET_API_KEY` con valor ⇒ fuente remota (actualización automática).
 * - Si está vacía o ausente ⇒ fuente manual (la app funciona igual, con precios a mano).
 *
 * La key se declara en `BuildConfig` desde una variable de entorno o propiedad de Gradle
 * (`-PMARKET_API_KEY=...`); nunca se hardcodea en el código fuente. BLD2-01: el campo existe
 * SIEMPRE (se declara incondicionalmente en defaultConfig tras SEC-10), así que se accede de
 * forma directa — la antigua lectura por reflexión obligaba a una keep rule que dejaba la key
 * etiquetada con su nombre de campo en el APK release.
 */
object MarketDataProvider {

    val repository: MarketDataRepository by lazy {
        val key = BuildConfig.MARKET_API_KEY
        if (key.isNotBlank()) RemoteMarketDataRepository(apiKey = key) else ManualMarketDataRepository
    }
}
