package dev.demos.graphing.data

import dev.demos.graphing.ui.TableData
import dev.demos.graphing.ui.TableFileFormat
import dev.demos.graphing.ui.encodeTableData
import dev.demos.graphing.cas.CasSyntax
import kotlinx.serialization.json.*

/** Two-column interchange. Cells stay expressions, and empty cells stay empty. */
object TableFiles {
    private const val MAX_ROWS = 2000

    fun encode(table: TableData, format: TableFileFormat): String {
        if (format == TableFileFormat.JSON) return buildJsonArray {
            repeat(table.size) { i -> add(buildJsonObject {
                put("x", cellJson(table.x.getOrElse(i) { "" }))
                put("y", cellJson(table.y.getOrElse(i) { "" }))
            }) }
        }.toString()
        val delimiter = if (format == TableFileFormat.TSV) '\t' else ','
        fun quote(value: String): String = if (value.any { it == delimiter || it in "\"\r\n" })
            "\"${value.replace("\"", "\"\"")}\"" else value
        return buildString {
            append("x${delimiter}y\r\n")
            repeat(table.size) { i ->
                append(quote(table.x.getOrElse(i) { "" })); append(delimiter)
                append(quote(table.y.getOrElse(i) { "" })); append("\r\n")
            }
        }
    }

    fun decode(contents: String): TableData {
        require(contents.length <= 1_000_000) { "Table file exceeds 1 MB" }
        val text = contents.removePrefix("\uFEFF").trim(' ', '\r', '\n')
        require(text.isNotEmpty()) { "The table file is empty" }
        val table = if (text.startsWith('[') || text.startsWith('{')) decodeJson(text) else decodeDelimited(text)
        require(table.size in 1..MAX_ROWS) { "A table must contain 1–$MAX_ROWS rows" }
        (table.x + table.y).filter { it.isNotBlank() }.forEach { value ->
            require(CasSyntax.splitArguments(value).size == 1) { "A table cell contains more than one expression" }
            CasSyntax.prepare(value)
        }
        require(encodeTableData(table).length <= 100_000) { "Table expressions exceed 100 KB" }
        return table
    }

    private fun cellJson(value: String): JsonElement = if (value.isBlank()) JsonNull
        else value.toBigDecimalOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(value)
    private fun cell(element: JsonElement?): String = when (element) {
        null, JsonNull -> ""
        is JsonPrimitive -> element.content.also {
            require(element.isString || element.booleanOrNull == null) { "Table cells must be numbers, strings, or null" }
            require(it.length <= 1000) { "A table cell is too long" }
        }
        else -> error("Table cells must be numbers, strings, or null")
    }

    private fun decodeJson(text: String): TableData {
        val root = Json.parseToJsonElement(text)
        if (root is JsonObject) {
            val x = root["x"] as? JsonArray ?: error("Expected x and y arrays")
            val y = root["y"] as? JsonArray ?: error("Expected x and y arrays")
            require(x.size == y.size) { "x and y columns must have equal lengths" }
            return TableData(x.map(::cell), y.map(::cell))
        }
        val rows = root as? JsonArray ?: error("Expected a JSON array")
        require(rows.size <= MAX_ROWS) { "Too many table rows" }
        val pairs = rows.map { row -> when (row) {
            is JsonArray -> { require(row.size == 2) { "Each row must have two cells" }; cell(row[0]) to cell(row[1]) }
            is JsonObject -> {
                require(row.containsKey("x") && row.containsKey("y")) { "Each row must contain x and y" }
                cell(row["x"]) to cell(row["y"])
            }
            else -> error("Expected two-cell rows or objects with x and y")
        } }
        return TableData(pairs.map { it.first }, pairs.map { it.second })
    }

    private fun decodeDelimited(text: String): TableData {
        // Detect separators outside quoted fields, including CSV cells with commas.
        var quoted = false
        val counts = mutableMapOf(',' to 0, '\t' to 0, ';' to 0)
        for (c in text) {
            if (c == '"') quoted = !quoted
            if (!quoted && c in "\r\n") break
            if (!quoted && c in counts) counts[c] = counts.getValue(c) + 1
        }
        val delimiter = counts.entries.firstOrNull { it.value == 1 }?.key ?: counts.maxByOrNull { it.value }?.key ?: ','
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        quoted = false
        var closedQuote = false
        var i = 0
        fun endField() { row += field.toString().trim(); field.setLength(0); closedQuote = false }
        fun endRow() {
            endField()
            if (row.any { it.isNotEmpty() } || row.size > 1) {
                require(row.size == 2) { "Each table row must have exactly two columns" }
                rows += row.toList()
                require(rows.size <= MAX_ROWS + 1) { "Too many table rows" }
            }
            row.clear()
        }
        while (i < text.length) {
            val c = text[i++]
            if (quoted) {
                if (c == '"') {
                    if (i < text.length && text[i] == '"') { field.append('"'); i++ }
                    else { quoted = false; closedQuote = true }
                } else field.append(c)
            } else when {
                c == delimiter -> endField()
                c == '\n' || c == '\r' -> { if (c == '\r' && i < text.length && text[i] == '\n') i++; endRow() }
                c == '"' -> { require(field.isBlank() && !closedQuote) { "Unexpected quote in table cell" }; field.setLength(0); quoted = true }
                else -> { require(!closedQuote || c.isWhitespace()) { "Unexpected text after quoted cell" }; field.append(c) }
            }
            require(field.length <= 1000) { "A table cell is too long" }
        }
        require(!quoted) { "A quoted table cell was not closed" }
        if (field.isNotEmpty() || row.isNotEmpty() || closedQuote) endRow()
        val first = rows.firstOrNull()
        val header = first?.let { cells ->
            val x = cells[0].lowercase().replace(Regex("[_0-9 ]"), "")
            val y = cells[1].lowercase().replace(Regex("[_0-9 ]"), "")
            (x == "x" && y == "y") || (rows.size > 1 && cells.all { it.toDoubleOrNull() == null } && rows[1].all { it.toDoubleOrNull() != null })
        } ?: false
        val data = if (header) rows.drop(1) else rows
        return TableData(data.map { it[0] }, data.map { it[1] })
    }
}
