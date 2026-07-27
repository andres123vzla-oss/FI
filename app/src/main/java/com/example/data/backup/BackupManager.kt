package com.example.data.backup

import com.example.data.repository.FinanceRepository
import com.example.security.BackupCrypto
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Orquestador del RESPALDO exportable/importable (P0 del backlog).
 *
 * Export: foto de las 4 tablas → JSON ([BackupCodec.encodePayload]) → sellado con la passphrase
 * del usuario ([BackupCrypto.seal]) → contenedor JSON → stream de salida (SAF).
 *
 * Import (orden diseñado para NUNCA perder datos, filosofía SEC-09/A1):
 *  1. Leer el archivo con tope de tamaño (patrón readBounded de RemoteMarketDataRepository).
 *  2. Validar el contenedor ([BackupCodec.decodeContainer] → [BackupFormatException]).
 *  3. Descifrar ([BackupCrypto.open] → [javax.crypto.AEADBadTagException] si la passphrase no
 *     corresponde o el archivo fue manipulado).
 *  4. Parsear el payload completo ([BackupCodec.decodePayload]).
 *  5. SOLO si TODO lo anterior fue exitoso: [FinanceRepository.replaceAllData] (transacción
 *     atómica todo-o-nada del DAO). Ante cualquier excepción previa, la BD actual queda intacta.
 *
 * Los errores se propagan tipados; el ViewModel los traduce a mensajes de UI. Aquí no se loguea
 * nada (el payload son datos financieros).
 */
class BackupManager(private val repository: FinanceRepository) {

    /**
     * Exporta todo el contenido al stream. Devuelve la foto exportada (para el mensaje de
     * resultado). El caller es dueño del stream y de la passphrase (y de limpiarla al final).
     */
    suspend fun exportTo(
        out: OutputStream,
        passphrase: CharArray,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): BackupSnapshot = withContext(Dispatchers.IO) {
        val snapshot = BackupSnapshot(
            transactions = repository.allTransactions.first(),
            categories = repository.allCategories.first(),
            budgets = repository.allBudgets.first(),
            investments = repository.allInvestments.first(),
        )
        val sealed = BackupCrypto.seal(BackupCodec.encodePayload(snapshot), passphrase)
        val text = BackupCodec.encodeContainer(sealed, nowEpochMs, CURRENT_SCHEMA_VERSION)
        out.write(text.toByteArray(Charsets.UTF_8))
        out.flush()
        snapshot
    }

    /**
     * Importa el respaldo del stream REEMPLAZANDO todo el contenido actual (el caller ya pidió
     * confirmación + reautenticación). Devuelve la foto importada para el mensaje de resultado.
     *
     * @throws BackupFormatException archivo inválido/dañado/versión desconocida/demasiado grande.
     * @throws javax.crypto.AEADBadTagException passphrase incorrecta o contenido manipulado.
     */
    suspend fun importFrom(
        input: InputStream,
        passphrase: CharArray,
    ): BackupSnapshot = withContext(Dispatchers.IO) {
        val text = readBounded(input)
            ?: throw BackupFormatException("El archivo supera el tamaño máximo de un respaldo.")
        val container = BackupCodec.decodeContainer(text)
        if (container.schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw BackupFormatException(
                "Este respaldo usa un esquema de datos más nuevo (v${container.schemaVersion}). " +
                    "Actualiza la app para importarlo.",
            )
        }
        val snapshot = BackupCodec.decodePayload(BackupCrypto.open(container.sealed, passphrase))
        // Punto de no retorno: todo validado y descifrado; el reemplazo es atómico en el DAO.
        repository.replaceAllData(
            categories = snapshot.categories,
            budgets = snapshot.budgets,
            investments = snapshot.investments,
            transactions = snapshot.transactions,
        )
        snapshot
    }

    /** Lectura acotada (minSdk 24, sin readNBytes): null si supera [MAX_FILE_BYTES]. */
    private fun readBounded(input: InputStream): String? {
        val buf = ByteArray(8192)
        val out = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n == -1) break
            total += n
            if (total > MAX_FILE_BYTES) return null
            out.write(buf, 0, n)
        }
        return out.toString("UTF-8")
    }

    companion object {
        /**
         * Versión del esquema Room que produce este export. DEBE avanzar junto con
         * `AppDatabase.version` (v3 hoy); el import rechaza respaldos de esquemas más nuevos.
         * Los esquemas antiguos (< actual) se aceptan: el formato v1 serializa los campos con
         * defaults en las entidades, por lo que columnas nuevas futuras deberán tener default.
         */
        const val CURRENT_SCHEMA_VERSION = 3

        /** Tope de lectura: muy por encima de cualquier respaldo personal real (~KB). */
        const val MAX_FILE_BYTES = 32 * 1024 * 1024
    }
}
