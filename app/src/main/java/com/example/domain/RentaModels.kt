package com.example.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Modelos del módulo de Renta (Operación Renta — Chile) del proyecto "Rinde".
 *
 * Todo en CLP. Las cifras tributarias (UTA, IPC, tasas, umbrales) llegan SIEMPRE desde un seed
 * parametrizado por año ([RentaParams] + tabla IPC); NUNCA se hardcodean en la lógica. Así un
 * error tributario es un dato corregible, no un bug enterrado en el código.
 *
 * Este archivo es la ÚNICA fuente de tipos del dominio Rinde: ningún otro archivo del motor
 * (RentaCalculator, DividendCalculator, F22Export, tests) declara data classes o enums; todos
 * los consumen desde aquí para evitar redeclaraciones y colisiones.
 *
 * OBJETO PURO: cero imports de Android/Room. Solo `kotlin.*`, `kotlin.math.*` y
 * `java.math.BigDecimal`/`java.math.RoundingMode` (válidos en JVM y Android sin desugaring,
 * ya en uso en util/FormatUtils.kt).
 */

// ===========================================================================================
// SECCIÓN A — Tipos base CONSERVADOS (compatibilidad dura con RentaCalculator/RentaCalculatorTest)
// ===========================================================================================

/** Año-mes simple (sin java.time, para correr en cualquier JVM/Android sin desugaring). */
data class YearMonth(val year: Int, val month: Int) {
    init { require(month in 1..12) { "mes inválido: $month" } }

    /** Mes calendario anterior (para el reajuste IPC, que usa el mes previo a compra/venta). */
    fun prev(): YearMonth = if (month == 1) YearMonth(year - 1, 12) else YearMonth(year, month - 1)

    /** Clave "YYYY-MM" para indexar la tabla IPC. */
    val key: String get() = "%04d-%02d".format(year, month)
}

/** Lote de COMPRA de un instrumento (acción/ETF). Fuente de verdad del costo por lote (FIFO). */
data class BuyLot(
    val ticker: String,
    val date: YearMonth,
    val quantity: Double,
    val unitPrice: Double,
    val commission: Double = 0.0,
)

/** Evento de VENTA (enajenación) a conciliar tributariamente. */
data class SaleEvent(
    val ticker: String,
    val date: YearMonth,
    val quantity: Double,
    val unitPrice: Double,
    val commission: Double = 0.0,
    /** true = acción/ETF con presencia bursátil acogida al Art. 107 LIR (impuesto único). */
    val isArt107: Boolean = false,
)

/** Parámetros tributarios del año, desde seed citado (SII/INE). */
data class RentaParams(
    val utaClp: Double,
    val art107Rate: Double = 0.10,
    val noRentaThresholdUta: Double = 10.0,
) {
    val noRentaThresholdClp: Double get() = noRentaThresholdUta * utaClp
}

/**
 * Régimen tributario aplicable al mayor valor de UNA enajenación.
 *
 * EXTENSIÓN aditiva (no rompe consumidores existentes): se agrega [PERDIDA] para clasificar
 * mayores valores negativos, que NO son "ingreso no renta" (fix auditoría: una pérdida no debe
 * confundirse con quedar bajo el umbral).
 */
enum class RentaRegime {
    /** Acción/ETF con presencia bursátil, Art. 107 LIR: impuesto único 10% sobre el mayor valor. */
    ART_107,

    /** Mayor valor de régimen general bajo el umbral anual (10 UTA): ingreso no constitutivo de renta. */
    INGRESO_NO_RENTA,

    /** Mayor valor de régimen general afecto a IGC (sobre el umbral anual). */
    REGIMEN_GENERAL,

    /** Venta sin lote de costo asociado (cobertura nula): no estimable, requiere revisión. */
    SIN_COSTO,

    /** Mayor valor negativo (pérdida): netea dentro de su pool de régimen. NUEVO (fix auditoría). */
    PERDIDA,
}

/**
 * Resultado del cálculo de mayor valor de UNA enajenación.
 *
 * Los campos nuevos llevan default para no romper construcciones existentes (RentaCalculatorTest,
 * RentaExplainer en ReasoningService.kt que lee ticker/proceeds/adjustedCost/capitalGain/regime/
 * taxEstimate/note).
 */
