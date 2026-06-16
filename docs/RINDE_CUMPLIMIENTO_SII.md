# Rinde — Cumplimiento legal/contable del módulo de Renta (SII, Chile)

Estrategia completa: `Rinde_Legal_SII.docx` (fuera del repo). Este archivo es el resumen
**accionable y vinculante para el código**.

> **Condición de lanzamiento (no opcional):** revisión por (1) abogado tributario y (2) abogado de
> protección de datos (Ley 21.719) chilenos ANTES de publicar el módulo de renta.

## Principio rector
La declaración ante el SII es **responsabilidad indelegable del contribuyente**. Rinde es un
**organizador de respaldos y borrador de apoyo**, NUNCA asesor ni declarante.

## Líneas rojas (en código y contratos) — ver `domain/SiiPolicy.kt`
- ❌ No presentar ni firmar el F22/DJ por el usuario. El flujo termina en "ingresa en sii.cl".
- ❌ No solicitar ni almacenar la Clave Tributaria. ❌ No screen-scraping del portal del SII.
- ❌ No actuar como agente informante/retenedor.
- ❌ No presentar **Art. 107** ni **Art. 41 A** como cifra definitiva en la v1.

## Disclosure v1 — ver `domain/RentaDisclosure.kt`
El motor (`RentaCalculator`, `DividendCalculator`) **no cambia** (la cifra cruda queda para la
memoria de cálculo y el contador). La capa de disclosure decide qué ve el usuario:
- **Art. 107** → `ORGANIZE_ONLY` (sin cifra; "organiza respaldos y revisa con tu contador").
- **Dividendos extranjeros (Art. 41 A)** → `ORGANIZE_ONLY`.
- Ingreso no renta / régimen general informativo / dividendos nacionales (crédito SAC) →
  `REFERENCIAL`, con disclaimer **versionado por año tributario** (`RentaDisclaimer.forYear`).

## Pendiente (capa legal/datos, fuera del motor)
- ToS + disclaimers redactados por abogado (limitación, no exención; Ley 19.496, Arts. 1465/44 CC).
- Política de Privacidad separada (Ley 21.719) + DPA con cada contador/proveedor + registro de
  tratamiento + derechos ARCO+ + política de retención/borrado de datos tributarios.
- Human-in-the-loop como **derivación/referido** a contadores independientes (no prestación integrada).
- QA del motor revisado por contador chileno cada temporada (única defensa real contra culpa grave).
