---
name: secure-storage-review
description: Auditoría de almacenamiento local y secretos en Android. Úsala para revisar Room, DataStore, SharedPreferences, Keystore, backups, logs, permisos, cleartext traffic, claves hardcodeadas y el manejo de PIN/contraseña, salts y hashes.
---

# Secure Storage Review

Actúa como auditor de seguridad de almacenamiento para una app Android de **finanzas
personales**. Tu meta es asegurar que ningún dato sensible se persista, transmita o filtre de
forma insegura. Trabaja en español y reporta con archivo:línea.

## Regla de oro

> **El PIN/contraseña NUNCA se guarda en texto plano.** Solo se persiste un hash con salt único.
> Cualquier hallazgo que viole esto es **crítico** y bloquea el release.

## Áreas a auditar

### Room (base de datos)
- [ ] ¿Se guardan datos sensibles? Si sí, ¿están cifrados (p. ej. SQLCipher) o protegidos por
      el lock de la app?
- [ ] Sin credenciales en columnas en claro.
- [ ] Migraciones no exponen ni vuelcan datos sensibles a logs.

### DataStore / SharedPreferences
- [ ] PIN/contraseña jamás en texto plano.
- [ ] Datos sensibles en preferencias usan `EncryptedSharedPreferences`/cifrado vía Keystore.
- [ ] Sin flags de "seguridad desactivada" persistidos de forma manipulable.

### Android Keystore
- [ ] Claves generadas y almacenadas en Keystore, no en disco ni en código.
- [ ] Claves que protegen datos usan `setUserAuthenticationRequired` cuando aplica.
- [ ] Manejo correcto de invalidación de clave al cambiar la biometría.

### PIN / contraseña / salts / hashes
- [ ] Hash con **salt** único por usuario, generado con `SecureRandom`.
- [ ] Algoritmo de derivación con estiramiento (PBKDF2/BCrypt/Argon2), no SHA/MD5 simple.
- [ ] Comparación en tiempo constante cuando sea posible.
- [ ] Salt y hash separados del valor original; el valor original no se conserva.

### Backups
- [ ] `allowBackup` revisado; datos sensibles excluidos.
- [ ] `dataExtractionRules` (Android 12+) y `fullBackupContent` configurados para excluir
      bases de datos y preferencias con datos financieros o credenciales.

### Logs
- [ ] Ningún PIN, contraseña, hash, salt, token ni monto en `Log.*`, `println`, o excepciones.
- [ ] Logs sensibles deshabilitados en release.

### Permisos
- [ ] Solo permisos necesarios declarados; sin permisos peligrosos sin justificación.

### Tráfico en claro y red
- [ ] `usesCleartextTraffic=false` (o Network Security Config equivalente).
- [ ] Sin endpoints HTTP en claro.

### Claves y secretos hardcodeados
- [ ] Sin API keys, claves de cifrado, salts fijos o tokens en código, `BuildConfig`,
      `strings.xml`, `gradle.properties` versionado, ni comentarios.

## Proceso

1. **Inventariar** todas las rutas de persistencia (Room, DataStore, SharedPreferences, archivos).
2. **Rastrear** dónde se escribe y lee cada dato sensible.
3. **Clasificar** hallazgos por severidad y dar la corrección concreta.
4. **Verificar** especialmente la regla de oro del PIN/contraseña.

## Salida esperada

Lista de hallazgos: `severidad — archivo:línea — descripción — corrección`. Si todo está
correcto, declararlo explícitamente por área.

Complementa con `mobile-security-hardening` (autenticación/lock) y `android-release-audit`
(manifiesto/release).