data class RentaResult(
    val ticker: String,
    val proceeds: Double,        // precio de venta neto de comisión (prorrateado a la cantidad calzada)
    val adjustedCost: Double,    // costo tributario reajustado por IPC
    val capitalGain: Double,     // mayor valor (puede ser negativo)
    val regime: RentaRegime,
    val taxEstimate: Double?,    // null = requiere IGC (no estimable sin el tramo del contribuyente)
    val belowThreshold: Boolean,
    val matchedQuantity: Double,
    val note: String = "",
    // --- NUEVOS, todos con default para no romper construcciones existentes ---
    /** Unidades de la venta sin lote de costo (cobertura parcial). 0.0 si calce total. */
    val uncoveredQuantity: Double = 0.0,
    /** Disclaimer obligatorio: toda salida lo porta. */
    val disclaimer: String = RentaDisclaimer.TEXT,
)

/** Item de conciliación contra lo informado por un tercero (corredora / propuesta del SII). */
data class ReconItem(
    val ticker: String,
    val ours: Double,
    val reported: Double,
) {
    val difference: Double get() = ours - reported
    /** Coincide si la diferencia es menor a 1 peso (tolerancia de redondeo). */
    val matches: Boolean get() = kotlin.math.abs(difference) < 1.0
}

// ===========================================================================================
// SECCIÓN B — Constantes y utilidades compartidas
// ===========================================================================================

/**
 * Disclaimer obligatorio del proyecto. Toda salida del motor Rinde debe portarlo:
 * el resultado es un borrador de apoyo, NO un archivo de carga ni una declaración al SII.
 */
object RentaDisclaimer {
    const val TEXT: String =
        "Borrador para revisar con tu contador; no se presenta directo al SII."
}

/**
 * Redondeo monetario CLP (HALF_UP a peso entero), coherente con util/FormatUtils.
 *
 * Convención de robustez heredada de FinanceCalculator/FormatUtils.sanitize: ante valores no
 * finitos (NaN/Infinity) se sanea a 0.0; nunca se propagan.
 *
 * REGLA: [round] se aplica SOLO a montos finales de salida, nunca en pasos intermedios por lote
 * (eso acumularía error de redondeo).
 */
object Clp {
    /** Redondea a peso entero (HALF_UP); sanea no-finitos a 0.0. */
    fun round(value: Double): Double {
        if (!isSafe(value)) return 0.0
        return BigDecimal(value).setScale(0, RoundingMode.HALF_UP).toDouble()
    }

    /** true si el valor es finito (NaN/Infinity → false). */
    fun isSafe(value: Double): Boolean = !value.isNaN() && !value.isInfinite()

    /** Sanea: no-finito → 0.0; en otro caso devuelve el valor tal cual. */
    fun sanitize(value: Double): Double = if (isSafe(value)) value else 0.0
}

// ===========================================================================================
// SECCIÓN C — Agregación ANUAL del mayor valor (régimen general + Art. 107)
// ===========================================================================================

/**
 * Resultado AGREGADO del año para el mayor valor de acciones/ETF (Art. 17 N°8 a) LIR).
 *
 * FIX auditoría CRÍTICO: el umbral de 10 UTA es ANUAL. Se evalúa sobre la SUMA de mayores valores
 * del régimen general del ejercicio, neteada con pérdidas del MISMO régimen — NO operación por
 * operación. Los pools Art.107 y régimen general son separados: las pérdidas netean solo dentro
 * de su propio pool.
 */
data class RentaYearSummary(
    val perSale: List<RentaResult>,        // un resultado por venta (FIFO global ya aplicado)
    val art107NetGain: Double,             // suma de mayores valores Art.107 (pérdidas netean SOLO aquí)
    val art107Tax: Double,                 // art107NetGain (si >0) * art107Rate
    val generalGrossGain: Double,          // suma de ganancias positivas régimen general
    val generalNetGain: Double,            // ganancias menos pérdidas, régimen general
    val generalBelowThreshold: Boolean,    // generalNetGain <= 10 UTA
    val generalTaxableBase: Double,        // 0 si bajo umbral; si no, TODO el mayor valor afecto a IGC
    val noRentaThresholdClp: Double,       // umbral usado (trazabilidad del seed)
    val disclaimer: String = RentaDisclaimer.TEXT,
)

// ===========================================================================================
// SECCIÓN D — Modelos de DIVIDENDOS (consumidos por DividendCalculator)
// ===========================================================================================

/**
 * Régimen tributario de la empresa pagadora (determina el % de crédito IDPC utilizable y si hay
 * restitución del 35%).
 */
enum class EmpresaRegime {
    /** Pro Pyme transparente/general (Art. 14 D): crédito IDPC 100%, sin restitución. */
    PRO_PYME_14D,

