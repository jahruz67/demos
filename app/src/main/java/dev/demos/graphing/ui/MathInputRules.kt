package dev.demos.graphing.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** The same edits apply to touch keys, a hardware keyboard, and native text editing. */
object MathInputRules {
    private val functions = setOf("sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh",
        "asinh", "acosh", "atanh", "sec", "csc", "cot", "ln", "log", "exp", "sqrt", "abs")
    private val identifier = Regex("[A-Za-z]+$")

    fun apply(previous: TextFieldValue, proposed: TextFieldValue, knownNames: Set<String> = emptySet()): TextFieldValue {
        if (proposed.composition != null || !proposed.selection.collapsed) return proposed
        val start = previous.selection.min
        val selectionEnd = previous.selection.max
        if (proposed.text.length == previous.text.length - (selectionEnd - start) + 1 &&
            proposed.text.take(start) == previous.text.take(start) && proposed.text.drop(start + 1) == previous.text.drop(selectionEnd)) {
            when (proposed.text[start]) {
                '/', '÷' -> {
                    val from = if (start != selectionEnd) start else operandStart(previous.text, start)
                    val numerator = previous.text.substring(from, selectionEnd)
                    val replacement = "\\frac{$numerator}{}"
                    return TextFieldValue(previous.text.replaceRange(from, selectionEnd, replacement), TextRange(from + replacement.length - 1))
                }
                '^' -> return TextFieldValue(previous.text.replaceRange(start, selectionEnd, "^{}"), TextRange(start + 2))
            }
        }
        if (!previous.selection.collapsed) {
            val suffix = previous.text.drop(selectionEnd)
            if (proposed.text.startsWith(previous.text.take(start)) && proposed.text.endsWith(suffix) && proposed.text.length >= start + suffix.length) {
                val insertion = proposed.text.substring(start, proposed.text.length - suffix.length)
                val deleted = deleteSelection(previous)
                val at = deleted.selection.end
                val replacement = TextFieldValue(deleted.text.replaceRange(at, at, insertion), TextRange(at + insertion.length))
                return if (insertion.length == 1) apply(deleted, replacement, knownNames) else replacement
            }
            return proposed
        }
        val at = previous.selection.end
        if (at > 0 && proposed.text == previous.text.removeRange(at - 1, at)) return backspace(previous)
        if (proposed.text.length == previous.text.length - 1 && at > 0 && at < previous.text.length &&
            previous.text[at - 1] in "([{" && previous.text[at] == closing(previous.text[at - 1]) &&
            proposed.text == previous.text.removeRange(at - 1, at)) {
            return TextFieldValue(previous.text.removeRange(at - 1, at + 1), TextRange(at - 1))
        }
        if (proposed.text.length != previous.text.length + 1 || proposed.selection.end != at + 1 ||
            proposed.text.take(at) != previous.text.take(at) || proposed.text.drop(at + 1) != previous.text.drop(at)) return proposed
        val typed = proposed.text[at]
        if (at < previous.text.length && typed in ")]}" && previous.text[at] == typed) {
            return previous.copy(selection = TextRange(at + 1))
        }
        // Allow an explicit '(' after an auto-completed function without nesting a second pair.
        if (typed in "({" && at > 0 && at < previous.text.length && previous.text[at - 1] == typed &&
            previous.text[at] == closing(typed) && identifier.find(previous.text.take(at - 1)) != null) return previous
        if (typed in "([{") {
            return proposed.copy(text = proposed.text.substring(0, at + 1) + closing(typed) + proposed.text.substring(at + 1))
        }
        if (!typed.isLetterOrDigit()) return proposed
        // sin(|) + h becomes sinh(|), rather than sin(h|).
        if (at > 0 && previous.text.getOrNull(at - 1) == '(' && previous.text.getOrNull(at) == ')') {
            val name = identifier.find(previous.text.take(at - 1))?.value
            if (name != null && ((typed == 'h' && name + typed in functions) || (typed == '2' && name == "atan"))) {
                val next = previous.text.substring(0, at - 1) + typed + previous.text.substring(at - 1)
                return TextFieldValue(next, TextRange(at + 1))
            }
        }
        val end = proposed.selection.end
        val name = identifier.find(proposed.text.take(end)) ?: return proposed
        val latex = name.range.first > 0 && proposed.text[name.range.first - 1] == '\\'
        if (latex && name.value in setOf("frac", "dfrac", "tfrac")) {
            return proposed.copy(text = proposed.text.take(end) + "{}{}" + proposed.text.drop(end), selection = TextRange(end + 1))
        }
        // Only split conventional coordinate/constant prefixes; ordinary names such as
        // catalog and user-defined multi-letter identifiers remain intact.
        val function = if (name.value in functions) name.value else if (!latex && name.value !in knownNames) functions.sortedByDescending { it.length }
            .firstOrNull { name.value.endsWith(it) && name.value.removeSuffix(it) in setOf("x", "y", "z", "t", "u", "v", "r", "pi", "e") }
            else null
        if (function == null) return proposed
        val split = name.value.length - function.length
        val boundary = name.range.first + split
        val separated = if (split > 0) proposed.text.substring(0, boundary) + "*" + proposed.text.substring(boundary) else proposed.text
        val nextEnd = end + if (split > 0) 1 else 0
        if (separated.getOrNull(nextEnd) in listOf('(', '{')) return proposed.copy(text = separated, selection = TextRange(nextEnd))
        val pair = if (latex && function == "sqrt") "{}" else "()"
        return proposed.copy(text = separated.take(nextEnd) + pair + separated.drop(nextEnd), selection = TextRange(nextEnd + 1))
    }

