package com.example

import com.example.data.cartola.CartolaFormatException
import com.example.data.cartola.CartolaImporter
import com.example.data.cartola.CartolaMapping
import com.example.data.cartola.CartolaParser
import com.example.data.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros de la importación de cartola bancaria (P1-2): parsing chileno (separador ';',
 * montos con $ y puntos de miles, fechas dd/MM/yyyy, preámbulo del banco, comillas CSV),
 * mapeo adivinado, y el plan de importación con su detección de duplicados.
 */
class CartolaImportTest {

    /** Cartola sintética estilo BancoEstado: preámbulo + ';' + cargo/abono + fila de totales. */
    private val cartola = """
        Cartola Cuenta RUT;;;
        Fecha desde 01/07/2026 hasta 27/07/2026;;;
        ;;;
        Fecha;Detalle;Cargo;Abono
        05/07/2026;Compra supermercado LIDER;25.990;
        10/07/2026;"Transferencia de JUAN; PEREZ";;150.000
        15/07/2026;Pago cuenta luz ENEL;${'$'} 32.450;
        TOTALES;;58.440;150.000
    """.trimIndent()

    @Test
    fun `parse detecta separador, salta el preambulo y conserva comillas`() {
        val parsed = CartolaParser.parse(cartola)
        assertEquals(';', parsed.separator)
        assertEquals(3, parsed.headerRowIndex)
        assertEquals(listOf("Fecha", "Detalle", "Cargo", "Abono"), parsed.headers)
        assertEquals(4, parsed.rows.size) // 3 movimientos + fila TOTALES
        // El ';' dentro de comillas NO corta la celda.
        assertEquals("Transferencia de JUAN; PEREZ", parsed.rows[1][1])
    }

    @Test
    fun `guessMapping identifica fecha, detalle y cargo-abono`() {
        val mapping = CartolaParser.guessMapping(listOf("Fecha", "Detalle", "Cargo", "Abono"))
        assertEquals(0, mapping.fechaIndex)
        assertEquals(1, mapping.descripcionIndex)
        assertEquals(2, mapping.cargoIndex)
        assertEquals(3, mapping.abonoIndex)
        assertNull(mapping.montoIndex)

        val monto = CartolaParser.guessMapping(listOf("FECHA", "GLOSA", "MONTO"))
        assertEquals(2, monto.montoIndex)
        assertNull(monto.cargoIndex)
    }

    @Test
    fun `montos chilenos - miles con punto, pesos, negativos y parentesis`() {
        assertEquals(1_234_567.0, CartolaParser.parseAmountClp("1.234.567")!!, 0.001)
        assertEquals(25_000.0, CartolaParser.parseAmountClp("$ 25.000")!!, 0.001)
        assertEquals(1_234.56, CartolaParser.parseAmountClp("1.234,56")!!, 0.001)
        assertEquals(-12_500.0, CartolaParser.parseAmountClp("-12.500")!!, 0.001)
        assertEquals(-5_000.0, CartolaParser.parseAmountClp("(5.000)")!!, 0.001)
        assertNull(CartolaParser.parseAmountClp(""))
        assertNull(CartolaParser.parseAmountClp("abc"))
    }

    @Test
    fun `fechas - ddMMyyyy con slash o guion, ISO, anio corto, e invalidas`() {
        assertEquals("2026-07-05", CartolaParser.parseDate("05/07/2026"))
        assertEquals("2026-07-05", CartolaParser.parseDate("5-7-2026"))
        assertEquals("2026-07-05", CartolaParser.parseDate("2026-07-05"))
        assertEquals("2026-07-05", CartolaParser.parseDate("05/07/26"))
        assertNull(CartolaParser.parseDate("31/02/2026")) // febrero no tiene 31
        assertNull(CartolaParser.parseDate("TOTALES"))
    }

    @Test
    fun `decode acepta UTF-8 y cae a Windows-1252 cuando viene del banco`() {
        assertEquals("Descripción", CartolaParser.decode("Descripción".toByteArray(Charsets.UTF_8)))
        // "Descripción" en Windows-1252: la ó es el byte 0xF3 (inválido como UTF-8).
        val win1252 = byteArrayOf(
            0x44, 0x65, 0x73, 0x63, 0x72, 0x69, 0x70, 0x63, 0x69, 0xF3.toByte(), 0x6E,
        )
        assertEquals("Descripción", CartolaParser.decode(win1252))
    }