    /** Régimen semi integrado (Art. 14 A): crédito IDPC 65%, restitución 35% (salvo convenio). */
    SEMI_INTEGRADO_14A,
}

/** Origen del dividendo, para mapeo F22 y agregación. */
enum class DividendSource { NACIONAL, EXTRANJERO }

/** Dividendo de fuente NACIONAL (sociedad chilena). Cifras en CLP. */
data class NationalDividend(
    val issuer: String,
    val date: YearMonth,
    val grossDividend: Double,        // dividendo distribuido (CLP), antes de gross-up
    val sacCreditIdpc: Double,        // crédito IDPC informado en SAC (DJ 1948), CLP
    val empresaRegime: EmpresaRegime,
    val conConvenio: Boolean = false, // residente país con convenio: NO restituye el 35%
)

/** Dividendo de fuente EXTRANJERA (acción/ETF/broker). Montos en MONEDA EXTRANJERA. */
data class ForeignDividend(
    val issuer: String,
    val date: YearMonth,
    val grossForeign: Double,         // dividendo bruto en moneda extranjera
    val taxWithheldForeign: Double,   // impuesto retenido afuera, moneda extranjera
    val currency: String = "USD",
)

/**
 * Parámetros tributarios de dividendos, desde seed (SII). NUNCA hardcodear.
 *
 * [fxObserved] indexa el tipo de cambio observado por fecha de PERCEPCIÓN, con clave
 * [YearMonth.key] ("YYYY-MM") → CLP por unidad extranjera. Supuesto declarado: granularidad
 * mensual suficiente para el borrador; si Rinde requiere TC por día exacto, cambiar la clave a
 * String ISO sin tocar las firmas.
 */
data class DividendParams(
    val proPymeCreditRate: Double = 1.00,    // 100% crédito IDPC (14 D)
    val semiCreditRate: Double = 0.65,       // 65% crédito IDPC (14 A)
    val semiRestitutionRate: Double = 0.35,  // restitución 35% (14 A sin convenio)
    val foreignCreditCapRate: Double = 0.35, // tope individual y global Art. 41 A
    /** Tipo de cambio observado por fecha de percepción: "YYYY-MM" → CLP por unidad extranjera. */
    val fxObserved: Map<String, Double> = emptyMap(),
)

/** Resultado de UN dividendo (nacional o extranjero) ya convertido a CLP. */
data class DividendResult(
    val issuer: String,
    val source: DividendSource,        // NACIONAL | EXTRANJERO
    val grossClp: Double,              // dividendo bruto en CLP (extranjero: ya convertido por TC)
    val grossUpClp: Double,            // incremento (crédito IDPC nacional / impuesto ext. Art.41A)
    val taxableBaseClp: Double,        // base afecta a IGC = grossClp + grossUpClp
    val creditClp: Double,             // crédito imputable contra IGC (IDPC utilizable / Art.41A topeado)
    val restitutionClp: Double,        // mayor impuesto por restitución 35% (solo 14A sin convenio); 0 si N/A
    val note: String = "",
    val disclaimer: String = RentaDisclaimer.TEXT,
)

/** Resumen AGREGADO de dividendos del año, con tope GLOBAL Art. 41 A aplicado. */
data class DividendYearSummary(
    val perDividend: List<DividendResult>,
    val totalTaxableBaseClp: Double,   // suma de bases afectas a IGC (nacional + extranjero)
    val totalIdpcCreditClp: Double,    // crédito IDPC nacional total
    val totalForeignCreditClp: Double, // crédito Art.41A tras aplicar TOPE GLOBAL (35% RNFE)
    val totalRestitutionClp: Double,
    val disclaimer: String = RentaDisclaimer.TEXT,
)

// ===========================================================================================
// SECCIÓN E — Modelos de F22 (consumidos por F22Export)
// ===========================================================================================

/** Una línea/código del Formulario 22 con su monto borrador. */
data class F22Line(
    val code: String,        // p.ej. "1070", "1043", "746"; String porque algunos son alfanuméricos/línea
    val label: String,       // glosa legible en español
    val amountClp: Double,   // monto en pesos enteros (ya redondeado)
    val source: String,      // trazabilidad: "Art.107 SQM", "Dividendo nacional BCI", etc.
)

/** Borrador completo del F22 para conciliar/exportar. */
data class F22Draft(
    val lines: List<F22Line>,
    val disclaimer: String = RentaDisclaimer.TEXT,
)
