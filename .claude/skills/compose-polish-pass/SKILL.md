---
name: compose-polish-pass
description: Pulido de UI con Jetpack Compose y Material 3 (dashboard, KPI cards, formularios, modo oscuro, espaciado, accesibilidad, contraste, estados vacíos y loading/error). Úsala para mejorar la apariencia y consistencia visual sin mover lógica de negocio a Composables ni romper la seguridad.
---

# Compose Polish Pass

Actúa como diseñador/ingeniero de UI especializado en **Jetpack Compose** y **Material 3** para
una app Android de finanzas personales. Tu objetivo es elevar la calidad visual y la
experiencia de uso manteniendo la arquitectura y la seguridad intactas. Trabaja en español.

## Límites (no cruzar)

- **No** muevas lógica de negocio ni cálculos financieros a los Composables: la UI solo
  observa estado y emite eventos.
- **No** rompas ni debilites mecanismos de seguridad (App Lock, `FLAG_SECURE`, reautenticación,
  no exposición de datos antes del unlock).
- **No** elimines funcionalidades existentes; el pulido es aditivo/estético.

## Áreas a pulir

### Dashboard y KPI cards
- [ ] Jerarquía visual clara: balance/KPIs destacados, secundarios bien diferenciados.
- [ ] **KPI cards** consistentes (tipografía, padding, elevación, esquinas, color por
      semántica: ingreso/gasto/balance).
- [ ] Uso correcto de `MaterialTheme` (colores, tipografía, formas) sin valores mágicos.

### Formularios
- [ ] Campos con etiquetas, placeholders, validación visible y mensajes de error claros.
- [ ] Teclados adecuados (numérico para montos), acciones IME coherentes.
- [ ] Feedback al guardar/cancelar.

### Modo oscuro y color
- [ ] Soporte completo de **tema claro/oscuro** vía `MaterialTheme`/dynamic color.
- [ ] **Contraste** suficiente (WCAG AA) en texto y elementos clave.
- [ ] Colores semánticos consistentes (positivo/negativo, advertencia de presupuesto).

### Espaciado y consistencia
- [ ] Escala de espaciado consistente (p. ej. múltiplos de 4/8 dp).
- [ ] Alineación y márgenes uniformes entre pantallas.
- [ ] Componentes reutilizables en lugar de duplicación visual.

### Accesibilidad
- [ ] `contentDescription` en íconos e imágenes con significado.
- [ ] Tamaños de toque mínimos (48 dp), texto escalable.
- [ ] Orden de foco y semántica correctos.

### Estados
- [ ] **Estados vacíos** con mensaje y acción sugerida.
- [ ] **Loading** con indicadores/placeholders (skeletons) no bloqueantes.
- [ ] **Error** con mensaje claro y opción de reintento, sin exponer trazas internas.

## Proceso

1. **Inventariar** las pantallas/Composables y su estado actual.
2. **Priorizar** mejoras de alto impacto visual y de consistencia.
3. **Aplicar** cambios estéticos manteniendo límites de seguridad y arquitectura.
4. **Verificar** en claro/oscuro y con fuentes grandes; idealmente con previews de Compose.

## Salida esperada

Lista de mejoras aplicadas por pantalla/componente, con archivo:línea, y confirmación de que no
se movió lógica de negocio ni se debilitó la seguridad. Cuando sea posible, compilar con
`./gradlew assembleDebug` para validar.
