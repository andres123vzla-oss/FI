package com.example.domain

/**
 * Ensamblado y exportación del borrador del Formulario 22 (Operación Renta — Chile) del módulo
 * "Rinde".
 *
 * Responsabilidad ÚNICA: mapear cifras YA CALCULADAS ([RentaYearSummary] de mayor valor y
 * [DividendYearSummary] de dividendos) a líneas código→monto del F22, y serializarlas a CSV.
 * Este archivo NO calcula impuestos ni reglas tributarias: solo mapea, redondea a peso entero y
 * formatea. Todo el cálculo vive en [RentaCalculator]/[DividendCalculator].
 *
 * OBJETO PURO: cero imports de Android/Room. Solo consume modelos de [RentaModels] y usa
 * `kotlin.*`. Robustez heredada de FinanceCalculator/FormatUtils: los montos pasan por
 * [Clp.round], que sanea NaN/Infinity a 0.0 antes de redondear a peso; nunca se propagan
 * valores no finitos al CSV.
 *
 * SUPUESTO/DISCLAIMER (memoria del proyecto): los códigos del F22 son TENTATIVOS. La asociación
 * del Art. 107 (Línea 8) al código y los códigos de dividendos NACIONALES NO están confirmados
 * en fuente oficial. Por eso [F22Codes] es un parámetro inyectable (pueden venir del seed por
 * año tributario) y los no confirmados llevan el prefijo "VERIFICAR-". El CSV es un borrador de
 * apoyo para conciliar con el contador, NO un archivo de carga al SII.
 */

/**
 * Códigos del Formulario 22 parametrizados.
 *
 * Los defaults son TENTATIVOS y trazables: los confirmados por la memoria del proyecto van con su
 * valor; los NO confirmados llevan prefijo "VERIFICAR-" para que salten a la vista en el borrador
 * y obliguen a la revisión con el contador. Inyectar otra instancia (p. ej. desde el seed del AT)
 * sustituye estos valores sin tocar la lógica de mapeo.
 */
data class F22Codes(
    // --- Recuadro N°4, régimen general percibido (memoria confirma 1067..1070) ---
    val codNumAcciones: String = "1067",
    val codPrecioEnajenacion: String = "1068",
    val codCostoActualizado: String = "1069",
    val codMayorValor: String = "1070",
    // --- Art. 107, impuesto único 10% (VERIFICAR contra instructivo Línea 8 SII) ---
    val codArt107MayorValor: String = "1043",
    // --- Dividendos extranjeros (VERIFICAR por año tributario) ---
    val codRentaExtranjeraIgc: String = "1104",
    val codIncrementoExterior: String = "748",
    val codCreditoExterior: String = "746",
    // --- Dividendos nacionales (NO confirmado en fuente oficial) ---
    val codDividendoNacionalBase: String = "VERIFICAR-DIV-NAC-BASE",
    val codCreditoIdpc: String = "VERIFICAR-DIV-NAC-CRED",
)

object F22Export {

    /** Disclaimer reexportado para conveniencia. */
    const val DISCLAIMER: String = RentaDisclaimer.TEXT

    // ===========================================================================================
    // Mapeo de mayor valor (acciones/ETF) → líneas F22
    // ===========================================================================================

    /**
     * Mapea el resumen ANUAL de mayor valor a líneas F22.
     *
     * Solo emite líneas con monto distinto de cero (tras [Clp.round]), para no ensuciar el
     * borrador con códigos en cero:
     *  - Art. 107: el mayor valor neto del año (solo si > 0; las pérdidas no generan base de
     *    impuesto único). Trazabilidad "Art.107 mayor valor".
     *  - Régimen general: la BASE AFECTA a IGC ([generalTaxableBase]), que el motor ya dejó en 0
     *    si el neto del año quedó bajo el umbral de 10 UTA. Trazabilidad "Régimen general mayor
     *    valor (afecto IGC)".
     *
     * NOTA: no se desglosan los códigos 1067/1068/1069 (n° acciones / precio / costo) porque el
     * resumen anual agrega varias ventas; quedan disponibles en [F22Codes] para un mapeo por venta
     * si se requiere. Aquí se prioriza la cifra afecta, que es la que concilia con el F22.
     */
    fun fromRenta(summary: RentaYearSummary, codes: F22Codes = F22Codes()): List<F22Line> {
        val lines = mutableListOf<F22Line>()

        val art107 = Clp.round(summary.art107NetGain)
        if (art107 > 0.0) {
            lines += F22Line(
                code = codes.codArt107MayorValor,
                label = "Mayor valor Art. 107 (impuesto único 10%)",
                amountClp = art107,
                source = "Art.107 mayor valor",
            )
        }

        val general = Clp.round(summary.generalTaxableBase)
        if (general > 0.0) {
            lines += F22Line(
                code = codes.codMayorValor,
                label = "Mayor valor régimen general afecto a IGC",
                amountClp = general,
                source = "Régimen general mayor valor (afecto IGC)",
            )
        }

        return lines
    }

    // ===========================================================================================
    // Mapeo de dividendos → líneas F22
    // ===========================================================================================

