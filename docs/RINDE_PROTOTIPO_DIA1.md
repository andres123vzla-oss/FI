# Rinde — Prototipo, Día 1 (núcleo verificable)

Rama: `feat/rinde-prototipo` (sobre `claude/auditoria-fixes`).

## Qué se agregó (aditivo, NO toca Room/UI/seguridad → build verde intacto)

**Motor tributario (dominio puro, testeable en JVM):**
- `domain/RentaModels.kt` — `YearMonth`, `BuyLot`, `SaleEvent`, `RentaParams`, `RentaResult`, `ReconItem`.
- `domain/RentaCalculator.kt` — mayor valor con **FIFO + reajuste IPC + Art.107 + umbral 10 UTA**
  y **conciliación** contra terceros (corredora / propuesta SII). Cero deps Android.

**Capa de razonamiento (LLM — Llama 3):**
- `domain/ReasoningService.kt` — interfaz + `RentaExplainer` (prompts) + `OfflineReasoningService`
  (fallback determinista, sin red). **El LLM solo explica; nunca calcula cifras.**
- `data/reasoning/OllamaReasoningService.kt` — backend real vía Ollama `POST /api/chat`
  (modelo `llama3` por defecto), patrón de red de `RemoteMarketDataRepository`.

**Seed parametrizado:**
- `app/src/main/assets/renta_params_2026.json` — UTA, IPC, tasas (valores de ejemplo, citar SII/INE).

**Tests (JVM, sin red):**
- `test/RentaCalculatorTest.kt` — 5 casos (Art.107 con IPC, ingreso no renta, FIFO 2 lotes, sin costo, conciliación).
- `test/ReasoningTest.kt` — prompts + fallback offline.

## Cómo correr los tests

Android Studio (Windows, recomendado): click derecho sobre `RentaCalculatorTest` → Run.

Línea de comando:
```
./gradlew :app:testDebugUnitTest --tests "com.example.RentaCalculatorTest" --tests "com.example.ReasoningTest"
```
En WSL agregar los workarounds: `-Pksp.incremental=false --max-workers=2 -Dorg.gradle.jvmargs="-Xmx2g"`
y repuntar `sdk.dir` al SDK de Linux (ver memoria fi-wsl-build-env).

## Siguiente (Día 2+, requiere build Android)
1. Entidades Room nuevas (`ProjectEntity`, `DocumentEntity`, `TaxLotEntity`, `ThirdPartyReportEntity`)
   + DAO + `MIGRATION_3_4` (schema actual = `3.json`). Crear test de migración o declarar fallback.
2. 2 `BottomNavItem` nuevos (Proyecto, Renta) en `MainActivity.kt` + composables bajo `LockGate.UNLOCKED`.
3. Adjuntar documento vía SAF al sandbox cifrado.
4. Panel **Conciliación SII** (clímax de demo, 100% offline) + botón webhook al chat (bonus).
5. Cargar `renta_params_2026.json` desde assets (parser).

> Antes de codear Día 2: mergear/estabilizar `claude/auditoria-fixes` y correr el smoke test.
> Corregir `network_security_config.xml` (hoy bloquea cleartext y solo whitelist finnhub.io) para el webhook/Ollama.
