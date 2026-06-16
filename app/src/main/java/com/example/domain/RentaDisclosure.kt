package com.example.domain

/**
 * Capa de DISCLOSURE: controla QUÉ se le muestra al usuario, separando el CÁLCULO (que sigue
 * intacto y disponible para la memoria de cálculo y el contador) de su EXPOSICIÓN al usuario final.
 *
 * Implementa la recomendación legal de la v1 (ver docs/RINDE_CUMPLIMIENTO_SII.md):
 *  - **Art. 107** (condicional: presencia bursátil, fechas) → NO se muestra como cifra: se degrada
 *    a "organizar respaldos y revisar con tu contador".
 *  - **Dividendos EXTRANJEROS (Art. 41 A:** crédito por impuesto externo, tipo de cambio) → ídem.
 *  - El resto (ingreso no renta, régimen general informativo, dividendos nacionales con crédito
 *    SAC) se muestra como REFERENCIAL, con disclaimer versionado por año tributario.
 *
 * El motor ([RentaCalculator] / [DividendCalculator]) NO cambia: el número crudo se sigue calculando
 * para auditoría; esta capa solo decide su presentación. Así se reduce el riesgo de recalificación
 * como "asesoría/planificación" sin perder la trazabilidad interna.
 */
enum class DisclosureLevel {
    /** Se muestra la cifra como referencial (no definitiva), con disclaimer. */
    REFERENCIAL,

    /** No se muestra cifra: solo se organizan respaldos y se deriva a un contador. */
    ORGANIZE_ONLY,
}

/** Vista segura (orientada al usuario) de un resultado de mayor valor. */
data class SafeRentaView(
    val ticker: String,
    val regime: RentaRegime,
    val level: DisclosureLevel,
    val taxEstimateShown: Double?,   // null si ORGANIZE_ONLY
    val requiresAccountant: Boolean,
    val message: String,
    val disclaimer: String,
)

/** Vista segura (orientada al usuario) de un dividendo. */
data class SafeDividendView(
    val issuer: String,
    val source: DividendSource,
    val level: DisclosureLevel,
    val taxableBaseShown: Double?,   // null si ORGANIZE_ONLY
    val creditShown: Double?,        // null si ORGANIZE_ONLY
    val requiresAccountant: Boolean,
    val message: String,
    val disclaimer: String,
)

object RentaDisclosure {

    private const val ORGANIZE_MSG_107 =
        "El Art. 107 depende de condiciones (presencia bursátil, fechas) que no calculamos en esta " +
            "versión. Organiza tus respaldos y revisa la cifra con tu contador."
    private const val ORGANIZE_MSG_41A =
        "Los dividendos del exterior (crédito por impuesto pagado afuera y tipo de cambio) no se " +
            "calculan en esta versión. Organiza tus respaldos y revísalo con tu contador."

    /** Degrada Art. 107 a ORGANIZE_ONLY; el resto se muestra referencial. */
    fun forSale(r: RentaResult, taxYear: Int): SafeRentaView {
        val disc = RentaDisclaimer.forYear(taxYear)
        return if (r.regime == RentaRegime.ART_107) {
            SafeRentaView(
                ticker = r.ticker, regime = r.regime, level = DisclosureLevel.ORGANIZE_ONLY,
                taxEstimateShown = null, requiresAccountant = true,
                message = ORGANIZE_MSG_107, disclaimer = disc,
            )
        } else {
            // Régimen general (requiere IGC) y sin costo necesitan revisión de un contador;
            // ingreso no renta y pérdida son informativos.
            val needsAccountant =
                r.regime == RentaRegime.REGIMEN_GENERAL || r.regime == RentaRegime.SIN_COSTO
            SafeRentaView(
                ticker = r.ticker, regime = r.regime, level = DisclosureLevel.REFERENCIAL,
                taxEstimateShown = r.taxEstimate, requiresAccountant = needsAccountant,
                message = r.note, disclaimer = disc,
            )
        }
    }

    /** Degrada dividendos EXTRANJEROS (Art. 41 A) a ORGANIZE_ONLY; nacionales referencial. */
    fun forDividend(d: DividendResult, taxYear: Int): SafeDividendView {
        val disc = RentaDisclaimer.forYear(taxYear)
        return if (d.source == DividendSource.EXTRANJERO) {
            SafeDividendView(
                issuer = d.issuer, source = d.source, level = DisclosureLevel.ORGANIZE_ONLY,
                taxableBaseShown = null, creditShown = null, requiresAccountant = true,
                message = ORGANIZE_MSG_41A, disclaimer = disc,
            )
        } else {
            SafeDividendView(
                issuer = d.issuer, source = d.source, level = DisclosureLevel.REFERENCIAL,
                taxableBaseShown = d.taxableBaseClp, creditShown = d.creditClp,
                requiresAccountant = false, message = d.note, disclaimer = disc,
            )
        }
    }
}
