# CLAUDE.md

## Qué es este proyecto (y para quién)

App **Android de finanzas personales en español** (Jetpack Compose + Material 3), offline-first
y cifrada en el dispositivo: ingresos, gastos, balance, categorías, presupuesto, cartera de
inversiones y el módulo **"Renta"** (Operación Renta — Chile: FIFO, reajuste IPC, Art. 107,
dividendos, borrador F22 y conciliación SII).

**Uso PERSONAL.** La usa únicamente el dueño del repo para manejar su propio dinero. No es un
producto para terceros ni se publica en Play Store. La postulación al Desafío CREA INACAP 2026
quedó cerrada (el pitch no se envió); sus documentos están archivados en `docs/crea/`.

Consecuencias de ese destinatario:
- Las cifras tributarias se muestran **completas** (la capa `RentaDisclosure` queda gobernada
  por un flag de distribución pública en `SiiPolicy`; no se elimina).
- La prioridad número uno es **no perder los datos** acumulados durante años de uso.

Toda la comunicación, comentarios y textos de UI son en **español**. El prompt de arranque
para sesiones de trabajo está en `docs/PROMPT_FABLE5.md`.

## Prioridades (en orden)

1. **Compilación estable y tests verdes** — el proyecto siempre compila; los tests unitarios
   (hoy 134: 38 del motor Rinde, 16 de respaldo, 6 de recurrentes y 1 de migración entre
   ellos) nunca quedan rojos.
2. **Nunca perder datos** — respaldo/restauración cifrados y exportables (IMPLEMENTADO:
   tarjeta "Respaldo" en Ajustes → Administración y Datos); toda subida de versión de Room
   lleva `Migration` explícita y testeada (esquemas versionados en `app/schemas/`); ningún
   borrado silencioso. Al subir `AppDatabase.version`, subir también
   `BackupManager.CURRENT_SCHEMA_VERSION`.
3. **Seguridad local antes de mostrar datos** — nada sensible visible sin desbloqueo.
4. **Cálculos financieros correctos** — exactitud numérica y casos límite controlados.
5. **Baja fricción de registro diario** — registrar un gasto debe costar segundos.
6. **UI moderna** — Compose + M3, claro/oscuro, accesible. Va al final: primero utilidad.

## Reglas (no romper)

- **No eliminar funcionalidades existentes** (si algo sobra, proponer y esperar aprobación).
- **No guardar PIN/contraseña en texto plano** — solo hash con salt (`security/PinHasher.kt`).
- **No hardcodear claves ni secretos** (código, recursos, `BuildConfig`).
- **No hardcodear cifras tributarias** (UTA, IPC, tasas, códigos F22): viven en el seed de
  assets (`renta_params_*.json`). Hoy esta regla está incumplida en `ui/screens/RentaPresenter.kt`
  (`SEED_UTA_PLACEHOLDER_CLP`) — corregirlo es parte del backlog, no un patrón a imitar.
- **No loguear datos sensibles** (PIN, hash, salt, montos, tokens, passphrases).
- **Sin Firebase ni backend obligatorio.** Red opcional: precios de mercado (Finnhub) y, a
  futuro, un Ollama local del propio usuario.
- **Líneas rojas SII** (`domain/SiiPolicy.kt`), válidas también para uso personal: nunca pedir
  ni almacenar la Clave Tributaria, nunca presentar/firmar ante el SII, sin screen-scraping
  del portal.

## Estado real (verificado 26-jul-2026)

Base sólida: Room + SQLCipher 4.16 (passphrase protegida por Android Keystore: AES/GCM,
StrongBox si existe), App Lock con PIN (`CharArray` borrable) y biometría con `CryptoObject`,
`FLAG_SECURE`, 6 pestañas (Resumen, Movimientos, Presupuesto, Portafolio, Ajustes, Renta),
motor tributario puro en `domain/` con 38 tests.

