package dev.demos.graphing.ui

/** Every visible slot retains offsets in the document, including empty LaTeX arguments. */
internal sealed interface EditableMath {
    val start: Int
    val end: Int
    data class Text(override val start: Int, override val end: Int) : EditableMath
    data class Row(val children: List<EditableMath>, override val start: Int, override val end: Int) : EditableMath
    data class Fraction(val top: EditableMath, val bottom: EditableMath, override val start: Int, override val end: Int) : EditableMath
    data class Script(val body: EditableMath, val raised: Boolean, override val start: Int, override val end: Int) : EditableMath
    data class Root(val body: EditableMath, override val start: Int, override val end: Int, val index: EditableMath? = null) : EditableMath
}

internal fun editableMath(source: String): EditableMath = EditableMathReader(source).read(0, source.length)

private class EditableMathReader(val source: String) {
    private fun close(open: Int, limit: Int): Int? {
        val closing = when (source[open]) { '(' -> ')'; '[' -> ']'; '{' -> '}'; else -> return null }
        var depth = 0
        for (i in open until limit) {
            if (source[i] == source[open]) depth++
            if (source[i] == closing) { depth--; if (depth == 0) return i }
        }
        return null
    }

    private fun unwrapped(start: Int, end: Int): Pair<Int, Int> {
        var a = start
        var b = end
        while (a < b && source[a] == '(' && close(a, b) == b - 1) { a++; b-- }
        return a to b
    }

    fun read(start: Int, end: Int, depth: Int = 0): EditableMath {
        if (start >= end || depth > 40) return EditableMath.Text(start, end)
        var level = 0
        var sum = -1
        var product = -1
        for (i in start until end) {
            val c = source[i]
            when (c) {
                '(', '[', '{' -> level++
                ')', ']', '}' -> level--
                else -> if (level == 0) {
                    val previous = source.substring(start, i).trimEnd().lastOrNull()
                    val unary = previous == null || previous in "+-*/÷×·^=<>:,"
                    val scientific = c in "+-" && i >= start + 2 && source[i - 1] in "eE" && source[i - 2].isDigit()
                    if (c in "=<>," || c in "+-−" && !unary && !scientific) sum = i
                    if (c in "/÷*×·" && !(c == '*' && (previous == '*' || source.getOrNull(i + 1) == '*'))) product = i
                }
            }
        }
        if (sum >= 0) {
            val op = if (source[sum] == '=' && sum > start && source[sum - 1] in "<>!:=") sum - 1 else sum
            return EditableMath.Row(listOf(read(start, op, depth + 1), EditableMath.Text(op, sum + 1), read(sum + 1, end, depth + 1)), start, end)
        }
        if (product >= 0) {
            if (source[product] in "/÷") {
                val (a, b) = unwrapped(start, product)
                val (c, d) = unwrapped(product + 1, end)
                return EditableMath.Fraction(read(a, b, depth + 1), read(c, d, depth + 1), start, end)
            }
            return EditableMath.Row(listOf(read(start, product, depth + 1), EditableMath.Text(product, product + 1), read(product + 1, end, depth + 1)), start, end)
        }
        val children = mutableListOf<EditableMath>()
        var textStart = start
        var at = start
        fun flush(to: Int) { if (textStart < to) children += EditableMath.Text(textStart, to) }
        while (at < end) {
            if (source[at] == '\\') {
                var commandEnd = at + 1
                while (commandEnd < end && source[commandEnd].isLetter()) commandEnd++
                val name = source.substring(at + 1, commandEnd)
                var arg = commandEnd
                while (arg < end && source[arg].isWhitespace()) arg++
                var rootIndex: EditableMath? = null
                if (name == "sqrt" && arg < end && source[arg] == '[') {
                    val indexEnd = close(arg, end)
                    if (indexEnd != null) {
                        rootIndex = read(arg + 1, indexEnd, depth + 1)
                        arg = indexEnd + 1
                        while (arg < end && source[arg].isWhitespace()) arg++
                    }
                }
                val argEnd = if (arg < end && source[arg] == '{') close(arg, end) else null
                if (name in setOf("frac", "dfrac", "tfrac") && argEnd != null) {
                    var denominator = argEnd + 1
                    while (denominator < end && source[denominator].isWhitespace()) denominator++
                    val denEnd = if (denominator < end && source[denominator] == '{') close(denominator, end) else null
                    if (denEnd != null) {
                        flush(at)
                        children += EditableMath.Fraction(read(arg + 1, argEnd, depth + 1), read(denominator + 1, denEnd, depth + 1), at, denEnd + 1)
                        at = denEnd + 1; textStart = at; continue
                    }
                }
                if (name == "sqrt" && argEnd != null) {
                    flush(at)
                    children += EditableMath.Root(read(arg + 1, argEnd, depth + 1), at, argEnd + 1, rootIndex)
                    at = argEnd + 1; textStart = at; continue
                }
                // A command is one text token; do not split its letters into variables.
                at = commandEnd.coerceAtLeast(at + 1)
                continue
            }
            if (source.startsWith("sqrt(", at)) {
                val last = close(at + 4, end)
                if (last != null) {
                    flush(at)
                    children += EditableMath.Root(read(at + 5, last, depth + 1), at, last + 1)
                    at = last + 1; textStart = at; continue
                }
            }
            if (source[at] in "^_" && at + 1 < end) {
                val open = at + 1
                val last = if (source[open] in "{(") close(open, end) else null
                val bodyStart = if (last != null) open + 1 else open
                var bodyEnd = last ?: (open + 1)
                if (last == null) {
                    while (bodyEnd < end && (source[bodyEnd].isDigit() || source[bodyEnd] == '.')) bodyEnd++
                }
                flush(at)
                children += EditableMath.Script(read(bodyStart, bodyEnd, depth + 1), source[at] == '^', at, if (last != null) last + 1 else bodyEnd)
                at = if (last != null) last + 1 else bodyEnd; textStart = at; continue
            }
            if (source[at] in "([{") {
                val last = close(at, end)
                if (last != null) {
                    flush(at + 1)
                    children += read(at + 1, last, depth + 1)
                    children += EditableMath.Text(last, last + 1)
                    at = last + 1; textStart = at; continue
                }
            }
            at++
        }
        flush(end)
        return if (children.size == 1) children[0] else if (children.isEmpty()) EditableMath.Text(start, end) else EditableMath.Row(children, start, end)
    }
}
