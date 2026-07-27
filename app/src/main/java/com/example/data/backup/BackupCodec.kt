package com.example.data.backup

import android.util.Base64
import com.example.data.entity.BudgetEntity
import com.example.data.entity.CategoryEntity
import com.example.data.entity.InvestmentEntity
import com.example.data.entity.RecurringRuleEntity
import com.example.data.entity.TransactionEntity
import com.example.security.BackupCrypto
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * El archivo/contenido de respaldo tiene un formato inválido (no es un respaldo, versión
 * desconocida, JSON dañado, campos faltantes…). El mensaje está pensado para mostrarse tal cual
 * al usuario. NUNCA se responde a esta excepción tocando la base de datos actual.
 */
class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Foto completa de las 5 tablas (con ids y timestamps) para exportar/importar sin pérdida. */
data class BackupSnapshot(
    val transactions: List<TransactionEntity>,
    val categories: List<CategoryEntity>,
    val budgets: List<BudgetEntity>,
    val investments: List<InvestmentEntity>,
    /** P1-1 (formato v2): reglas recurrentes. Vacía en respaldos v1. */
    val recurring: List<RecurringRuleEntity> = emptyList(),
) {
    val isEmpty: Boolean
        get() = transactions.isEmpty() && categories.isEmpty() &&
            budgets.isEmpty() && investments.isEmpty() && recurring.isEmpty()
}

/**
 * Serialización del RESPALDO exportable (formato v1).
 *
 * Estructura del archivo (JSON legible; solo el payload va cifrado):
 * ```json
 * {
 *   "magic": "FISUITE_BACKUP", "formatVersion": 1, "createdAtEpochMs": 0,
 *   "app":    { "schemaVersion": 3 },
 *   "kdf":    { "algo": "PBKDF2WithHmacSHA256", "iterations": 210000, "saltB64": "…" },
 *   "cipher": { "transformation": "AES/GCM/NoPadding", "ivB64": "…" },
 *   "payloadB64": "…AES/GCM del JSON de las 4 tablas…"
 * }
 * ```
 * El header queda en claro a propósito: permite diagnosticar un archivo sin descifrarlo y le da
 * al import los parámetros KDF exactos. No filtra nada sensible (salt/IV no son secretos).
 *
 * Responsabilidad ÚNICA de mapeo (análoga a [com.example.domain.F22Export]): aquí no hay crypto
 * ni acceso a BD. JSON con `org.json` (patrón de `RemoteMarketDataRepository`) y Base64 de
 * `android.util` (patrón de `DatabaseKeyProvider`) — se testea con Robolectric.
 *
 * Parseo ESTRICTO: cualquier campo faltante o malformado termina en [BackupFormatException] con
 * mensaje claro en español; jamás se importa "lo que se pudo".
 */
object BackupCodec {

    const val MAGIC = "FISUITE_BACKUP"

    /**
     * v1: 4 tablas. v2 (P1-1): + clave "recurring" en el payload. El decode acepta ambas: un
     * payload sin "recurring" (respaldos v1 ya exportados) importa con la lista vacía.
     */
    const val FORMAT_VERSION = 2

    /** Contenedor decodificado (header + bloque sellado listo para [BackupCrypto.open]). */
    class Container(
        val formatVersion: Int,
        val createdAtEpochMs: Long,
        val schemaVersion: Int,
        val sealed: BackupCrypto.Sealed,
    )

    // =======================================================================================
    // Payload (las 4 tablas) ↔ JSON
    // =======================================================================================

