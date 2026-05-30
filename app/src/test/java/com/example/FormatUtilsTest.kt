package com.example

import com.example.util.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Validación del formato de moneda y porcentajes, incluyendo casos límite
 * (NaN / Infinity / división por cero saneada a 0).
 */
class FormatUtilsTest {

    @Test
    fun `CLP usa punto de miles y sin decimales`() {
        assertEquals("CLP 1.090.094", FormatUtils.formatCLP(1_090_094.0))
        assertEquals("CLP 748.825", FormatUtils.formatCLP(748_825.0))
        assertEquals("CLP 341.269", FormatUtils.formatCLP(341_269.0))
    }

    @Test
    fun `CLP maneja cero y negativos`() {
        assertEquals("CLP 0", FormatUtils.formatCLP(0.0))
        assertEquals("CLP -5.000", FormatUtils.formatCLP(-5_000.0))
    }

    @Test
    fun `USD usa coma de miles y dos decimales`() {
        assertEquals("USD 1,090.09", FormatUtils.formatUSD(1_090.09))
        assertEquals("USD 0.00", FormatUtils.formatUSD(0.0))
    }

    @Test
    fun `valores no finitos se sanean a cero`() {
        assertEquals("CLP 0", FormatUtils.formatCLP(Double.NaN))
        assertEquals("CLP 0", FormatUtils.formatCLP(Double.POSITIVE_INFINITY))
        assertEquals("USD 0.00", FormatUtils.formatUSD(Double.NEGATIVE_INFINITY))
        assertEquals("0.0%", FormatUtils.formatPercentage(Double.NaN))
    }

    @Test
    fun `porcentaje se formatea correctamente`() {
        assertEquals("50.0%", FormatUtils.formatPercentage(0.5))
        assertEquals("0.0%", FormatUtils.formatPercentage(0.0))
    }
}
