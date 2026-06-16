package com.example.domain

/**
 * Motor de DIVIDENDOS del proyecto "Rinde" (Operación Renta — Chile).
 *
 * Cubre dos fuentes:
 *  - NACIONAL (sociedad chilena): el dividendo se incrementa ("gross-up") por el crédito IDPC
 *    informado en el SAC (DJ 1948), formando la base afecta a IGC. El crédito imputable depende
 *    del régimen de la empresa pagadora: Pro Pyme 14 D usa el 100% del crédito IDPC; el semi
 *    integrado 14 A usa el 65% y, salvo convenio, genera una restitución del 35% (mayor impuesto).
 *  - EXTRANJERA (acción/ETF/broker): el dividendo bruto y el impuesto retenido afuera se convierten
 *    a CLP por el tipo de cambio OBSERVADO a la fecha de PERCEPCIÓN. El crédito por impuestos
 *    externos (Art. 41 A LIR) tiene un tope INDIVIDUAL del 35% de la renta extranjera y un tope
 *    GLOBAL del 35% de la Renta Neta de Fuente Extranjera (RNFE) del ejercicio, aplicado en el
 *    agregado anual.
 *
 * OBJETO PURO: cero imports de Android/Room. Solo `kotlin.*`. Todas las tasas, topes y tipos de
 * cambio llegan desde [DividendParams] (seed SII); NUNCA se hardcodean en la lógica. Guards de
 * robustez (vía [Clp.sanitize]/[Clp.isSafe]) impiden propagar NaN/Infinity.
 *
 * El motor entrega base afecta a IGC y créditos imputables; NO calcula el Impuesto Global
 * Complementario final (requiere la tabla de tramos del período y la suma con las demás rentas
 * del contribuyente).
 *
 * SUPUESTOS / FUERA DE ALCANCE (declarados): no se modela el Art. 41 G (rentas pasivas / control
 * de entidades en el exterior) ni los convenios país-específicos más allá del flag
 * [NationalDividend.conConvenio]; la granularidad del tipo de cambio es mensual ([YearMonth.key]).
 */
object DividendCalculator {

    /** Disclaimer obligatorio reexportado por conveniencia. */
    const val DISCLAIMER: String = RentaDisclaimer.TEXT

    /**
     * Dividendo NACIONAL: forma la base afecta a IGC y el crédito IDPC imputable.
     *
     * Aritmética:
     *  - `creditRate` = 100% (Pro Pyme 14 D) o 65% (semi integrado 14 A), desde [DividendParams].
     *  - `creditClp`  = `sacCreditIdpc * creditRate` (crédito IDPC efectivamente imputable).
     *  - `grossUpClp` = `creditClp` (el incremento que se agrega al dividendo es el crédito).
     *  - `taxableBaseClp` = `grossClp + grossUpClp` (dividendo percibido + incremento).
     *  - `restitutionClp` = `sacCreditIdpc * semiRestitutionRate` SOLO si 14 A y sin convenio; 0 si no.
     *
     * Todas las cifras se sanean a finito (NaN/Infinity → 0.0) antes de operar.
     */
    fun computeNational(div: NationalDividend, params: DividendParams): DividendResult {
        val grossClp = Clp.sanitize(div.grossDividend)
        val sacCredit = Clp.sanitize(div.sacCreditIdpc)

        val isSemi = div.empresaRegime == EmpresaRegime.SEMI_INTEGRADO_14A
        val creditRate = if (isSemi) {
            Clp.sanitize(params.semiCreditRate)
        } else {
            Clp.sanitize(params.proPymeCreditRate)
        }

        val creditClp = Clp.sanitize(sacCredit * creditRate)
        val grossUpClp = creditClp
        val taxableBaseClp = Clp.sanitize(grossClp + grossUpClp)

        // Restitución del 35%: solo régimen semi integrado 14 A y SIN convenio vigente.
        val restitutionClp = if (isSemi && !div.conConvenio) {
            Clp.sanitize(sacCredit * Clp.sanitize(params.semiRestitutionRate))
        } else {
            0.0
        }

        val note = buildString {
            if (isSemi) {
                append("Semi integrado 14A: crédito IDPC al ")
                append((creditRate * 100).toInt())
                append("%")
                append(if (div.conConvenio) " (con convenio, sin restitución)." else ", restitución 35%.")
            } else {
                append("Pro Pyme 14D: crédito IDPC al 100%, sin restitución.")
            }
        }

        return DividendResult(
            issuer = div.issuer,
            source = DividendSource.NACIONAL,
            grossClp = grossClp,
            grossUpClp = grossUpClp,
            taxableBaseClp = taxableBaseClp,
            creditClp = creditClp,
            restitutionClp = restitutionClp,
            note = note,
        )
    }

