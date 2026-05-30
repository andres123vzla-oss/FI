package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object FormatUtils {

    fun formatCLP(amount: Double): String {
        val symbols = DecimalFormatSymbols(Locale("es", "CL")).apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }
        val formatter = DecimalFormat("#,##0", symbols)
        val formatted = formatter.format(amount)
        return "CLP $formatted"
    }

    fun formatUSD(amount: Double): String {
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ','
            decimalSeparator = '.'
        }
        val formatter = DecimalFormat("#,##0.00", symbols)
        val formatted = formatter.format(amount)
        return "USD $formatted"
    }

    fun formatPercentage(value: Double): String {
        val formatter = DecimalFormat("0.0%")
        return formatter.format(value)
    }

    fun formatPercentage2Signed(value: Double): String {
        val formatter = DecimalFormat("+0.00%;-0.00%")
        return formatter.format(value)
    }
}
