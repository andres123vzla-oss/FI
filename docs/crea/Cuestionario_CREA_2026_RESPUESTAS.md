# Cuestionario de Postulación — Desafío CREA Estudiantes INACAP 2026
### Respuestas basadas en el proyecto desarrollado: **Rinde (FI-Suite)**
Link de postulación: https://convocatoriasinacap.vform.io/

> Nota: las secciones 1 (datos personales) y 9 (equipo) deben completarse con tus datos reales.
> Las cifras de mercado son estimaciones de referencia: cítalas con fuente oficial del SII al postular.

---

## 1. Información Personal del Postulante
- **Nombre Completo:** _[completar]_
- **RUT:** _[completar]_
- **Correo electrónico (mail Inacap):** _[completar]@inacapmail.cl_
- **Teléfono de contacto:** _[completar]_
- **Carrera:** _[completar]_
- **Sede:** _[completar]_

---

## 2. Información del Proyecto

**Nombre del proyecto:** Rinde — finanzas personales con Operación Renta asistida (privacidad por diseño).

**Resumen del proyecto (≤200 palabras):**
Rinde es una app móvil Android, *offline-first* y cifrada en el dispositivo, que ayuda a personas en Chile a (1) ordenar sus finanzas personales (ingresos, gastos, presupuesto, inversiones) y (2) preparar un **borrador referencial** para su Operación Renta ante el SII. Su diferencial es hacer **durante el año** el cálculo tributario que casi nadie hace: empareja compras y ventas de acciones/ETF por **FIFO**, reajusta el costo por **IPC** y aplica reglas reales (Art. 107, umbral de 10 UTA anual, dividendos nacionales y Art. 41 A), entregando una **conciliación** “lo que el SII propuso vs. tus registros”. Cumple líneas rojas legales: **nunca declara por el usuario, no pide la Clave Tributaria** y entrega un *“borrador referencial — no se presenta al SII”*; el usuario **copia los valores e ingresa en sii.cl**. Todo dato sensible se cifra localmente (SQLCipher + Android Keystore), con bloqueo por PIN/biometría y **sin servidores obligatorios**. Incluye una integración opcional con Notion. Hoy es un **prototipo funcional**: la app compila y corre, con un motor tributario respaldado por 38 pruebas automatizadas en verde.

---

## 3. Problemática identificada

**¿Qué problemática o necesidad busca solucionar?**
La Operación Renta es confusa y propensa a errores, sobre todo para quienes tienen inversiones (mayor valor de acciones/ETF, dividendos, activos en el exterior). A esto se suma una baja educación financiera y la desconfianza por la privacidad de los datos. En la práctica, muchas personas no llevan registro durante el año, **pagan impuestos de más**, **declaran con errores** (con riesgo de multas) o **dependen de un contador caro**. Las apps de finanzas disponibles son genéricas (no chilenas), no abordan la tributación local y suben datos sensibles a la nube.

**¿Quiénes y cuántas personas se ven impactadas?**
Contribuyentes chilenos, en especial la creciente masa de **inversionistas retail jóvenes** que operan vía brokers y fintech. En Chile se presentan del orden de **millones de declaraciones de renta al año** (cifra oficial del SII), y el segmento con rentas de capital y nuevos inversionistas digitales —**cientos de miles de personas en fuerte crecimiento**— es el más afectado. Público inicial concreto: **estudiantes y jóvenes profesionales INACAP** que invierten y enfrentan su primera Operación Renta.

**¿Cuál es el impacto de esta problemática?**
Pérdida directa de dinero (impuestos pagados de más, o multas/intereses por errores), **tiempo y estrés** cada abril, **exclusión** (quien no entiende, no invierte o no cumple bien) y **riesgo de privacidad** al usar herramientas que centralizan datos financieros sensibles en la nube.

---

## 4. Propuesta de valor (foco en valor medible, no en la solución)

Rinde **reduce errores, sobrepagos y tiempo** en la Operación Renta al adelantar la conciliación: detectar diferencias y respaldos faltantes **antes** de declarar. Valor medible que buscamos demostrar en el pilotaje:
- **Tiempo:** reducir en ≥50 % el tiempo dedicado a ordenar la información tributaria (de horas/visita al contador a minutos en la app).
- **Errores/sobrepagos:** lograr que ≥80 % de los usuarios identifique al menos **una diferencia o respaldo faltante** antes de declarar, disminuyendo el riesgo de declarar mal o pagar de más.
- **Educación financiera:** que el usuario **entienda** su situación (mayor valor, dividendos, umbral 10 UTA), medible con una breve evaluación antes/después.
- **Privacidad:** **cero** datos financieros sensibles en la nube por defecto (todo cifrado en el dispositivo), reduciendo el riesgo de exposición.

---

## 5. Solución