    /** Serializa la foto completa a JSON UTF-8 (esto es lo que se cifra). */
    fun encodePayload(snapshot: BackupSnapshot): ByteArray {
        val root = JSONObject()
        root.put("transactions", JSONArray().apply {
            snapshot.transactions.forEach { t ->
                put(
                    JSONObject()
                        .put("id", t.id)
                        .put("type", t.type)
                        .put("date", t.date)
                        .put("categoryName", t.categoryName)
                        .put("description", t.description)
                        .put("amount", t.amount)
                        .put("createdAt", t.createdAt)
                        .put("updatedAt", t.updatedAt)
                )
            }
        })
        root.put("categories", JSONArray().apply {
            snapshot.categories.forEach { c ->
                put(
                    JSONObject()
                        .put("id", c.id)
                        .put("name", c.name)
                        .put("type", c.type)
                        .put("colorHex", c.colorHex)
                        .put("iconName", c.iconName)
                        .put("isDefault", c.isDefault)
                )
            }
        })
        root.put("budgets", JSONArray().apply {
            snapshot.budgets.forEach { b ->
                put(
                    JSONObject()
                        .put("id", b.id)
                        .put("categoryName", b.categoryName)
                        .put("month", b.month)
                        .put("year", b.year)
                        .put("plannedAmount", b.plannedAmount)
                )
            }
        })
        root.put("investments", JSONArray().apply {
            snapshot.investments.forEach { i ->
                put(
                    JSONObject()
                        .put("id", i.id)
                        .put("ticker", i.ticker)
                        .put("companyName", i.companyName)
                        .put("quantity", i.quantity)
                        .put("purchasePrice", i.purchasePrice)
                        .put("currentPrice", i.currentPrice)
                        .put("currency", i.currency)
                        .put("createdAt", i.createdAt)
                        .put("updatedAt", i.updatedAt)
                )
            }
        })
        root.put("recurring", JSONArray().apply {
            snapshot.recurring.forEach { r ->
                put(
                    JSONObject()
                        .put("id", r.id)
                        .put("type", r.type)
                        .put("categoryName", r.categoryName)
                        .put("description", r.description)
                        .put("amount", r.amount)
                        .put("dayOfMonth", r.dayOfMonth)
                        .put("active", r.active)
                        .put("lastYear", r.lastYear)
                        .put("lastMonth", r.lastMonth)
                        .put("createdAt", r.createdAt)
                        .put("updatedAt", r.updatedAt)
                )
            }
        })
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    /** Reconstruye la foto desde el JSON descifrado. Estricto: campo faltante → excepción. */
    fun decodePayload(bytes: ByteArray): BackupSnapshot = try {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        val transactions = root.getJSONArray("transactions").mapObjects { o ->
            TransactionEntity(
                id = o.getInt("id"),
                type = o.getString("type"),
                date = o.getString("date"),
                categoryName = o.getString("categoryName"),
                description = o.getString("description"),
                amount = o.getDouble("amount"),
                createdAt = o.getLong("createdAt"),
                updatedAt = o.getLong("updatedAt"),
            )
        }
        val categories = root.getJSONArray("categories").mapObjects { o ->
            CategoryEntity(
                id = o.getInt("id"),
                name = o.getString("name"),
                type = o.getString("type"),
                colorHex = o.getString("colorHex"),
                iconName = o.getString("iconName"),
                isDefault = o.getBoolean("isDefault"),
            )
        }
        val budgets = root.getJSONArray("budgets").mapObjects { o ->
            BudgetEntity(
                id = o.getInt("id"),
                categoryName = o.getString("categoryName"),
                month = o.getInt("month"),
                year = o.getInt("year"),
                plannedAmount = o.getDouble("plannedAmount"),
            )
        }
        val investments = root.getJSONArray("investments").mapObjects { o ->
            InvestmentEntity(
                id = o.getInt("id"),
                ticker = o.getString("ticker"),
                companyName = o.getString("companyName"),
                quantity = o.getDouble("quantity"),
                purchasePrice = o.getDouble("purchasePrice"),
                currentPrice = o.getDouble("currentPrice"),
                currency = o.getString("currency"),
                createdAt = o.getLong("createdAt"),
                updatedAt = o.getLong("updatedAt"),
            )
        }
        // P1-1 (formato v2): clave OPCIONAL — un respaldo v1 (sin "recurring") importa con vacía.
        val recurring = root.optJSONArray("recurring")?.mapObjects { o ->
            RecurringRuleEntity(
                id = o.getInt("id"),
                type = o.getString("type"),
                categoryName = o.getString("categoryName"),
                description = o.getString("description"),
                amount = o.getDouble("amount"),
                dayOfMonth = o.getInt("dayOfMonth"),
                active = o.getBoolean("active"),
                lastYear = o.getInt("lastYear"),
                lastMonth = o.getInt("lastMonth"),
                createdAt = o.getLong("createdAt"),
                updatedAt = o.getLong("updatedAt"),
            )
        } ?: emptyList()
        BackupSnapshot(transactions, categories, budgets, investments, recurring)
    } catch (e: JSONException) {
        throw BackupFormatException("El contenido del respaldo está dañado o incompleto.", e)
    }

    // =======================================================================================
    // Contenedor (header en claro + payload sellado) ↔ texto del archivo
    // =======================================================================================

    /** Serializa el contenedor completo (lo que se escribe al archivo). */
    fun encodeContainer(
        sealed: BackupCrypto.Sealed,
        createdAtEpochMs: Long,
        schemaVersion: Int,
    ): String {
        val root = JSONObject()
            .put("magic", MAGIC)
            .put("formatVersion", FORMAT_VERSION)
            .put("createdAtEpochMs", createdAtEpochMs)
            .put("app", JSONObject().put("schemaVersion", schemaVersion))
            .put(
                "kdf",
                JSONObject()
                    .put("algo", sealed.kdf.algorithm)
                    .put("iterations", sealed.kdf.iterations)
                    .put("saltB64", Base64.encodeToString(sealed.kdf.salt, Base64.NO_WRAP)),
            )
            .put(
                "cipher",
                JSONObject()
                    .put("transformation", BackupCrypto.TRANSFORMATION)
                    .put("ivB64", Base64.encodeToString(sealed.iv, Base64.NO_WRAP)),
            )
            .put("payloadB64", Base64.encodeToString(sealed.ciphertext, Base64.NO_WRAP))
        // Indentado (2): el archivo es diagnosticable a ojo; el tamaño extra es despreciable.
        return root.toString(2)
    }

    /** Valida y decodifica el contenedor. NO descifra (eso es de [BackupCrypto.open]). */
    fun decodeContainer(text: String): Container {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupFormatException("El archivo no es un respaldo válido de esta app.", e)
        }
        try {
            if (root.optString("magic") != MAGIC) {
                throw BackupFormatException("El archivo no es un respaldo válido de esta app.")
            }
            val version = root.getInt("formatVersion")
            if (version > FORMAT_VERSION) {
                throw BackupFormatException(
                    "Este respaldo fue creado por una versión más nueva de la app. Actualízala para importarlo.",
                )
            }
            if (version < 1) {
                throw BackupFormatException("Versión de respaldo inválida: $version.")
            }
            val cipher = root.getJSONObject("cipher")
            val transformation = cipher.getString("transformation")
            if (transformation != BackupCrypto.TRANSFORMATION) {
                throw BackupFormatException("Cifrado no soportado: $transformation.")
            }
            val kdf = root.getJSONObject("kdf")
            val sealed = BackupCrypto.Sealed(
                kdf = BackupCrypto.KdfSpec(
                    algorithm = kdf.getString("algo"),
                    iterations = kdf.getInt("iterations"),
                    salt = decodeB64(kdf.getString("saltB64"), "salt"),
                ),
                iv = decodeB64(cipher.getString("ivB64"), "IV"),
                ciphertext = decodeB64(root.getString("payloadB64"), "payload"),
            )
            return Container(
                formatVersion = version,
                createdAtEpochMs = root.getLong("createdAtEpochMs"),
                schemaVersion = root.getJSONObject("app").getInt("schemaVersion"),
                sealed = sealed,
            )
        } catch (e: JSONException) {
            throw BackupFormatException("Al respaldo le faltan campos obligatorios.", e)
        } catch (e: IllegalArgumentException) {
            // require() de KdfSpec/Sealed (algoritmo desconocido, salt/IV vacíos) o Base64 inválido.
            throw BackupFormatException("Los parámetros del respaldo son inválidos.", e)
        }
    }

    private fun decodeB64(value: String, field: String): ByteArray = try {
        Base64.decode(value, Base64.NO_WRAP)
    } catch (e: IllegalArgumentException) {
        throw BackupFormatException("El campo '$field' del respaldo no es Base64 válido.", e)
    }

    /** Mapea cada JSONObject del array (estricto: un elemento no-objeto lanza JSONException). */
    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { transform(getJSONObject(it)) }
}
