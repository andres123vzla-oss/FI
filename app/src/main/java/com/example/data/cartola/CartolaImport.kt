package com.example.data.cartola

import com.example.data.entity.TransactionEntity
import java.nio.charset.Charset
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/**
 * El archivo no parece una cartola utilizable (sin encabezados reconocibles, etc.).
 * El mensaje está pensado para mostrarse tal cual al usuario.
 */
class CartolaFormatException(message: String) : Exception(message)

/**
 * Mapeo de columnas de la cartola → campos del movimiento. Lo adivina [CartolaParser.guessMapping]
 * y lo confirma/corrige el usuario en el diálogo de mapeo.
 *
 * Dos modos excluyentes de monto:
 *  - [montoIndex]: una sola columna CON SIGNO (negativo = gasto, positivo = ingreso).
 *  - [cargoIndex]/[abonoIndex]: columnas separadas del formato bancario chileno
 *    (cargo = sale plata = gasto; abono = entra plata = ingreso).
 */
data class CartolaMapping(
    val fechaIndex: Int?,
    val descripcionIndex: Int?,
    val montoIndex: Int?,
    val cargoIndex: Int?,
    val abonoIndex: Int?,
    val categoryName: String = "Otros",
)

/**
 * Parser PURO de cartolas bancarias chilenas en CSV (P1-2). Cero Android: test JUnit plano.
 *
 * Robustez pensada para archivos reales de banco:
 *  - Codificación: UTF-8 y, si trae caracteres rotos, Windows-1252 (la clásica de los bancos).
 *  - Separador: detecta ';' (convención chilena), ',' o tabulador.
 *  - Preámbulo: se salta las filas previas ("Cartola cuenta…", rangos de fechas, vacías) hasta
 *    encontrar la fila de ENCABEZADOS por palabras clave (fecha + monto/cargo/abono).
 *  - Comillas CSV: separadores dentro de comillas no cortan la celda; "" escapa la comilla.
 *  - Montos CLP: punto = miles, coma = decimal ("$ 1.234.567,89"); negativos con '-' o
 *    paréntesis. (Convención chilena a propósito: "1,234.56" estilo US no está soportado.)
 *  - Fechas: dd/MM/yyyy, dd-MM-yyyy, yyyy-MM-dd y dd/MM/yy → salida "YYYY-MM-DD" (la de la app),
 *    validando que el día exista en el mes.
 */
object CartolaParser {

    /** Resultado del análisis: encabezados + filas de datos (sin preámbulo ni encabezado). */
    data class Parsed(
        val headers: List<String>,
        val rows: List<List<String>>,
        val separator: Char,
        val headerRowIndex: Int,
    )

