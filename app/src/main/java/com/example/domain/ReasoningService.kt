package com.example.domain

/**
 * Capa de RAZONAMIENTO (LLM) de "Rinde".
 *
 * Un modelo que razona (p. ej. **Llama 3 vía Ollama**, self-host o on-device) que EXPLICA en
 * lenguaje claro un cálculo tributario, una conciliación o una rendición. Regla de oro:
 *
 *   El LLM NUNCA calcula ni decide cifras. El número lo produce el motor determinista
 *   ([RentaCalculator]); el LLM solo lo redacta/explica. Así el dato es siempre auditable y el
 *   modelo no puede "alucinar" montos.
 *
 * Interfaz pura para enchufar distintos backends (Ollama/Llama, on-device, nube) y testear con
 * una implementación offline determinista. Coherente con el ADN local-first: el modelo puede
 * correr en el dispositivo o servidor del usuario, sin enviar datos financieros a terceros.
 */
interface ReasoningService {
    fun isAvailable(): Boolean

    /** Suspende porque el backend real hace I/O de red. */
    suspend fun reason(request: ReasoningRequest): ReasoningResult
}

data class ReasoningRequest(
    val system: String,
    val user: String,
    val maxTokens: Int = 512,
    val temperature: Double = 0.2,
)

data class ReasoningResult(
    val text: String,
    val model: String,
    val offline: Boolean = false,
)

/**
 * Construye prompts a partir de resultados YA calculados (datos verificados → el LLM solo redacta).
 * Mantener aquí los prompts permite testearlos sin red.
 */
object RentaExplainer {

    const val SYSTEM: String =
        "Eres un asistente tributario chileno. Explica en español, claro y breve, SIN inventar " +
        "cifras: usa únicamente los números entregados. Cierra recordando que es un borrador para " +
        "revisar con un contador y que no se presenta directamente al SII."

    fun explainPrompt(r: RentaResult): ReasoningRequest {
        val user = buildString {
            append("Explica este resultado de mayor valor para ").append(r.ticker).append(":\n")
            append("- Precio de venta neto: ").append(r.proceeds).append('\n')
            append("- Costo tributario reajustado por IPC: ").append(r.adjustedCost).append('\n')
            append("- Mayor valor (ganancia): ").append(r.capitalGain).append('\n')
            append("- Régimen: ").append(r.regime).append('\n')
            append("- Impuesto estimado: ")
                .append(r.taxEstimate?.toString() ?: "requiere tu tramo de Global Complementario").append('\n')
            append("Nota del cálculo: ").append(r.note).append('\n')
            append("Explica en 3-4 frases qué significa y qué debería hacer la persona.")
        }
        return ReasoningRequest(system = SYSTEM, user = user)
    }
}

/**
 * Implementación OFFLINE determinista (sin red): redacta una explicación con plantilla a partir
 * del prompt. Sirve de fallback cuando no hay modelo disponible y para que la demo y los tests no
 * dependan de la red. No "alucina": solo reformatea los datos entregados.
 */
class OfflineReasoningService : ReasoningService {
    override fun isAvailable(): Boolean = true

    override suspend fun reason(request: ReasoningRequest): ReasoningResult {
        val text = buildString {
            append("Resumen (modo offline, sin IA conectada):\n")
            append(request.user)
            append("\n\nRecuerda: es un borrador para revisar con tu contador; no se presenta directo al SII.")
        }
        return ReasoningResult(text = text, model = "offline-template", offline = true)
    }
}
