package com.example.data.notion

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Cliente REAL de la API oficial de Notion (`api.notion.com`, solo HTTPS).
 *
 * Seguridad (convenciones SEC2-06):
 *  - El token viaja SOLO en la cabecera Authorization (nunca en la URL, que acaba en logs).
 *  - No se loguea nada: ni token, ni cuerpos (los payloads llevan montos financieros).
 *  - Errores traducidos a [NotionApiException] con mensajes aptos para mostrarse tal cual.
 *
 * OkHttp en vez de HttpURLConnection porque la API exige PATCH para actualizar páginas y
 * HttpURLConnection no soporta ese método (ver nota en libs.versions.toml).
 */
class NotionApi(
    private val token: String,
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = "https://api.notion.com/v1",
) : NotionClient {

    override suspend fun createPage(databaseId: String, propertiesJson: String): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("parent", JSONObject().put("database_id", databaseId))
                .put("properties", JSONObject(propertiesJson))
                .toString()
            val response = execute("POST", "$baseUrl/pages", body)
            val id = JSONObject(response).optString("id")
            if (id.isEmpty()) {
                throw NotionApiException("Notion no devolvió el id de la página creada.")
            }
            id
        }

    override suspend fun updatePage(pageId: String, propertiesJson: String) {
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("properties", JSONObject(propertiesJson)).toString()
            execute("PATCH", "$baseUrl/pages/$pageId", body)
        }
    }

    private fun execute(method: String, url: String, body: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Notion-Version", NOTION_VERSION)
            .method(method, body.toRequestBody(JSON))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) return text
                throw when (response.code) {
                    401 -> NotionApiException("Token de Notion inválido o revocado. Revísalo en Ajustes.")
                    403, 404 -> NotionApiException(
                        "Notion no encuentra la base (¿compartiste la página 'Finanzas' con tu integración?).",
                    )
                    429 -> NotionApiException("Notion limitó las peticiones; espera unos segundos y reintenta.")
                    in 500..599 -> NotionApiException("Notion tiene problemas (${response.code}); reintenta más tarde.")
                    else -> NotionApiException("Notion rechazó la petición (${response.code}).")
                }
            }
        } catch (e: IOException) {
            throw NotionApiException("Sin conexión con Notion. Revisa tu red e inténtalo de nuevo.", e)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        /** Versión estable y documentada del API; las bases espejo tienen un solo data source. */
        const val NOTION_VERSION = "2022-06-28"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
