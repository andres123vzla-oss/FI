package com.example.data.notion

/** Error de la API de Notion con mensaje apto para UI (jamás incluye el token ni montos). */
class NotionApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Contrato mínimo contra Notion para la sync one-way (push). Es interfaz para poder testear
 * [NotionSyncManager] con un cliente falso sin red (mismo patrón que MarketDataRepository).
 */
interface NotionClient {
    /** Crea una página en la base indicada y devuelve su pageId. */
    suspend fun createPage(databaseId: String, propertiesJson: String): String

    /** Actualiza las propiedades de una página existente. */
    suspend fun updatePage(pageId: String, propertiesJson: String)
}
