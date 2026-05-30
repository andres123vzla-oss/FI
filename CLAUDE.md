# CLAUDE.md

## Descripción del proyecto

App **Android de finanzas personales en español**. Permite gestionar ingresos, gastos,
balance, gastos por categoría, presupuesto e inversiones, con seguridad local (App Lock,
PIN/contraseña y biometría) y una UI moderna en Jetpack Compose + Material 3.

Toda la comunicación, comentarios y textos de UI son en **español**.

## Prioridades (en orden)

1. **Compilación estable** — el proyecto siempre debe compilar.
2. **Seguridad local antes de mostrar datos financieros** — nada sensible visible sin desbloqueo.
3. **Cálculos financieros correctos** — exactitud numérica y casos límite controlados.
4. **UI moderna** — Jetpack Compose + Material 3, claro/oscuro, accesible.
5. **Tests mínimos** — cubrir al menos los cálculos financieros y la seguridad crítica.

## Reglas (no romper)

- **No eliminar funcionalidades existentes.**
- **No guardar PIN/contraseña en texto plano** — solo hash con salt.
- **No hardcodear claves** ni secretos (código, recursos, `BuildConfig`).
- **No loguear datos sensibles** (PIN, hash, salt, montos, tokens).
- **No usar Firebase ni backend externo obligatorio.**

## Comandos

Ejecutar cuando sea posible:

```bash
./gradlew assembleDebug   # compilación
./gradlew test            # tests unitarios
./gradlew lint            # análisis estático
```

## Datos financieros de referencia (estado inicial)

| Métrica            | Valor esperado |
|--------------------|----------------|
| Ingresos iniciales | CLP 1.090.094  |
| Gastos iniciales   | CLP 748.825    |
| Balance inicial    | CLP 341.269    |

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
