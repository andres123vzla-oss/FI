package com.example

import com.example.domain.OfflineReasoningService
import com.example.domain.RentaExplainer
import com.example.domain.RentaRegime
import com.example.domain.RentaResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica la capa de razonamiento (LLM) sin depender de red: el constructor de prompts y el
 * fallback offline determinista. El backend real (Ollama/Llama) se prueba manualmente.
 */
class ReasoningTest {

    private fun sample() = RentaResult(
        ticker = "SQM", proceeds = 143_000.0, adjustedCost = 110_250.0, capitalGain = 32_750.0,
        regime = RentaRegime.ART_107, taxEstimate = 3_275.0, belowThreshold = false,
        matchedQuantity = 100.0, note = "Impuesto único Art. 107 (10%).",
    )

    @Test
    fun `explainPrompt incluye las cifras del resultado y el disclaimer`() {
        val req = RentaExplainer.explainPrompt(sample())
        assertTrue(req.user.contains("SQM"))
        assertTrue(req.user.contains("32750"))
        assertTrue(req.system.contains("contador"))
    }

    @Test
    fun `offline reasoning es determinista y no requiere red`() = runBlocking {
        val svc = OfflineReasoningService()
        assertTrue(svc.isAvailable())
        val res = svc.reason(RentaExplainer.explainPrompt(sample()))
        assertTrue(res.offline)
        assertTrue(res.text.contains("SQM"))
        assertTrue(res.text.contains("contador"))
    }
}