    /** Decodifica bytes del archivo: UTF-8, o Windows-1252 si el UTF-8 vino corrupto. */
    fun decode(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('�')) String(bytes, Charset.forName("windows-1252")) else utf8
    }

    /** Analiza el texto completo. @throws CartolaFormatException si no hay encabezados. */
    fun parse(text: String): Parsed {
        val lines = text.split('\n').map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        if (lines.isEmpty()) throw CartolaFormatException("El archivo está vacío.")
        val sep = detectSeparator(lines.take(10))
        val allRows = lines.map { parseLine(it, sep) }
        val headerIndex = findHeaderRow(allRows)
        if (headerIndex < 0) {
            throw CartolaFormatException(
                "No se encontró la fila de encabezados. La cartola debe tener columnas como " +
                    "Fecha y Monto (o Cargo/Abono).",
            )
        }
        val headers = allRows[headerIndex].map { it.trim() }
        val rows = allRows.drop(headerIndex + 1).filter { row -> row.any { it.isNotBlank() } }
        return Parsed(headers = headers, rows = rows, separator = sep, headerRowIndex = headerIndex)
    }

    /** Detecta el separador contando ocurrencias en las primeras líneas; empate → ';'. */
    fun detectSeparator(lines: List<String>): Char {
        val candidates = listOf(';', ',', '\t')
        val counts = candidates.associateWith { c -> lines.sumOf { line -> line.count { it == c } } }
        val best = counts.maxByOrNull { it.value } ?: return ';'
        if (best.value == 0) return ';'
        // Preferencia chilena: si ';' está presente con conteo comparable, gana ante ','.
        return if (best.key == ',' && counts[';']!! >= counts[',']!!) ';' else best.key
    }

    /** Parseo CSV de una línea con soporte de comillas (separador dentro de comillas no corta). */
    fun parseLine(line: String, separator: Char): List<String> {
        val out = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == separator && !inQuotes -> {
                    out.add(cell.toString())
                    cell.setLength(0)
                }
                else -> cell.append(c)
            }
            i++
        }
        out.add(cell.toString())
        return out
    }

    private val FECHA_KEYS = listOf("fecha")
    private val DESC_KEYS = listOf("descripcion", "detalle", "glosa", "movimiento", "concepto", "observacion")
    private val CARGO_KEYS = listOf("cargo", "debito", "giro")
    private val ABONO_KEYS = listOf("abono", "credito", "deposito")
    private val MONTO_KEYS = listOf("monto", "importe", "valor")

    /** Fila de encabezados = primera con una celda "fecha" y alguna de monto/cargo/abono. */
    fun findHeaderRow(rows: List<List<String>>): Int {
        rows.take(25).forEachIndexed { index, row ->
            val cells = row.map { normalize(it) }
            val hasFecha = cells.any { cell -> FECHA_KEYS.any { cell.contains(it) } }
            val hasMonto = cells.any { cell ->
                (CARGO_KEYS + ABONO_KEYS + MONTO_KEYS).any { cell.contains(it) }
            }
            if (hasFecha && hasMonto) return index
        }
        return -1
    }

    /** Adivina el mapeo por palabras clave de los encabezados (el usuario lo confirma). */
    fun guessMapping(headers: List<String>): CartolaMapping {
        val cells = headers.map { normalize(it) }
        fun firstMatch(keys: List<String>): Int? =
            cells.indexOfFirst { cell -> keys.any { cell.contains(it) } }.takeIf { it >= 0 }
        val cargo = firstMatch(CARGO_KEYS)
        val abono = firstMatch(ABONO_KEYS)
        return CartolaMapping(
            fechaIndex = firstMatch(FECHA_KEYS),
            descripcionIndex = firstMatch(DESC_KEYS),
            // Con cargo/abono presentes se ignora "monto": suele ser el saldo u otra columna.
            montoIndex = if (cargo == null && abono == null) firstMatch(MONTO_KEYS) else null,
            cargoIndex = cargo,
            abonoIndex = abono,
        )
    }

    /**
     * Monto CLP chileno → Double. "" o basura → null (la fila se reporta como ignorada).
     * Soporta "$ 1.234.567", "25.990", "1.234,56", "-12.500", "(5.000)".
     */
    fun parseAmountClp(raw: String): Double? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true
            s = s.substring(1, s.length - 1)
        }
        s = s.replace("$", "").replace("CLP", "", ignoreCase = true)
            .replace(" ", "").replace(" ", "")
        if (s.startsWith("-")) {
            negative = true
            s = s.substring(1)
        }
        if (s.endsWith("-")) {
            negative = true
            s = s.dropLast(1)
        }
        if (s.isEmpty()) return null
        // Convención chilena: puntos = miles, coma = decimal.
        s = s.replace(".", "").replace(',', '.')
        val value = s.toDoubleOrNull() ?: return null
        if (!value.isFinite()) return null
        return if (negative) -value else value
    }

    private val DMY = Regex("""^(\d{1,2})[/-](\d{1,2})[/-](\d{4})$""")
    private val YMD = Regex("""^(\d{4})[/-](\d{1,2})[/-](\d{1,2})$""")
    private val DMY2 = Regex("""^(\d{1,2})[/-](\d{1,2})[/-](\d{2})$""")

    /** Fecha de cartola → "YYYY-MM-DD" (convención dd/MM chilena), o null si no es válida. */
    fun parseDate(raw: String): String? {
        val s = raw.trim()
        val (y, m, d) = when {
            DMY.matches(s) -> DMY.find(s)!!.destructured.let { (dd, mm, yy) ->
                Triple(yy.toInt(), mm.toInt(), dd.toInt())
            }
            YMD.matches(s) -> YMD.find(s)!!.destructured.let { (yy, mm, dd) ->
                Triple(yy.toInt(), mm.toInt(), dd.toInt())
            }
            DMY2.matches(s) -> DMY2.find(s)!!.destructured.let { (dd, mm, yy) ->
                Triple(2000 + yy.toInt(), mm.toInt(), dd.toInt())
            }
            else -> return null
        }
        if (m !in 1..12) return null
        if (d !in 1..daysInMonth(y, m)) return null
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }

    private fun daysInMonth(year: Int, month: Int): Int {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    /** Normaliza para comparar: minúsculas, sin tildes, espacios colapsados. */
    fun normalize(text: String): String = text.lowercase(Locale.ROOT)
        .replace('á', 'a').replace('é', 'e').replace('í', 'i')
        .replace('ó', 'o').replace('ú', 'u').replace('ñ', 'n')
        .trim().replace(Regex("\\s+"), " ")
}

