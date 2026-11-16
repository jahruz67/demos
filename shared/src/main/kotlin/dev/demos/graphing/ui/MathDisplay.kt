package dev.demos.graphing.ui

import dev.demos.graphing.math.LatexSyntax

/** Display-only tree: evaluation and saved documents retain the original source. */
internal sealed interface MathDisplay {
    data class Text(val value: String) : MathDisplay
    data class Row(val children: List<MathDisplay>) : MathDisplay
    data class Fraction(val numerator: MathDisplay, val denominator: MathDisplay) : MathDisplay
}

internal fun MathDisplay.hasFraction(): Boolean = when (this) {
    is MathDisplay.Fraction -> true
    is MathDisplay.Row -> children.any { it.hasFraction() }
    is MathDisplay.Text -> false
}

internal fun mathDisplay(source: String): MathDisplay {
    val normalized = runCatching { LatexSyntax.normalize(source) }.getOrDefault(source)
    return displayPart(unwrap(normalized), 0)
}

private fun displayPart(source: String, depth: Int): MathDisplay {
    val text = source.trim()
    if (depth >= 32) return MathDisplay.Text(text)
    if (text.isEmpty()) return MathDisplay.Text("□")
    var nesting = 0
    var sum = -1
    var product = -1
    text.forEachIndexed { index, c ->
        when (c) {
            '(', '[', '{' -> nesting++
            ')', ']', '}' -> nesting--
            else -> if (nesting == 0) {
                val previous = text.take(index).trimEnd().lastOrNull()
                val unary = previous == null || previous in "+-−*/÷×·^=<>:,"
                val scientificSign = c in "+-" && index >= 2 && text[index - 1] in "eE" &&
                    text[index - 2].let { it.isDigit() || it == '.' } && text.getOrNull(index + 1)?.isDigit() == true
                if (c in "=<>," || (c in "+-−" && !unary && !scientificSign)) sum = index
                if (c in "*/÷×·" && !(c == '*' && (previous == '*' || text.getOrNull(index + 1) == '*'))) product = index
            }
        }
    }
    if (sum >= 0) {
        val operatorStart = if (text[sum] == '=' && sum > 0 && text[sum - 1] in "<>!:=") sum - 1 else sum
        return MathDisplay.Row(listOf(
            displayPart(text.take(operatorStart), depth + 1), MathDisplay.Text(text.substring(operatorStart, sum + 1)),
            displayPart(text.drop(sum + 1), depth + 1),
        ))
    }
    if (product >= 0) {
        val left = text.take(product)
        val right = text.drop(product + 1)
        if (text[product] in "/÷") return MathDisplay.Fraction(
            displayPart(unwrap(left), depth + 1), displayPart(unwrap(right), depth + 1),
        )
        return MathDisplay.Row(listOf(displayPart(left, depth + 1), MathDisplay.Text(text[product].toString()), displayPart(right, depth + 1)))
    }
    // Descend into function arguments and groups, preserving their delimiters.
    val children = mutableListOf<MathDisplay>()
    var start = 0
    var open = -1
    nesting = 0
    text.forEachIndexed { index, c ->
        if (c in "([{") {
            if (nesting == 0) open = index
            nesting++
        } else if (c in ")]}") {
            nesting--
            if (nesting == 0 && open >= 0) {
                // Keep scripts together so the text renderer retains their raised/lowered baseline.
                if (open > 0 && text[open - 1] in "^_") return@forEachIndexed
                children += MathDisplay.Text(text.substring(start, open + 1))
                children += displayPart(text.substring(open + 1, index), depth + 1)
                children += MathDisplay.Text(c.toString())
                start = index + 1
            }
        }
    }
    if (children.isEmpty()) return MathDisplay.Text(text)
    if (start < text.length) children += MathDisplay.Text(text.substring(start))
    if (children.none { it.hasFraction() }) return MathDisplay.Text(text)
    return MathDisplay.Row(children)
}

private fun unwrap(source: String): String {
    var text = source.trim()
    while (text.startsWith('(') && text.endsWith(')')) {
        var depth = 0
        val enclosesAll = text.withIndex().all { (index, c) ->
            if (c == '(') depth++
            if (c == ')') depth--
            depth > 0 || index == text.lastIndex
        }
        if (!enclosesAll) break
        text = text.substring(1, text.lastIndex).trim()
    }
    return text
}
