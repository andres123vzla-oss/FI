package com.example.data.reasoning

import com.example.domain.ReasoningRequest
import com.example.domain.ReasoningResult
import com.example.domain.ReasoningService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Backend de razonamiento sobre **Ollama** (modelo Llama 3 u otro que razone), self-host o local.
 *
 * Habla con `POST {baseUrl}/api/chat` (stream=false). Mantiene el ADN local-first: el modelo puede
 * correr en el dispositivo o servidor del usuario, sin enviar datos financieros a la nube.
 *
 * Reusa el patrón de red de `RemoteMarketDataRepository` (HttpURLConnection + timeouts + lectura
 * acotada). NO se testea en JVM (requiere un Ollama corriendo); la lógica determinista y el fallback
 * viven en `OfflineReasoningService`.
 *
 * Nota de despliegue: con `network_security_config.xml` actual (cleartext deshabilitado, solo CAs del
 * sistema), `baseUrl` DEBE ser HTTPS con CA pública (p. ej. un túnel ngrok/Cloudflare al Ollama),
 * o hay que agregar el host al netsec config.
 */
class OllamaReasoningService(
    private val baseUrl: String,           // p. ej. "https://mi-host:11434"
    private val model: String = "llama3",  // o "llama3.1", "deepseek-r1", "qwen2.5", etc.
    private val timeoutMs: Int = 60_000,
) : ReasoningService {

    override fun isAvailable(): Boolean = baseUrl.isNotBlank()

    override suspend fun reason(request: ReasoningRequest): ReasoningResult = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("model", model)
            put("stream", false)
            put(
                "options",
                JSONObject()
                    .put("temperature", request.temperature)
                    .put("num_predict", request.maxTokens),
            )
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", request.system))
                    .put(JSONObject().put("role", "user").put("content", request.user)),
            )
        }.toString()

        val conn = (URL("$baseUrl/api/chat").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        try {
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext ReasoningResult(
                    text = "No se pudo consultar el modelo (HTTP $code).",
                    model = model,
                    offline = false,
                )
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val content = JSONObject(body).optJSONObject("message")?.optString("content").orEmpty()
            ReasoningResult(
                text = content.ifBlank { "(respuesta vacía del modelo)" },
                model = model,
            )
        } finally {
            conn.disconnect()
        }
    }
}
