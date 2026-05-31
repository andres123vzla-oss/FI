package com.example.data.market

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
    private val timeoutMs: Int = 8000,
) : MarketDataRepository {

    override val canRefresh: Boolean = apiKey.isNotBlank()

    override val modeLabel: String = "API de mercado"

    override suspend fun fetchPrice(ticker: String): PriceResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext PriceResult.Failure(ticker, "No hay API key de mercado configurada.")
        }
        val symbol = ticker.trim().uppercase()
        if (symbol.isEmpty()) {
            return@withContext PriceResult.Failure(ticker, "Ticker inválido.")
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
}
