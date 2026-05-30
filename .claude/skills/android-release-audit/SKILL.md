---
name: android-release-audit
description: Auditoría de preparación para release de una app Android. Úsala para revisar el Manifest, permisos, allowBackup, dataExtractionRules, fullBackupContent, usesCleartextTraffic, componentes exported, logs, configuración de release, dependencias innecesarias y exposición de datos sensibles antes del unlock.
---

# Android Release Audit

Actúa como ingeniero responsable de la **auditoría previa a producción** de una app Android de
finanzas personales. Tu meta es detectar problemas de configuración y seguridad antes de
publicar. Trabaja en español y reporta con archivo:línea.

## Áreas de auditoría

### AndroidManifest
- [ ] Solo los **permisos** estrictamente necesarios; sin permisos peligrosos injustificados.
- [ ] **`allowBackup`**: revisado y configurado acorde a la sensibilidad de los datos
      (preferiblemente `false` o con reglas de exclusión).
- [ ] **`dataExtractionRules`** (Android 12+) configurado para excluir datos financieros y
      credenciales de cloud backup y device transfer.
- [ ] **`fullBackupContent`** (Android ≤11) configurado coherentemente.
- [ ] **`usesCleartextTraffic`** en `false` (o Network Security Config equivalente).
- [ ] **Componentes `exported`**: cada Activity/Service/Receiver/Provider con `exported`
      explícito; nada exportado sin necesidad ni sin protección (permiso/firma).

### Logs
- [ ] Sin logs de datos sensibles (PIN, hash, salt, montos, tokens).
- [ ] Logging deshabilitado o reducido en build de release.

### Configuración de release (Gradle)
- [ ] `minifyEnabled`/R8 y reglas ProGuard revisadas.
- [ ] `debuggable=false` en release; sin `BuildConfig.DEBUG` filtrando rutas inseguras.
- [ ] Firma de release configurada fuera del control de versiones (sin keystore ni claves
      commiteadas).
- [ ] `versionCode`/`versionName` correctos.

### Dependencias
- [ ] Sin **dependencias innecesarias** o sin uso.
- [ ] Sin librerías con vulnerabilidades conocidas o abandonadas.
- [ ] Sin Firebase ni backend externo obligatorio introducido sin justificación.

### Datos sensibles antes del unlock
- [ ] Ningún dato financiero visible antes de la autenticación (incluida pantalla de recientes,
      notificaciones, widgets y deep links).
- [ ] `FLAG_SECURE` presente donde corresponde.

### Preparación básica para producción
- [ ] Íconos, nombre y `applicationId` correctos.
- [ ] Manejo de errores y estados de carga sin exponer trazas internas al usuario.
- [ ] Compila en release; `./gradlew lint` sin issues bloqueantes.

## Proceso

1. **Leer** el `AndroidManifest.xml`, los `build.gradle(.kts)` y reglas de backup/network.
2. **Verificar** cada ítem contra el código real.
3. **Clasificar** hallazgos por severidad (bloqueante / alto / medio / bajo).
4. **Recomendar** la corrección concreta para cada hallazgo.

## Salida esperada

Informe por área con `severidad — archivo:línea — hallazgo — corrección` y un veredicto final:
**listo / no listo para release**, con la lista de bloqueantes.

Complementa con `secure-storage-review` y `mobile-security-hardening`.
