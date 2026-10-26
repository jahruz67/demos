package dev.demos.graphing.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import dev.demos.graphing.math.RegressionModel

/** Empty cells are represented by undef so document sources stay valid CAS lists. */
@Serializable
data class TableOptions(
    val xHeader: String = "",
    val yHeader: String = "",
    val regression: RegressionModel? = null,
    val regressionHidden: Boolean = false,
    val regressionColor: Int = 0xFF6042A6.toInt(),
    val residuals: Boolean = false,
    val logMode: Boolean = false,
    val extraColumns: List<TableColumn> = emptyList(),
)

@Serializable
data class TableColumn(val header: String, val values: List<String> = emptyList(), val color: Int = 0xFF388C46.toInt(), val hidden: Boolean = false)

data class TableData(val x: List<String>, val y: List<String>, val options: TableOptions = TableOptions()) {
    val size: Int get() = maxOf(x.size, y.size, options.extraColumns.maxOfOrNull { it.values.size } ?: 0)
    fun header(column: Int, number: Int): String = when (column) {
        0 -> options.xHeader.ifBlank { "x_$number" }
        1 -> options.yHeader.ifBlank { "y_$number" }
        else -> options.extraColumns[column - 2].header
    }
    fun column(column: Int): List<String> = when (column) { 0 -> x; 1 -> y; else -> options.extraColumns[column - 2].values }
    val columnCount: Int get() = 2 + options.extraColumns.size
    fun withCell(row: Int, column: Int, value: String): TableData {
        val cells = MutableList(maxOf(size, row + 1)) { this.column(column).getOrElse(it) { "" } }
        cells[row] = value
        return when (column) {
            0 -> copy(x = cells)
            1 -> copy(y = cells)
            else -> copy(options = options.copy(extraColumns = options.extraColumns.mapIndexed { index, item -> if (index == column - 2) item.copy(values = cells) else item }))
        }
    }
    fun withHeader(column: Int, value: String): TableData = when (column) {
        0 -> copy(options = options.copy(xHeader = value))
        1 -> copy(options = options.copy(yHeader = value))
        else -> copy(options = options.copy(extraColumns = options.extraColumns.mapIndexed { index, item -> if (index == column - 2) item.copy(header = value) else item }))
    }
    fun withoutRow(row: Int) = copy(x = x.filterIndexed { i, _ -> i != row }, y = y.filterIndexed { i, _ -> i != row },
        options = options.copy(extraColumns = options.extraColumns.map { it.copy(values = it.values.filterIndexed { i, _ -> i != row }) }))
}

fun parseTableData(source: String): TableData? {
    val text = source.trim()
    if (!text.startsWith("table(") || !text.endsWith(')')) return null
    val parts = splitMathArguments(text.substring(6, text.length - 1))
    if (parts.size !in 2..3) return null
    fun parseList(value: String): List<String>? {
        val list = value.trim()
        if (!list.startsWith('[') || !list.endsWith(']')) return null
        val body = list.substring(1, list.length - 1)
        return if (body.isBlank()) emptyList() else splitMathArguments(body).map {
            val cell = it.trim()
            when {
                cell == "undef" -> ""
                cell.startsWith('"') -> runCatching { (Json.parseToJsonElement(cell) as JsonPrimitive).content }.getOrDefault(cell)
                else -> cell
            }
        }
    }
    val options = if (parts.size == 3) runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<TableOptions>(parts[2]) }.getOrNull() ?: return null else TableOptions()
    return TableData(parseList(parts[0]) ?: return null, parseList(parts[1]) ?: return null, options)
}

fun encodeTableData(table: TableData): String {
    // Quote unfinished expressions so typing '(' or a comma never breaks the table container.
    fun encoded(values: List<String>) = List(table.size) {
        val cell = values.getOrElse(it) { "" }.trim()
        if (cell.isEmpty()) "undef" else if (cell.toDoubleOrNull()?.isFinite() == true) cell else JsonPrimitive(cell).toString()
    }.joinToString(",")
    val options = if (table.options == TableOptions()) "" else ",${Json.encodeToString(table.options)}"
    return "table([${encoded(table.x)}],[${encoded(table.y)}]$options)"
}

fun parseNoteText(source: String): String? {
    val text = source.trim()
    if (!text.startsWith("note(") || !text.endsWith(')')) return null
    return runCatching {
        (Json.parseToJsonElement(text.substring(5, text.length - 1)) as? JsonPrimitive)?.takeIf { it.isString }?.content
    }.getOrNull()
}

fun encodeNoteText(text: String): String = "note(${JsonPrimitive(text)})"

data class FolderData(val title: String, val collapsed: Boolean = false)

fun parseFolderData(source: String): FolderData? {
    val text = source.trim()
    if (!text.startsWith("folder(") || !text.endsWith(')')) return null
    return runCatching {
        val args = Json.parseToJsonElement("[${text.substring(7, text.length - 1)}]") as JsonArray
        val title = (args.firstOrNull() as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        FolderData(title, (args.getOrNull(1) as? JsonPrimitive)?.booleanOrNull ?: false)
    }.getOrNull()
}

fun encodeFolderData(folder: FolderData): String = "folder(${JsonPrimitive(folder.title)},${folder.collapsed})"

@Serializable
data class ImageData(val uri: String, val name: String = "Image", val x: Double = 0.0, val y: Double = 0.0,
    val width: Double = 10.0, val height: Double = 10.0, val angle: Double = 0.0, val opacity: Double = 1.0)

fun parseImageData(source: String): ImageData? = source.trim().let { text ->
    if (!text.startsWith("image(") || !text.endsWith(')')) null else
        runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<ImageData>(text.substring(6, text.length - 1)) }.getOrNull()
}
fun encodeImageData(image: ImageData): String = "image(${Json.encodeToString(image)})"

@Serializable
data class InferenceData(val kind: String = "mean", val tableId: Long? = null, val column: Int = 0,
    val mean: String = "", val deviation: String = "", val count: String = "", val nullValue: String = "0",
    val confidence: String = "0.95", val alternative: String = "≠")

fun parseInferenceData(source: String): InferenceData? = source.trim().let { text ->
    if (!text.startsWith("inference(") || !text.endsWith(')')) null else
        runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<InferenceData>(text.substring(10, text.length - 1)) }.getOrNull()
}
fun encodeInferenceData(value: InferenceData): String = "inference(${Json.encodeToString(value)})"

private fun splitMathArguments(source: String): List<String> {
    val values = mutableListOf<String>()
    var depth = 0
    var start = 0
    var quoted = false
    var escaped = false
    source.forEachIndexed { index, c ->
        if (quoted) {
            if (escaped) escaped = false
            else if (c == '\\') escaped = true
            else if (c == '"') quoted = false
        } else when (c) {
            '"' -> quoted = true
            '(', '[', '{' -> depth++
            ')', ']', '}' -> depth--
            ',' -> if (depth == 0) { values += source.substring(start, index); start = index + 1 }
        }
    }
    values += source.substring(start)
    return values
}
