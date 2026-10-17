package dev.demos.graphing.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull

/** Empty cells are represented by undef so document sources stay valid CAS lists. */
data class TableData(val x: List<String>, val y: List<String>) {
    val size: Int get() = maxOf(x.size, y.size)
}

fun parseTableData(source: String): TableData? {
    val text = source.trim()
    if (!text.startsWith("table(") || !text.endsWith(')')) return null
    val parts = splitMathArguments(text.substring(6, text.length - 1))
    if (parts.size != 2) return null
    fun parseList(value: String): List<String>? {
        val list = value.trim()
        if (!list.startsWith('[') || !list.endsWith(']')) return null
        val body = list.substring(1, list.length - 1)
        return if (body.isBlank()) emptyList() else splitMathArguments(body).map { if (it.trim() == "undef") "" else it.trim() }
    }
    return TableData(parseList(parts[0]) ?: return null, parseList(parts[1]) ?: return null)
}

fun encodeTableData(table: TableData): String {
    fun encoded(values: List<String>) = List(table.size) { values.getOrElse(it) { "" }.trim().ifEmpty { "undef" } }.joinToString(",")
    return "table([${encoded(table.x)}],[${encoded(table.y)}])"
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
