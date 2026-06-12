package com.example.util

import java.math.RoundingMode
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

    /**
     * CALC-06: crea un [DecimalFormat] con redondeo HALF_UP, la convención financiera habitual
     * (el default de DecimalFormat es HALF_EVEN / banker's rounding). Centralizar aquí garantiza
     * que TODOS los formatters (moneda, porcentaje y compactos) redondeen igual.
     */
    private fun df(pattern: String, symbols: DecimalFormatSymbols): DecimalFormat =
        DecimalFormat(pattern, symbols).apply { roundingMode = RoundingMode.HALF_UP }

    fun formatCLP(amount: Double): String {
        val symbols = DecimalFormatSymbols(Locale.forLanguageTag("es-CL")).apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }
        val formatter = df("#,##0", symbols)
        val formatted = formatter.format(sanitize(amount))
        return "CLP $formatted"
    }

    fun formatUSD(amount: Double): String {
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ','
            decimalSeparator = '.'
        }
        val formatter = df("#,##0.00", symbols)
        val formatted = formatter.format(sanitize(amount))
        return "USD $formatted"
    }

    // Símbolos fijos en-US para que el separador decimal del porcentaje sea determinista
    // (no depende del locale del dispositivo) y consistente en toda la app.
    private val percentSymbols = DecimalFormatSymbols(Locale.US)

    fun formatPercentage(value: Double): String {
        val formatter = df("0.0%", percentSymbols)
        return formatter.format(sanitize(value))
    }

    fun formatPercentage2Signed(value: Double): String {
        val formatter = df("+0.00%;-0.00%", percentSymbols)
        return formatter.format(sanitize(value))
    }

    // --- Formato compacto para tarjetas pequeñas (evita que los montos se corten) ---

    private val clpCompactSymbols = DecimalFormatSymbols(Locale.US).apply { decimalSeparator = ',' }
    private val usdCompactSymbols = DecimalFormatSymbols(Locale.US).apply { decimalSeparator = '.' }

    /**
     * Formato CLP compacto: `CLP 1,09M`, `CLP 748,8K`. Para montos pequeños usa el formato CLP
     * completo. Pensado para tarjetas KPI estrechas donde el número no debe partirse.
     */
    fun formatCLPCompact(amount: Double): String {
        val v = sanitize(amount)
        val abs = kotlin.math.abs(v)
        val sign = if (v < 0) "-" else ""
        return when {
            abs >= 1_000_000.0 -> "CLP $sign${df("0.##", clpCompactSymbols).format(abs / 1_000_000.0)}M"
            abs >= 10_000.0 -> "CLP $sign${df("0.#", clpCompactSymbols).format(abs / 1_000.0)}K"
            else -> formatCLP(v)
        }
    }

    /**
     * Formato USD compacto: `USD 3.58K`, `USD 1.09M`. Para montos pequeños usa el formato USD
     * completo con dos decimales.
     */
    fun formatUSDCompact(amount: Double): String {
        val v = sanitize(amount)
        val abs = kotlin.math.abs(v)
        val sign = if (v < 0) "-" else ""
        return when {
            abs >= 1_000_000.0 -> "USD $sign${df("0.00", usdCompactSymbols).format(abs / 1_000_000.0)}M"
            abs >= 10_000.0 -> "USD $sign${df("0.00", usdCompactSymbols).format(abs / 1_000.0)}K"
            else -> formatUSD(v)
        }
    }
}
