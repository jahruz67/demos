package dev.demos.graphing.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** The same edits apply to touch keys, a hardware keyboard, and native text editing. */
object MathInputRules {
    private val functions = setOf("sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh",
        "asinh", "acosh", "atanh", "sec", "csc", "cot", "ln", "log", "exp", "sqrt", "abs")
    private val identifier = Regex("[A-Za-z]+$")

    fun apply(previous: TextFieldValue, proposed: TextFieldValue): TextFieldValue {
        if (proposed.composition != null || !previous.selection.collapsed || !proposed.selection.collapsed) return proposed
        val at = previous.selection.end
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
        if (name.value !in functions || proposed.text.getOrNull(end) in listOf('(', '{')) return proposed
        val pair = if (latex && name.value == "sqrt") "{}" else "()"
        return proposed.copy(text = proposed.text.take(end) + pair + proposed.text.drop(end), selection = TextRange(end + 1))
    }

    private fun closing(opening: Char): Char = when (opening) { '(' -> ')'; '[' -> ']'; '{' -> '}'; else -> opening }
}
