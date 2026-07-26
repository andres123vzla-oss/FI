package com.example.ui.screens

import com.example.domain.BuyLot
import com.example.domain.DividendCalculator
import com.example.domain.DividendParams
import com.example.domain.DividendResult
import com.example.domain.DividendSource
import com.example.domain.F22Export
import com.example.domain.ForeignDividend
import com.example.domain.NationalDividend
import com.example.domain.RentaCalculator
import com.example.domain.RentaDisclaimer
import com.example.domain.RentaDisclosure
import com.example.domain.RentaParams
import com.example.domain.RentaRegime
import com.example.domain.RentaResult
import com.example.domain.SaleEvent
import com.example.domain.SiiPolicy
import com.example.util.FormatUtils
import kotlin.math.abs

/**
 * Capa de PRESENTACIÓN (pura, sin Android/Compose) de la pantalla "Renta" (Operación Renta — Chile).
 *
 * Cablea la UI al dominio que YA existe sin duplicar ni reimplementar reglas tributarias:
 *  - [RentaCalculator.computeYear] / [DividendCalculator.computeYear] → cifras agregadas del año.
 *  - [RentaDisclosure] → decide QUÉ se muestra: Art. 107 y dividendos del exterior (Art. 41 A)
 *    SIEMPRE en modo "requiere contador" (ORGANIZE_ONLY), sin cifra de impuesto.
 *  - [RentaCalculator.reconcile] → conciliación local contra la propuesta del SII.
 *  - [F22Export] → CSV borrador para el botón "Copiar valores para ingresar en sii.cl".
 *  - [RentaDisclaimer.forYear] → sello versionado por año tributario.
 *  - [SiiPolicy.FINAL_ACTION_LABEL] → etiqueta del botón final (jamás "Presentar"/"Declarar").
 *
 * Diseño dueño: los montos arrancan VACÍOS (la app abre en blanco). Con entradas vacías, el motor
 * devuelve cero operaciones y este presentador produce un estado vacío amable, manteniendo SIEMPRE
 * el sello legal, las dos tarjetas "requiere contador" (la política v1) y el footer de Clave
 * Tributaria. Cuando existan datos, las cifras referenciales se muestran con su sello.
 *
 * OBJETO PURO: solo `kotlin.*`, el dominio y `util/FormatUtils` (JVM puro). Cero imports de Android,
 * así queda testeable como el resto del motor.
 */
object RentaPresenter {

    /**
     * Seed PLACEHOLDER. El valor real (UTA del año tributario) debe inyectarse desde fuente oficial
     * (SII/INE) cuando exista esa capa de datos. NUNCA se muestra al usuario y, con operaciones
     * vacías, no afecta ninguna cifra; existe solo para satisfacer la firma del motor.
     */
    private const val SEED_UTA_PLACEHOLDER_CLP = 800_000.0