/**
 * Convierte filas parseadas + mapeo confirmado en un PLAN de importación (P1-2). Puro y
 * determinista; la inserción real la hace el DAO en una transacción.
 *
 * Duplicados (la garantía clave: importar dos veces la misma cartola no duplica nada):
 * la huella de un movimiento es fecha + monto + tipo + descripción normalizada. Se omiten
 * tanto los que ya existen en la app como los repetidos dentro del propio archivo.
 */
object CartolaImporter {

    data class Plan(
        val nuevos: List<TransactionEntity>,
        val duplicadosExistentes: Int,
        val duplicadosEnArchivo: Int,
        val filasIgnoradas: Int,
    )

    fun buildPlan(
        parsed: CartolaParser.Parsed,
        mapping: CartolaMapping,
        existing: List<TransactionEntity>,
        nowMs: Long = System.currentTimeMillis(),
    ): Plan {
        val fechaIndex = mapping.fechaIndex
            ?: throw CartolaFormatException("Debes indicar cuál columna es la Fecha.")
        if (mapping.montoIndex == null && mapping.cargoIndex == null && mapping.abonoIndex == null) {
            throw CartolaFormatException("Debes indicar la columna de Monto (o Cargo/Abono).")
        }

        val existingKeys = existing
            .map { fingerprint(it.date, it.amount, it.type, it.description) }
            .toHashSet()
        val seenKeys = HashSet<String>()
        val nuevos = ArrayList<TransactionEntity>()
        var dupExisting = 0
        var dupFile = 0
        var ignoradas = 0

        for (row in parsed.rows) {
            val fecha = CartolaParser.parseDate(row.getOrNull(fechaIndex).orEmpty())
            if (fecha == null) {
                ignoradas++
                continue
            }

            val desc = mapping.descripcionIndex?.let { row.getOrNull(it) }?.trim()
                .orEmpty().ifEmpty { "Movimiento de cartola" }

            val resolved = resolveAmount(row, mapping)
            if (resolved == null) {
                ignoradas++
                continue
            }
            val (type, amount) = resolved

            val key = fingerprint(fecha, amount, type, desc)
            when {
                key in existingKeys -> dupExisting++
                key in seenKeys -> dupFile++
                else -> {
                    seenKeys.add(key)
                    nuevos.add(
                        TransactionEntity(
                            type = type,
                            date = fecha,
                            categoryName = mapping.categoryName,
                            description = desc,
                            amount = amount,
                            createdAt = nowMs,
                            updatedAt = nowMs,
                        ),
                    )
                }
            }
        }
        return Plan(nuevos, dupExisting, dupFile, ignoradas)
    }

    /** (tipo, monto absoluto) de la fila según el modo del mapeo, o null si no hay monto usable. */
    private fun resolveAmount(row: List<String>, mapping: CartolaMapping): Pair<String, Double>? {
        mapping.montoIndex?.let { idx ->
            val v = CartolaParser.parseAmountClp(row.getOrNull(idx).orEmpty()) ?: return null
            if (v == 0.0) return null
            return (if (v < 0) "EXPENSE" else "INCOME") to abs(v)
        }
        val cargo = mapping.cargoIndex?.let { CartolaParser.parseAmountClp(row.getOrNull(it).orEmpty()) }
        if (cargo != null && cargo != 0.0) return "EXPENSE" to abs(cargo)
        val abono = mapping.abonoIndex?.let { CartolaParser.parseAmountClp(row.getOrNull(it).orEmpty()) }
        if (abono != null && abono != 0.0) return "INCOME" to abs(abono)
        return null
    }

    private fun fingerprint(date: String, amount: Double, type: String, description: String): String =
        "$date|${String.format(Locale.US, "%.2f", amount)}|$type|${CartolaParser.normalize(description)}"
}