    fun insertConstant(value: TextFieldValue, symbol: String): TextFieldValue {
        val base = if (value.selection.collapsed) value else deleteSelection(value)
        val at = base.selection.end
        val left = base.text.getOrNull(at - 1)
        val right = base.text.getOrNull(at)
        val before = if (left != null && (left.isLetterOrDigit() || left in ")]!}π")) "*" else ""
        val after = if (right != null && (right.isLetterOrDigit() || right in "([{\\π")) "*" else ""
        val insertion = before + symbol + after
        return TextFieldValue(base.text.replaceRange(at, at, insertion), TextRange(at + before.length + symbol.length))
    }

    fun move(value: TextFieldValue, direction: Int): TextFieldValue {
        val offsets = editableMath(value.text).caretOffsets(value.text)
        val next = if (direction < 0) offsets.lastOrNull { it < value.selection.end } else offsets.firstOrNull { it > value.selection.end }
        return value.copy(selection = TextRange(next ?: value.selection.end))
    }

    fun backspace(value: TextFieldValue): TextFieldValue {
        val at = value.selection.end
        if (!value.selection.collapsed) return deleteSelection(value)
        if (at <= 0) return value
        fun structures(node: EditableMath): List<EditableMath> = when (node) {
            is EditableMath.Row -> node.children.flatMap(::structures)
            is EditableMath.Fraction -> structures(node.top) + structures(node.bottom) + node
            is EditableMath.Script -> structures(node.body) + node
            is EditableMath.Root -> node.index?.let(::structures).orEmpty() + structures(node.body) + node
            else -> emptyList()
        }
        for (node in structures(editableMath(value.text))) {
            if (node is EditableMath.Fraction && at == node.bottom.start) {
                if (node.bottom.start == node.bottom.end) {
                    val numerator = value.text.substring(node.top.start, node.top.end)
                    return TextFieldValue(value.text.replaceRange(node.start, node.end, numerator), TextRange(node.start + numerator.length))
                }
                return value.copy(selection = TextRange(node.top.end))
            }
            val body = when (node) { is EditableMath.Script -> node.body; is EditableMath.Root -> node.body; else -> null }
            if (body != null && at == body.start && body.start == body.end) {
                return TextFieldValue(value.text.removeRange(node.start, node.end), TextRange(node.start))
            }
            if (at == node.end && value.text.getOrNull(at - 1) in listOf('}', ')')) {
                val slot = when (node) { is EditableMath.Fraction -> node.bottom.end; is EditableMath.Script -> node.body.end; is EditableMath.Root -> node.body.end; else -> at - 1 }
                return value.copy(selection = TextRange(slot))
            }
        }
        if (at < value.text.length && value.text[at - 1] in "([{" && value.text[at] == closing(value.text[at - 1])) {
            return TextFieldValue(value.text.removeRange(at - 1, at + 1), TextRange(at - 1))
        }
        // Delete a rendered LaTeX symbol as a unit.
        val command = Regex("\\\\[A-Za-z]+$").find(value.text.take(at))
        if (command != null) return TextFieldValue(value.text.removeRange(command.range.first, at), TextRange(command.range.first))
        return TextFieldValue(value.text.removeRange(at - 1, at), TextRange(at - 1))
    }

    private fun deleteSelection(value: TextFieldValue): TextFieldValue {
        val a = value.selection.min
        val b = value.selection.max
        val ranges = mutableListOf<Pair<Int, Int>>()
        fun visit(node: EditableMath) {
            if (a <= node.start && b >= node.end) { ranges += node.start to node.end; return }
            when (node) {
                is EditableMath.Text -> if (b > node.start && a < node.end) ranges += maxOf(a, node.start) to minOf(b, node.end)
                is EditableMath.Row -> node.children.forEach(::visit)
                is EditableMath.Fraction -> {
                    if (a <= node.top.start && b >= node.bottom.end) ranges += node.start to node.end
                    else { visit(node.top); visit(node.bottom) }
                }
                is EditableMath.Script -> visit(node.body)
                is EditableMath.Root -> { node.index?.let(::visit); visit(node.body) }
            }
        }
        visit(editableMath(value.text))
        var source = value.text
        for ((start, end) in ranges.sortedByDescending { it.first }) source = source.removeRange(start, end)
        val caret = ranges.minOfOrNull { it.first } ?: a
        return TextFieldValue(source, TextRange(caret.coerceIn(0, source.length)))
    }

    private fun operandStart(source: String, end: Int): Int {
        var at = end
        var depth = 0
        while (at > 0) {
            val c = source[at - 1]
            if (c in ")]}") depth++
            if (c in "([{") { if (depth == 0) break; depth-- }
            if (depth == 0 && (c in "+-−=*/÷×·,<>&|" || c.isWhitespace())) break
            at--
        }
        return at
    }

    private fun closing(opening: Char): Char = when (opening) { '(' -> ')'; '[' -> ']'; '{' -> '}'; else -> opening }
}
