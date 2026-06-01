package com.example.data.market

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fuente de precios REMOTA preparada para una API externa (por defecto, formato tipo Finnhub
 * `/quote?symbol=...&token=...`, que devuelve el precio actual en el campo `c`).
 *
 * La API key NUNCA está hardcodeada: se inyecta desde fuera (el provider la lee de
 * `BuildConfig.MARKET_API_KEY`, alimentado por una variable de entorno / `local.properties`).
 * Si la key está vacía, esta clase no debe instanciarse (ver [MarketDataProvider]); la app sigue
 * operando con precios manuales.
 *
 * Usa [HttpURLConnection] para no depender de librerías de red adicionales. Ante cualquier fallo
 * devuelve [PriceResult.Failure] con un mensaje claro y nunca lanza excepción al llamador, de modo
 * que el último precio conocido se conserve.
 */
class RemoteMarketDataRepository(
    private val apiKey: String,
    private val baseUrl: String = "https://finnhub.io/api/v1/quote",
    private val searchUrl: String = "https://finnhub.io/api/v1/search",
    private val timeoutMs: Int = 8000,
) : MarketDataRepository {

    override val canRefresh: Boolean = apiKey.isNotBlank()

    override val modeLabel: String = "API de mercado"

    // Símbolos plausibles (US y prefijados tipo BINANCE:BTCUSDT / OANDA:EUR_USD).
    private val tickerRegex = Regex("^[A-Z0-9.\\-:_]{1,20}$")

    override suspend fun fetchPrice(ticker: String): PriceResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext PriceResult.Failure(ticker, "No hay API key de mercado configurada.")
        }
        val symbol = ticker.trim().uppercase()
        if (symbol.isEmpty() || !tickerRegex.matches(symbol)) {
            return@withContext PriceResult.Failure(ticker, "Ticker no válido para la API.")
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl?symbol=$symbol&token=$apiKey")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return@withContext PriceResult.Failure(symbol, "El servicio respondió $code.")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val price = JSONObject(body).optDouble("c", Double.NaN)
            if (!price.isFinite() || price <= 0.0) {
                return@withContext PriceResult.Failure(symbol, "Sin precio disponible para $symbol.")
            }
            PriceResult.Success(symbol, price)
        } catch (e: Exception) {
            PriceResult.Failure(symbol, "No se pudo actualizar $symbol (sin conexión o error de red).")
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Busca símbolos en Finnhub (`/search?q=...`). Espeja la estructura de [fetchPrice]: mismos
     * timeouts, try/finally y garantía de "nunca lanza" (ante cualquier error devuelve lista vacía).
     * No registra la query, la key ni el cuerpo de la respuesta.
     */
    override suspend fun searchSymbols(query: String): List<SymbolMatch> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()

        var connection: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val url = URL("$searchUrl?q=$encoded&token=$apiKey")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONObject(body).optJSONArray("result") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val symbol = o.optString("symbol").trim()
                    if (symbol.isEmpty()) continue
                    add(
                        SymbolMatch(
                            symbol = symbol,
                            description = o.optString("description").ifBlank { symbol },
                            type = o.optString("type"),
                        )
                    )
                }
            }.take(25)
        } catch (e: Exception) {
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }
}
