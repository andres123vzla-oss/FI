package com.example.domain

/**
 * Líneas rojas de interacción con el SII para el módulo de Renta de "Rinde".
 *
 * Derivadas de la estrategia legal (ver docs/RINDE_CUMPLIMIENTO_SII.md). Principio rector: la
 * declaración es responsabilidad INDELEGABLE del contribuyente. Rinde es un "organizador de
 * respaldos y borrador de apoyo", NUNCA asesor ni declarante.
 *
 * PROHIBICIONES (no implementar nunca, ni detrás de un flag):
 *  - NO presentar ni firmar el F22/DJ por el usuario.
 *  - NO solicitar ni almacenar la Clave Tributaria del SII.
 *  - NO hacer screen-scraping del portal del SII con la clave del usuario.
 *  - NO actuar como agente informante/retenedor.
 *  - NO presentar Art. 107 ni Art. 41 A como cifra definitiva en la v1 (ver [RentaDisclosure]).
 *
 * Estas reglas son contractuales y de arquitectura: cualquier PR que las viole debe rechazarse en
 * la revisión de código.
 */
object SiiPolicy {

    /** Naturaleza del servicio (para UI/onboarding). */
    const val SERVICE_NATURE: String =
        "Rinde es una herramienta de apoyo que organiza respaldos y genera borradores referenciales. " +
            "No presta asesoría legal, tributaria ni contable, y no presenta declaraciones por ti."

    /** Mensaje de cierre del flujo: el usuario declara, Rinde no. */
    const val USER_FILES_AT_SII: String =
        "Rinde no presenta ni firma. Ingresa estos valores en tu cuenta del SII (sii.cl), con tu Clave Tributaria."

    /** Etiqueta del botón final (jamás "Presentar" / "Declarar"). */
    const val FINAL_ACTION_LABEL: String = "Copiar valores para ingresar en sii.cl"
}