    @Test
    fun `plan completo - cargo es gasto, abono es ingreso, totales se ignora`() {
        val parsed = CartolaParser.parse(cartola)
        val mapping = CartolaParser.guessMapping(parsed.headers).copy(categoryName = "Otros")
        val plan = CartolaImporter.buildPlan(parsed, mapping, existing = emptyList(), nowMs = 99L)

        assertEquals(3, plan.nuevos.size)
        assertEquals(1, plan.filasIgnoradas) // TOTALES (sin fecha válida)
        assertEquals(0, plan.duplicadosExistentes)

        val (t1, t2, t3) = plan.nuevos
        assertEquals("EXPENSE", t1.type)
        assertEquals("2026-07-05", t1.date)
        assertEquals(25_990.0, t1.amount, 0.001)
        assertEquals("Otros", t1.categoryName)
        assertEquals("INCOME", t2.type)
        assertEquals(150_000.0, t2.amount, 0.001)
        assertEquals("Transferencia de JUAN; PEREZ", t2.description)
        assertEquals("EXPENSE", t3.type)
        assertEquals(32_450.0, t3.amount, 0.001)
    }

    @Test
    fun `duplicados - contra existentes (normalizados) y dentro del archivo`() {
        val parsed = CartolaParser.parse(cartola)
        val mapping = CartolaParser.guessMapping(parsed.headers)
        // Ya existe el súper del 05/07 con distinta capitalización y espacios: es el MISMO.
        val existente = TransactionEntity(
            id = 1, type = "EXPENSE", date = "2026-07-05", categoryName = "Alimentación",
            description = "  compra   SUPERMERCADO lider ", amount = 25_990.0,
            createdAt = 1L, updatedAt = 1L,
        )
        val plan = CartolaImporter.buildPlan(parsed, mapping, existing = listOf(existente), nowMs = 99L)
        assertEquals(2, plan.nuevos.size)
        assertEquals(1, plan.duplicadosExistentes)

        // Reimportar TODO el resultado: nada nuevo (garantía de importar dos veces sin duplicar).
        val replan = CartolaImporter.buildPlan(
            parsed, mapping,
            existing = listOf(existente) + plan.nuevos.mapIndexed { i, t -> t.copy(id = 10 + i) },
            nowMs = 99L,
        )
        assertTrue(replan.nuevos.isEmpty())
        assertEquals(3, replan.duplicadosExistentes)
    }

    @Test
    fun `monto unico con signo - negativo gasto, positivo ingreso, cero ignorado`() {
        val texto = """
            Fecha,Descripcion,Monto
            01/07/2026,Sueldo empresa,1.500.000
            02/07/2026,Farmacia,-15.990
            03/07/2026,Ajuste,0
        """.trimIndent()
        val parsed = CartolaParser.parse(texto)
        assertEquals(',', parsed.separator)
        val plan = CartolaImporter.buildPlan(
            parsed, CartolaParser.guessMapping(parsed.headers), emptyList(), nowMs = 99L,
        )
        assertEquals(2, plan.nuevos.size)
        assertEquals("INCOME", plan.nuevos[0].type)
        assertEquals(1_500_000.0, plan.nuevos[0].amount, 0.001)
        assertEquals("EXPENSE", plan.nuevos[1].type)
        assertEquals(1, plan.filasIgnoradas) // monto 0
    }

    @Test
    fun `archivos invalidos fallan con mensaje claro y sin tocar nada`() {
        assertThrows(CartolaFormatException::class.java) {
            CartolaParser.parse("esto no es una cartola\nni tiene encabezados")
        }
        val parsed = CartolaParser.parse(cartola)
        assertThrows(CartolaFormatException::class.java) {
            CartolaImporter.buildPlan(
                parsed,
                CartolaMapping(fechaIndex = null, descripcionIndex = 1, montoIndex = 2, cargoIndex = null, abonoIndex = null),
                emptyList(),
            )
        }
    }
}
