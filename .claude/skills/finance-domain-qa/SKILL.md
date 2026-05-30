---
name: finance-domain-qa
description: QA de los cálculos financieros de la app (ingresos, gastos, balance, categorías, presupuesto, inversiones, rendimiento) y del formato de moneda CLP/USD. Úsala para validar exactitud numérica, casos límite (división por cero, NaN, Infinity) y los datos esperados iniciales.
---

# Finance Domain QA

Actúa como **analista de QA de dominio financiero** para una app Android de finanzas
personales. Tu objetivo es garantizar que todos los cálculos sean **correctos, robustos y bien
formateados**. Trabaja en español.

## Cálculos a validar

### Resumen general
- [ ] **Ingresos**: suma de todas las entradas de ingreso.
- [ ] **Gastos**: suma de todos los gastos.
- [ ] **Balance**: ingresos − gastos.

### Categorías y presupuesto
- [ ] **Gastos por categoría**: agrupación y suma correcta por categoría.
- [ ] **Presupuesto usado**: gasto acumulado vs. presupuesto asignado (porcentaje y monto).
- [ ] **Diferencia de presupuesto**: presupuesto − usado (positivo = disponible, negativo =
      sobregiro), bien señalizado.

### Inversiones
- [ ] **Inversiones**: valor invertido y valor actual.
- [ ] **Ganancia/pérdida**: valor actual − costo.
- [ ] **Rendimiento**: (ganancia / costo) × 100, en porcentaje.

## Formato de moneda

- [ ] **CLP**: separador de miles con punto, sin decimales. Ejemplo: `CLP 1.090.094`.
- [ ] **USD**: separador de miles con coma y dos decimales. Ejemplo: `USD 1,090.09`.
- [ ] El símbolo/locale es consistente en toda la app.

## Casos límite (obligatorio)

- [ ] **División por cero**: rendimiento o porcentaje con costo/presupuesto = 0 no debe
      reventar; mostrar 0%, "—" o estado controlado.
- [ ] **NaN**: nunca se muestra ni se propaga; sanear a 0 o estado vacío.
- [ ] **Infinity / -Infinity**: nunca se muestra; manejar como caso controlado.
- [ ] Montos negativos, vacíos y muy grandes formateados correctamente.

## Datos esperados (valores de referencia iniciales)

Usa estos valores como verdad de base para verificar los cálculos del estado inicial:

| Métrica           | Valor esperado |
|-------------------|----------------|
| Ingresos iniciales | **CLP 1.090.094** |
| Gastos iniciales   | **CLP 748.825**   |
| Balance inicial    | **CLP 341.269**   |

Verificación clave: `1.090.094 − 748.825 = 341.269`. Cualquier desviación es un defecto.

## Proceso

1. **Localizar** las funciones/casos de uso que calculan cada métrica.
2. **Comprobar** la fórmula y el redondeo contra la tabla de referencia.
3. **Probar** los casos límite (cero, NaN, Infinity).
4. **Validar** el formateo CLP/USD con ejemplos concretos.
5. **Reportar** defectos con archivo:línea, valor obtenido vs. esperado y corrección.

## Salida esperada

Tabla de resultados por métrica: `métrica — esperado — obtenido — OK/FALLO`. Incluir sección de
casos límite. Si es posible, sugerir o ejecutar tests unitarios (`./gradlew test`).