    /**
     * Mapea el resumen ANUAL de dividendos a líneas F22.
     *
     * Emite (omitiendo las que queden en cero tras [Clp.round]):
     *  - Base afecta a IGC total (nacional + extranjero) → [F22Codes.codRentaExtranjeraIgc] como
     *    código tentativo de renta afecta. Trazabilidad "Dividendos base afecta IGC".
     *  - Incremento por impuestos del exterior (gross-up Art. 41 A) — aproximado como el crédito
     *    extranjero ya topeado, que es el incremento imputable. Trazabilidad "Dividendos
     *    incremento exterior".
     *  - Crédito IDPC nacional total → [F22Codes.codCreditoIdpc]. Trazabilidad "Dividendos crédito
     *    IDPC nacional".
     *  - Crédito por impuestos del exterior (Art. 41 A, con tope global aplicado) →
     *    [F22Codes.codCreditoExterior]. Trazabilidad "Dividendos crédito exterior".
     *
     * La restitución 35% (14A sin convenio) NO se mapea a una línea de crédito: es mayor impuesto,
     * no un código de ingreso, y su tratamiento exacto en el F22 depende del recuadro del régimen;
     * queda fuera del borrador por no estar confirmado.
     */
    fun fromDividends(summary: DividendYearSummary, codes: F22Codes = F22Codes()): List<F22Line> {
        val lines = mutableListOf<F22Line>()

        val base = Clp.round(summary.totalTaxableBaseClp)
        if (base > 0.0) {
            lines += F22Line(
                code = codes.codRentaExtranjeraIgc,
                label = "Dividendos: base afecta a IGC (nacional + extranjero)",
                amountClp = base,
                source = "Dividendos base afecta IGC",
            )
        }

        val foreignCredit = Clp.round(summary.totalForeignCreditClp)
        if (foreignCredit > 0.0) {
            lines += F22Line(
                code = codes.codIncrementoExterior,
                label = "Dividendos: incremento por impuesto exterior (Art. 41 A)",
                amountClp = foreignCredit,
                source = "Dividendos incremento exterior",
            )
        }

        val idpcCredit = Clp.round(summary.totalIdpcCreditClp)
        if (idpcCredit > 0.0) {
            lines += F22Line(
                code = codes.codCreditoIdpc,
                label = "Dividendos: crédito IDPC nacional",
                amountClp = idpcCredit,
                source = "Dividendos crédito IDPC nacional",
            )
        }

        if (foreignCredit > 0.0) {
            lines += F22Line(
                code = codes.codCreditoExterior,
                label = "Dividendos: crédito por impuesto exterior (Art. 41 A, tope global)",
                amountClp = foreignCredit,
                source = "Dividendos crédito exterior",
            )
        }

        return lines
    }

    // ===========================================================================================
    // Borrador combinado
    // ===========================================================================================

    /**
     * Borrador combinado (mayor valor + dividendos). Cualquiera de los dos resúmenes puede ser
     * `null` (no hay datos de ese origen). Con AMBOS `null` devuelve un borrador vacío. El orden
     * de las líneas es DETERMINISTA: primero las de renta, luego las de dividendos, en el orden de
     * emisión de [fromRenta]/[fromDividends], para que el CSV sea reproducible y comparable.
     */
    fun buildDraft(
        renta: RentaYearSummary?,
        dividends: DividendYearSummary?,
        codes: F22Codes = F22Codes(),
    ): F22Draft {
        val lines = mutableListOf<F22Line>()
        if (renta != null) lines += fromRenta(renta, codes)
        if (dividends != null) lines += fromDividends(dividends, codes)
        return F22Draft(lines = lines)
    }

    // ===========================================================================================
    // Exportación CSV
    // ===========================================================================================

    /**
     * Serializa el borrador a CSV determinista.
     *
     * Formato:
     *  - Encabezado fijo: `codigo,glosa,monto_clp,origen`.
     *  - Una fila por línea, en el orden estable del borrador.
     *  - Montos en pesos enteros (sin separador de miles, sin decimales): se re-redondea con
     *    [Clp.round] por defensa y se formatea como entero (`toLong`).
     *  - Última fila: comentario con el disclaimer obligatorio, prefijado con `#` para distinguirlo
     *    de los datos.
     *
     * Escapado RFC-4180 mínimo: si un campo de texto contiene coma, comilla doble o salto de línea,
     * se encierra en comillas dobles y las comillas internas se duplican. Los montos no se escapan
     * (siempre numéricos). El separador de líneas es "\n" para que el test pueda comparar el string
     * exacto sin depender del SO.
     */
    fun toCsv(draft: F22Draft): String {
        val sb = StringBuilder()
        sb.append("codigo,glosa,monto_clp,origen").append('\n')
        for (line in draft.lines) {
            sb.append(escape(line.code)).append(',')
            sb.append(escape(line.label)).append(',')
            sb.append(formatAmount(line.amountClp)).append(',')
            sb.append(escape(line.source)).append('\n')
        }
        // Disclaimer como comentario final (no es una fila de datos).
        sb.append("# ").append(draft.disclaimer)
        return sb.toString()
    }

    /** Formatea el monto a peso entero sin separadores; re-saneado/redondeado por defensa. */
    private fun formatAmount(value: Double): String {
        val rounded = Clp.round(value) // sanea no-finitos a 0.0 y redondea a peso entero
        return rounded.toLong().toString()
    }

    /**
     * Escapa un campo de texto según RFC-4180 mínimo: encierra en comillas si contiene coma,
     * comilla doble o salto de línea, duplicando las comillas internas.
     */
    private fun escape(field: String): String {
        val needsQuoting = field.contains(',') ||
            field.contains('"') ||
            field.contains('\n') ||
            field.contains('\r')
        if (!needsQuoting) return field
        val escaped = field.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