**Respaldo cifrado exportable (P0, hecho 26-jul-2026):** Ajustes → Administración y Datos.
Export/import de las 5 tablas con passphrase del usuario (PBKDF2 + AES-256/GCM, formato v2 de
`BackupCodec`; los respaldos v1 se importan igual), archivo vía SAF, import atómico
todo-o-nada que jamás toca la BD ante error, recordatorio "último respaldo hace N días" y
reautenticación por PIN para importar. Además: háptica (desbloqueo, destructivas, copiado),
pull-to-refresh en Portafolio, combines tipados en `FinanceViewModel`, `LocalClipboard` y
pares de tokens `onPrimary`/`onError`.

**Movimientos recurrentes (P1-1, hecho 26-jul-2026):** tarjeta en Ajustes (crear regla con
tipo/categoría/monto/día, activar/desactivar, eliminar). `data/recurring/RecurringGenerator`
(puro, 6 tests): día efectivo recortado en meses cortos, catch-up de meses saltados, mes en
curso solo al llegar el día, ancla `lastYear/lastMonth` idempotente (reabrir jamás duplica),
aplicación atómica en `FinanceDao.applyGenerated`. Se dispara al abrir la app y al cambiar el
día (`refreshDateTick`). Reactivar una regla re-ancla a hoy (no genera los meses apagados).
BD **v4** con `MIGRATION_3_4` testeada (MigrationTest construye la v3 desde el `3.json`
exportado y deja que Room valide el esquema migrado — en AGP 9 los assets de host tests no se
pueden inyectar, por eso no se usa MigrationTestHelper).

Brechas conocidas, por gravedad:

| # | Brecha | Detalle |
|---|--------|---------|
| 1 | **La pantalla Renta es una cáscara** | `RentaScreen` llama a `RentaPresenter.build` solo con `buys`; ventas, dividendos y propuesta SII van vacíos → la Conciliación siempre muestra su estado vacío y el CSV copiado solo trae encabezado + disclaimer. No hay UI para registrar ventas ni dividendos. |
| 2 | **El seed tributario no se lee** | `app/src/main/assets/renta_params_2026.json` no tiene ningún lector; `RentaPresenter` usa UTA=800.000 placeholder e `ipcIndex` vacío (el reajuste IPC siempre da factor 1,0). |
| 3 | **Fecha de compra falsa en el FIFO** | Los lotes se derivan de `InvestmentEntity.createdAt` (fecha en que se creó el registro, no la de compra real). |
| 4 | Sin importación CSV de cartola | Los movimientos no recurrentes se teclean a mano (las plantillas mensuales ya se generan solas). |
| 5 | `ReasoningService`/Ollama sin consumidor | Código muerto a la espera de cablearse (`network_security_config.xml` hoy solo permite `finnhub.io`). |
| 6 | Deprecaciones y estilo pendientes | `TabRow` deprecado (AjustesScreen, MovimientosScreen → Primary/SecondaryTabRow); `Icons.Filled.ReceiptLong` (MovimientosScreen) y `Backspace` (PinPad) → AutoMirrored; `Color.White` de DashboardScreen por auditar sitio a sitio (los blancos sobre degradado fijo de los heros están BIEN y no se tocan). |
| 7 | `README.md` es la plantilla de AI Studio | Reescribir cuando toque. |
| 8 | `FinanceViewModel` ~1000 líneas | Partirlo por dominios (dashboard/movimientos/presupuesto/portafolio) cuando haya una razón funcional; el respaldo ya salió a `BackupViewModel`. |

## Backlog priorizado

**P0 — no perder nada — ✅ COMPLETADO (26-jul-2026)**
Respaldo cifrado exportable/importable + recordatorio en Ajustes. Falta solo la verificación
E2E manual en emulador: exportar → "Borrar todos los datos" → importar → verificar los
totales de referencia y la cartera. Passphrase incorrecta debe fallar con mensaje claro y BD
intacta (ya blindado por `BackupRoundTripTest`).

**P1 — fricción diaria** (recurrentes ✅ hechas)
1. Importar cartola bancaria CSV con mapeo de columnas y detección de duplicados.
2. Entrada rápida: repetir último movimiento, categoría sugerida por descripción; checkbox
   "repetir cada mes" en el diálogo de agregar movimiento (crea la regla desde Movimientos).