    fun build(
        taxYear: Int,
        buys: List<BuyLot> = emptyList(),
        sales: List<SaleEvent> = emptyList(),
        nationals: List<NationalDividend> = emptyList(),
        foreigns: List<ForeignDividend> = emptyList(),
        siiProposed: Map<String, Double> = emptyMap(),
        ourFigures: Map<String, Double> = emptyMap(),
        params: RentaParams = RentaParams(utaClp = SEED_UTA_PLACEHOLDER_CLP),
        dividendParams: DividendParams = DividendParams(),
    ): RentaUiState {
        // --- Motor (cableado real al dominio existente) ---
        val renta = RentaCalculator.computeYear(buys, sales, ipcIndex = emptyMap(), params = params)
        val dividends = DividendCalculator.computeYear(nationals, foreigns, dividendParams)
        val reconItems = RentaCalculator.reconcile(ourFigures, siiProposed)
        val draft = F22Export.buildDraft(renta, dividends)
        val csv = F22Export.toCsv(draft)

        // --- Disclosure: Art. 107 y dividendos del exterior SIEMPRE "requiere contador" (sin cifra) ---
        val art107View = RentaDisclosure.forSale(
            RentaResult(
                ticker = "—", proceeds = 0.0, adjustedCost = 0.0, capitalGain = 0.0,
                regime = RentaRegime.ART_107, taxEstimate = 0.0, belowThreshold = false,
                matchedQuantity = 0.0,
            ),
            taxYear,
        )
        val art41View = RentaDisclosure.forDividend(
            DividendResult(
                issuer = "—", source = DividendSource.EXTRANJERO, grossClp = 0.0, grossUpClp = 0.0,
                taxableBaseClp = 0.0, creditClp = 0.0, restitutionClp = 0.0,
            ),
            taxYear,
        )

        // --- Tiles del resumen (sin inventar: derivados de lo que el motor calculó) ---
        val tiles = listOf(
            RentaTile(label = "Ventas del año", value = sales.size.toString()),
            RentaTile(
                label = "Dividendos",
                value = FormatUtils.formatCLPCompact(dividends.totalTaxableBaseClp),
                isAmount = true,
            ),
            RentaTile(label = "Respaldos", value = "0"),
        )
        val emptyHint = if (sales.isEmpty() && nationals.isEmpty() && foreigns.isEmpty()) {
            "Aún no registras ventas ni dividendos del año tributario $taxYear. " +
                "Cuando los agregues, verás aquí su detalle referencial."
        } else {
            null
        }

        // --- Conciliación SII ---
        val rows = reconItems.map { item ->
            val diffLabel = if (item.matches) {
                "Coincide"
            } else {
                val sign = if (item.difference >= 0) "+" else "−"
                sign + FormatUtils.formatCLP(abs(item.difference))
            }
            RentaReconRow(
                concept = item.ticker,
                siiText = FormatUtils.formatCLP(item.reported),
                mineText = FormatUtils.formatCLP(item.ours),
                diffLabel = diffLabel,
                matches = item.matches,
            )
        }
        val reconEmpty = if (rows.isEmpty()) {
            "Aún no hay datos para conciliar. Cuando registres tus operaciones y cargues la " +
                "propuesta del SII, las diferencias aparecerán aquí."
        } else {
            null
        }
        val diffCount = rows.count { !it.matches }
        val diffWarning = if (diffCount > 0) {
            val noun = if (diffCount == 1) "diferencia detectada" else "diferencias detectadas"
            "$diffCount $noun. Revísalas con tu contador antes de ingresar al SII."
        } else {
            null
        }

        return RentaUiState(
            overline = "Operación Renta $taxYear",
            sealHeadline = "Borrador referencial — no se presenta al SII. Tú declaras en sii.cl.",
            sealDetail = RentaDisclaimer.forYear(taxYear),
            tiles = tiles,
            emptyHint = emptyHint,
            art107 = RentaOrganizeCard(
                title = "Mayor valor en acciones · Art. 107",
                message = art107View.message,
            ),
            art41a = RentaOrganizeCard(
                title = "Dividendos del exterior · Art. 41 A",
                message = art41View.message,
            ),
            reconRows = rows,
            reconEmptyMessage = reconEmpty,
            diffWarning = diffWarning,
            copyLabel = SiiPolicy.FINAL_ACTION_LABEL,
            copyPayload = csv,
        )
    }
}

/** Una métrica del bloque "Resumen de inversiones". [isAmount] enmascara el valor en modo privacidad. */
data class RentaTile(
    val label: String,
    val value: String,
    val isAmount: Boolean = false,
)

/** Tarjeta informativa en modo "requiere contador" (sin cifra de impuesto): Art. 107 / Art. 41 A. */
data class RentaOrganizeCard(
    val title: String,
    val message: String,
    val chipLabel: String = "Requiere contador",
)

/** Una fila de la conciliación SII (propuesta del SII vs. nuestros registros). */
data class RentaReconRow(
    val concept: String,
    val siiText: String,
    val mineText: String,
    val diffLabel: String,
    val matches: Boolean,
)

/** Estado completo, listo para render, de la pantalla "Renta". */
data class RentaUiState(
    val overline: String,
    val sealHeadline: String,
    val sealDetail: String,
    val tiles: List<RentaTile>,
    val emptyHint: String?,
    val art107: RentaOrganizeCard,
    val art41a: RentaOrganizeCard,
    val reconRows: List<RentaReconRow>,
    val reconEmptyMessage: String?,
    val diffWarning: String?,
    val copyLabel: String,
    val copyPayload: String,
)