    /**
     * Dividendo EXTRANJERO: convierte a CLP por el TC observado a fecha de PERCEPCIÓN y calcula el
     * crédito por impuestos externos (Art. 41 A) con tope INDIVIDUAL.
     *
     * Aritmética:
     *  - `fx` = `fxObserved[date.key]` (CLP por unidad extranjera); si falta o no es válido → null.
     *  - Si `fx == null`: no se inventa tipo de cambio. `grossClp = grossUpClp = taxableBaseClp =
     *    creditClp = 0.0` y se deja nota para registrar el TC.
     *  - `grossClp`   = `grossForeign * fx`.
     *  - `grossUpClp` = impuesto extranjero en CLP = `taxWithheldForeign * fx` (Art. 41 A: la renta
     *    se declara incrementada en el impuesto pagado afuera).
     *  - `taxableBaseClp` = `grossClp + grossUpClp`.
     *  - `creditClp` = `min(impuestoCLP, foreignCreditCapRate * grossClp)` (tope individual 35%).
     *
     * El tope GLOBAL (35% de la RNFE del ejercicio) se aplica en [computeYear], no aquí.
     */
    fun computeForeign(div: ForeignDividend, params: DividendParams): DividendResult {
        val fx = fxRate(params, div.date)

        if (fx == null) {
            return DividendResult(
                issuer = div.issuer,
                source = DividendSource.EXTRANJERO,
                grossClp = 0.0,
                grossUpClp = 0.0,
                taxableBaseClp = 0.0,
                creditClp = 0.0,
                restitutionClp = 0.0,
                note = "Falta TC observado para ${div.date.key}: registra el tipo de cambio (${div.currency}).",
            )
        }

        val grossForeign = Clp.sanitize(div.grossForeign)
        val taxForeign = Clp.sanitize(div.taxWithheldForeign)

        val grossClp = Clp.sanitize(grossForeign * fx)
        val taxClp = Clp.sanitize(taxForeign * fx)
        val grossUpClp = taxClp
        val taxableBaseClp = Clp.sanitize(grossClp + grossUpClp)

        // Tope INDIVIDUAL Art. 41 A: el crédito no supera el 35% de la renta extranjera (gross CLP).
        val capRate = Clp.sanitize(params.foreignCreditCapRate)
        val individualCap = Clp.sanitize(capRate * grossClp)
        val creditClp = Clp.sanitize(minOf(taxClp, individualCap))

        val note = if (taxClp > individualCap) {
            "Crédito Art.41A topeado al ${(capRate * 100).toInt()}% individual (TC ${div.date.key} = $fx)."
        } else {
            "Convertido por TC observado ${div.date.key} = $fx ${div.currency}."
        }

        return DividendResult(
            issuer = div.issuer,
            source = DividendSource.EXTRANJERO,
            grossClp = grossClp,
            grossUpClp = grossUpClp,
            taxableBaseClp = taxableBaseClp,
            creditClp = creditClp,
            restitutionClp = 0.0,
            note = note,
        )
    }

    /**
     * DRIVER ANUAL: procesa todos los dividendos nacionales y extranjeros, agrega bases y créditos,
     * y aplica el TOPE GLOBAL Art. 41 A.
     *
     * Tope global: el crédito por impuestos externos del ejercicio no puede exceder el 35% de la
     * Renta Neta de Fuente Extranjera (RNFE = Σ `grossClp` de los extranjeros). Si la suma de
     * créditos individuales supera ese tope, cada crédito se escala proporcionalmente por
     * `topeGlobal / Σ créditos` (prorrateo del recorte). Los agregados se redondean a peso entero.
     *
     * Nota: la lista [DividendYearSummary.perDividend] conserva los resultados INDIVIDUALES (con el
     * crédito al tope individual); el recorte global solo afecta el total [totalForeignCreditClp].
     */
    fun computeYear(
        nationals: List<NationalDividend>,
        foreigns: List<ForeignDividend>,
        params: DividendParams,
    ): DividendYearSummary {
        val nationalResults = nationals.map { computeNational(it, params) }
        val foreignResults = foreigns.map { computeForeign(it, params) }
        val all = nationalResults + foreignResults

        val totalTaxableBase = Clp.round(all.sumOf { Clp.sanitize(it.taxableBaseClp) })
        val totalIdpcCredit = Clp.round(nationalResults.sumOf { Clp.sanitize(it.creditClp) })
        val totalRestitution = Clp.round(all.sumOf { Clp.sanitize(it.restitutionClp) })

        // RNFE del ejercicio = suma de rentas brutas extranjeras (en CLP).
        val rnfe = foreignResults.sumOf { Clp.sanitize(it.grossClp) }
        val capRate = Clp.sanitize(params.foreignCreditCapRate)
        val globalCap = Clp.sanitize(capRate * rnfe)

        val foreignCreditSum = foreignResults.sumOf { Clp.sanitize(it.creditClp) }
        // Si la suma de créditos individuales muerde el tope global, prorratear el recorte.
        val cappedForeignCredit = if (foreignCreditSum > globalCap && foreignCreditSum > 0.0) {
            globalCap
        } else {
            foreignCreditSum
        }
        val totalForeignCredit = Clp.round(Clp.sanitize(cappedForeignCredit))

        return DividendYearSummary(
            perDividend = all,
            totalTaxableBaseClp = totalTaxableBase,
            totalIdpcCreditClp = totalIdpcCredit,
            totalForeignCreditClp = totalForeignCredit,
            totalRestitutionClp = totalRestitution,
        )
    }

    /**
     * Conversión de tipo de cambio robusta: devuelve `fxObserved[date.key]` si existe y es finito y
     * positivo; en cualquier otro caso devuelve null (no se inventa un TC). Guard estilo
     * FinanceCalculator.ratio: divisor/factor no positivo o no finito → resultado seguro (null).
     */
    fun fxRate(params: DividendParams, date: YearMonth): Double? {
        val fx = params.fxObserved[date.key] ?: return null
        if (!Clp.isSafe(fx) || fx <= 0.0) return null
        return fx
    }
}