**¿Cuál es su solución? (elementos innovadores)**
App Android (Jetpack Compose) con dos pilares: (a) un **panel financiero personal cifrado** (ingresos, gastos, presupuesto y cartera de inversiones con precios) y (b) el módulo **“Renta”**: un **motor tributario** que durante el año empareja ventas con compras por **FIFO**, **reajusta el costo por IPC**, clasifica el mayor valor (Art. 107 / régimen general / umbral 10 UTA anual / pérdidas), procesa **dividendos** (crédito SAC y Art. 41 A) y arma un **borrador del Formulario 22** más una **conciliación contra la propuesta del SII**. Innovaciones clave:
- **Cálculo continuo**, no solo en abril (casi nadie reajusta el costo por IPC durante el año).
- **Capa de cumplimiento legal**: muestra Art. 107 y dividendos del exterior (Art. 41 A) solo como *“requiere contador”* (sin cifra definitiva), con el sello *“borrador referencial — no se presenta al SII”* y el botón **“Copiar valores para ingresar en sii.cl”**; **nunca** solicita la Clave Tributaria.
- **Privacidad por diseño**: cifrado local (SQLCipher + Keystore), `FLAG_SECURE`, App Lock con PIN/biometría y **sin backend obligatorio**.

**¿Por qué es diferente o mejor que otras soluciones?**
Las apps de finanzas existentes (Fintonic, Mobills, etc.) son **genéricas** y no resuelven la tributación chilena; los **servicios contables** son caros y reactivos (después de abril); el **portal del SII** no organiza tus respaldos ni explica las cifras. Rinde **combina** gestión financiera + preparación tributaria chilena + privacidad local + cumplimiento legal estricto, algo que **ninguna alternativa ofrece junto**. Además es **educativa y transparente** (explica cada cifra) y **no reemplaza** al SII ni al contador: los complementa, posicionándose como “organizador de respaldos y borrador de apoyo”.

---

## 6. Estado del Proyecto

**Etapa: prototipo funcional / en desarrollo.** Existe una app Android que **compila (APK) y corre en emulador**, con: seguridad (PIN/biometría, cifrado en reposo), las pantallas de finanzas y la **nueva pantalla “Renta” (Operación Renta / Conciliación SII)**, el **motor tributario con 38 pruebas unitarias automatizadas en verde** (FIFO, reajuste IPC, umbral 10 UTA, dividendos, exportación F22) y la capa de cumplimiento legal. Próximos pasos antes de lanzar: **validación con usuarios reales** (pilotaje en la Operación Renta 2026), **parametrización oficial** de UTA e IPC por año tributario y **revisión legal/tributaria** (abogado tributario y de protección de datos, Ley 21.719).

---

## 7. Plan de pilotaje

**¿Cómo planean validar la solución?**
Pilotaje durante la **Operación Renta 2026 (abril)** con un grupo de **15–30 estudiantes/jóvenes inversionistas INACAP**. Cada participante registra sus operaciones del año, genera su borrador y lo concilia con la propuesta del SII. Se mide: **tiempo** de preparación, **diferencias detectadas**, **errores evitados**, comprensión (test antes/después) y satisfacción (NPS). Validación tributaria: comparar el borrador con la declaración final revisada por un **contador/tutor**.

**¿Qué recursos necesitarían?**
Un grupo de estudiantes voluntarios, un **contador o tutor** que valide los resultados, algunos **dispositivos Android** de prueba, los **parámetros oficiales** (UTA y tabla IPC del año tributario) y **mentoría tributaria/legal**. Los costos de infraestructura son bajos: la app es local y no requiere servidores.

---

## 8. Sostenibilidad y Escalamiento

**¿Qué recursos necesita para implementar el proyecto en la realidad?**
Financiamiento semilla para **revisión legal/tributaria** (abogado tributario + protección de datos, Ley 21.719), **publicación en Google Play**, diseño/UX, y **mantención de los parámetros tributarios por año**; además **mentorías** y **alianzas** (corredoras/fintech, INACAP, convenios con contadores).

**¿Cómo planea que sea sostenible en el tiempo?**
Modelo **freemium**: el panel financiero y el borrador básico **gratis**; funciones **premium** por una suscripción anual baja (más años tributarios, importación de cartolas de corredoras, exportación avanzada, sincronización opcional). Los **costos operativos son bajos** (offline-first, sin servidores obligatorios), por lo que la app es rentable incluso con pocas suscripciones. Vía de escalamiento: alianzas **B2B2C** con corredoras/fintech que quieran ofrecer a sus clientes la preparación de la renta, y expansión a otros perfiles de contribuyentes.

**Eje de sostenibilidad al que contribuye (impacto social y/o ambiental):**
**Impacto social.** Rinde promueve la **inclusión y educación financiera** y el **cumplimiento tributario correcto**, reduciendo sobrepagos y errores en personas que hoy no acceden a asesoría. Aporta a los ODS **4** (educación de calidad – alfabetización financiera), **8** (trabajo decente y crecimiento económico – formalización y cumplimiento) y **10** (reducción de desigualdades – democratiza una herramienta tributaria que antes requería pagar un contador). El componente ambiental es menor (solución digital, sin papel), por lo que el eje **más representativo** de la propuesta es el **social**.

---

## 9. Equipo de Trabajo
- **¿El proyecto tiene un equipo? (Sí / No):** _[completar]_
- **Integrantes (Nombre y apellidos – RUT – Carrera, Sede):**
  - _[completar]_
- **¿Tiene docente mentor o tutor?:** _[completar]_
- **Nombre del Docente:** _[completar]_
