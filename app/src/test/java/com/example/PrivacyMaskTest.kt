package com.example

import com.example.domain.CashFlowAnalyzer
import com.example.ui.components.MASK
import com.example.ui.components.maskAmount
import com.example.util.FinanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifica que ocultar montos es SOLO presentación: enmascara el texto mostrado pero nunca
 * altera los cálculos financieros subyacentes.
 */
class PrivacyMaskTest {

    @Test
    fun `mascara reemplaza el valor solo cuando esta activa`() {
        assertEquals("CLP 1.090.094", maskAmount("CLP 1.090.094", hidden = false))
        assertEquals(MASK, maskAmount("CLP 1.090.094", hidden = true))
    }

    @Test
    fun `ocultar montos no cambia el balance ni la tasa de ahorro`() {
        val income = 1_090_094.0
        val expense = 748_825.0

        // Cálculos idénticos independientemente del estado de privacidad de la UI.
        val balance = FinanceCalculator.balance(income, expense)
        val savings = CashFlowAnalyzer.savingsRate(income, expense)

        assertEquals(341_269.0, balance, 0.0001)
        assertEquals(0.31307, savings, 0.0001)

        // La máscara solo afecta el texto, no el número calculado.
        val shown = maskAmount("CLP 341.269", hidden = true)
        assertEquals(MASK, shown)
        assertEquals(341_269.0, balance, 0.0001)
    }
}
