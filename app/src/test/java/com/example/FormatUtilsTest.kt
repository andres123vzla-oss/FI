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

    // CALC-06: fija el redondeo HALF_UP (convención financiera) en lugar del HALF_EVEN por
    // defecto de DecimalFormat. En el límite .5 HALF_UP redondea hacia arriba; HALF_EVEN daría
    // CLP 2 (al par) y USD 2.12.
    @Test
    fun `redondeo en el limite es HALF_UP`() {
        assertEquals("CLP 3", FormatUtils.formatCLP(2.5))   // HALF_EVEN daría "CLP 2"
        assertEquals("CLP 4", FormatUtils.formatCLP(3.5))   // ambos coinciden, refuerza dirección
        assertEquals("USD 2.13", FormatUtils.formatUSD(2.125)) // HALF_EVEN daría "USD 2.12"
    }

    @Test
    fun `CLP compacto usa sufijos M y K sin partir el numero`() {
        assertEquals("CLP 1,09M", FormatUtils.formatCLPCompact(1_090_094.0))
        assertEquals("CLP 748,8K", FormatUtils.formatCLPCompact(748_825.0))
        // Montos pequeños conservan el formato completo.
        assertEquals("CLP 5.000", FormatUtils.formatCLPCompact(5_000.0))
    }

    @Test
    fun `USD compacto usa sufijos con punto decimal`() {
        assertEquals("USD 1.09M", FormatUtils.formatUSDCompact(1_090_000.0))
        assertEquals("USD 35.80K", FormatUtils.formatUSDCompact(35_800.0))
        // Por debajo de 10.000 conserva el formato USD completo.
        assertEquals("USD 3,580.00", FormatUtils.formatUSDCompact(3_580.0))
    }

    @Test
    fun `compacto sanea valores no finitos`() {
        assertEquals("CLP 0", FormatUtils.formatCLPCompact(Double.NaN))
        assertEquals("USD 0.00", FormatUtils.formatUSDCompact(Double.POSITIVE_INFINITY))
    }
}