**P1 — Renta útil**
4. Entidades Room `TaxLotEntity` / `SaleEntity` / `DividendEntity` + `MIGRATION_4_5` explícita
   y testeada (y subir `BackupManager.CURRENT_SCHEMA_VERSION` + formato v3 del respaldo).
5. Fecha de compra real en la cartera (hoy `createdAt`).
6. Leer `renta_params_2026.json` desde assets con cifras oficiales (SII/INE) y eliminar el
   placeholder.
7. UI para registrar ventas/dividendos y pegar la propuesta del SII → la Conciliación muestra
   diferencias reales y el CSV del F22 lleva líneas de datos.
8. Flag de distribución en `SiiPolicy` (`PUBLIC_DISTRIBUTION = false`) para que
   `RentaDisclosure` muestre las cifras completas de Art. 107 y Art. 41 A en modo personal.

**P2 — extras**
9. Cablear `ReasoningService` a un Ollama local ("explícame mis gastos del mes").
10. README real.
11. Brecha #6 (deprecaciones TabRow/íconos AutoMirrored + auditoría `Color.White` de
    Dashboard) y #8 (partir `FinanceViewModel`).

## Mapa rápido del código

- `domain/` — motor puro JVM (cero Android): `RentaCalculator`, `DividendCalculator`,
  `F22Export`, `RentaModels`, `RentaDisclosure`, `SiiPolicy`, analizadores
  (presupuesto/flujo/categorías/salud financiera). Cualquier cambio tributario entra con su
  test primero.
- `security/` — `DatabaseKeyProvider` (Keystore + AES/GCM), `BackupCrypto` (PBKDF2 + AES/GCM
  del respaldo, JVM puro), `PinHasher`, `SecurityViewModel`, `AppLockManager`, `BiometricGate`,
  `SecurityPreferences`.
- `data/` — Room (`AppDatabase` v4, migraciones explícitas, política de no-borrado silencioso),
  `FinanceDao`, `FinanceRepository`, `backup/` (`BackupCodec` formato v2 + `BackupManager`
  export/import atómico), `recurring/RecurringGenerator` (puro), mercado (Finnhub/manual),
  `OllamaReasoningService`.
- `ui/` — pantallas Compose, `RentaPresenter` (presentación pura testeable), `BackupViewModel`
  (flujo de respaldo, separado del `FinanceViewModel`), componentes compartidos
  (`PrivacyAmountText`, `FinanceCard`, `MainTopBar`, `EmptyState`), tema (dark-first).
- Tests en `app/src/test/` (JVM puro + Robolectric).

## Comandos

```bash
./gradlew assembleDebug   # compilación (Windows: gradlew.bat)
./gradlew test            # tests unitarios — deben quedar todos verdes
./gradlew lint            # análisis estático
```

## Datos de referencia del seed demo (carga MANUAL)

La app arranca **vacía**; no existe auto-siembra (blindado por `SeedDataRegressionTest`). El
set demo se carga solo con `FinanceRepository.restoreSeedData()` y debe sumar:

| Métrica  | Valor          |
|----------|----------------|
| Ingresos | CLP 1.090.094  |
| Gastos   | CLP 748.825    |
| Balance  | CLP 341.269    |

Verificación: `1.090.094 − 748.825 = 341.269`.

Formato de moneda:
- **CLP**: punto como separador de miles, sin decimales (`CLP 1.090.094`).
- **USD**: coma de miles y dos decimales (`USD 1,090.09`).

Casos límite a manejar siempre: división por cero, `NaN`, `Infinity`.

## Skills disponibles

- **mobile-security-hardening** — App Lock, PIN/biometría, Keystore, bloqueo automático,
  reautenticación, `FLAG_SECURE`, protección antes del unlock.
- **secure-storage-review** — auditoría de Room, DataStore, SharedPreferences, Keystore,
  backups, logs, secretos y manejo de PIN/salt/hash.
- **finance-domain-qa** — validación de cálculos financieros, formato CLP/USD y casos límite.
- **android-release-audit** — Manifest, permisos, backups, cleartext, exported, release.
- **compose-polish-pass** — pulido de UI Compose/Material 3 sin tocar lógica ni seguridad.
