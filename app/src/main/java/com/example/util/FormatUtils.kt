package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object FormatUtils {

    /**
     * Sanea valores no finitos. Los cálculos financieros pueden producir NaN o Infinity
     * (p. ej. división por cero); nunca deben mostrarse ni propagarse: se tratan como 0.
     */
    private fun sanitize(value: Double): Double =
        if (value.isNaN() || value.isInfinite()) 0.0 else value

    fun formatCLP(amount: Double): String {
        val symbols = DecimalFormatSymbols(Locale.forLanguageTag("es-CL")).apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }
        val formatter = DecimalFormat("#,##0", symbols)
        val formatted = formatter.format(sanitize(amount))
        return "CLP $formatted"
    }

    fun formatUSD(amount: Double): String {
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ','
            decimalSeparator = '.'
        }
        val formatter = DecimalFormat("#,##0.00", symbols)
        val formatted = formatter.format(sanitize(amount))
        return "USD $formatted"
    }

    // Símbolos fijos en-US para que el separador decimal del porcentaje sea determinista
    // (no depende del locale del dispositivo) y consistente en toda la app.
    private val percentSymbols = DecimalFormatSymbols(Locale.US)

    fun formatPercentage(value: Double): String {
        val formatter = DecimalFormat("0.0%", percentSymbols)
        return formatter.format(sanitize(value))
    }

    fun formatPercentage2Signed(value: Double): String {
        val formatter = DecimalFormat("+0.00%;-0.00%", percentSymbols)
        return formatter.format(sanitize(value))
    }
}
