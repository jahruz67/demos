package dev.demos.graphing.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.em

/** Math notation stays editable: every displayed caret maps back to its source. */
object LatexVisualTransformation : VisualTransformation {
    private val symbols = mapOf("pi" to "π", "theta" to "θ", "infty" to "∞", "times" to "×", "cdot" to "·",
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "lambda" to "λ", "mu" to "μ",
        "sigma" to "σ", "omega" to "ω", "phi" to "φ", "rho" to "ρ",
        "div" to "÷", "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥", "neq" to "≠", "ne" to "≠", "int" to "∫")
    private val functions = setOf("sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh", "ln", "log", "exp", "abs")

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val rendered = AnnotatedString.Builder()
        val original = IntArray(source.length + 1)
        val transformed = mutableListOf(0)
        fun emit(start: Int, end: Int, value: String, style: SpanStyle? = null, moveCaret: Boolean = true) {
            val before = rendered.length
            val count = end - start
            if (count > 0) for (i in 0..count) original[start + i] = before + (i.toLong() * value.length / count).toInt()
            else original[start] = before
            rendered.append(value)
            if (style != null && value.isNotEmpty()) rendered.addStyle(style, before, rendered.length)
            if (value.isEmpty()) { if (moveCaret) transformed[transformed.lastIndex] = end }
            else for (i in 1..value.length) transformed += start + (i.toLong() * count / value.length).toInt()
        }
        fun group(start: Int, limit: Int): IntRange? {
            var at = start
            while (at < limit && source[at].isWhitespace()) at++
            if (at >= limit || source[at] != '{') return null
            var depth = 1
            val open = at++
            while (at < limit) {
                if (source[at] == '{') depth++
                if (source[at] == '}' && --depth == 0) return open..at
                at++
            }
            return null
        }
        fun script(superScript: Boolean) = SpanStyle(
            baselineShift = if (superScript) BaselineShift.Superscript else BaselineShift.Subscript,
            fontSize = .8.em,
        )
        fun render(start: Int, end: Int, style: SpanStyle? = null, depth: Int = 0) {
            if (depth > 32) { emit(start, end, source.substring(start, end), style); return }
            var at = start
            while (at < end) {
                val c = source[at]
                if (c == '\\') {
                    var commandEnd = at + 1
                    while (commandEnd < end && source[commandEnd].isLetter()) commandEnd++
                    val command = source.substring(at + 1, commandEnd)
                    val argument = group(commandEnd, end)
                    if (command == "sqrt" && source.getOrNull(commandEnd) == '[') {
                        val indexEnd = source.indexOf(']', commandEnd + 1).takeIf { it in (commandEnd + 1) until end }
                        val radicand = indexEnd?.let { group(it + 1, end) }
                        if (indexEnd != null && radicand != null) {
                            emit(at, commandEnd + 1, "")
                            render(commandEnd + 1, indexEnd, script(true), depth + 1)
                            emit(indexEnd, radicand.first + 1, "√(", style)
                            if (radicand.first + 1 == radicand.last) emit(radicand.last, radicand.last, "□", style)
                            render(radicand.first + 1, radicand.last, style, depth + 1)
                            emit(radicand.last, radicand.last + 1, ")", style)
                            at = radicand.last + 1
                            continue
                        }
                    }
                    if (command in setOf("frac", "dfrac", "tfrac") && argument != null) {
                        val denominator = group(argument.last + 1, end)
                        if (denominator != null) {
                            emit(at, argument.first + 1, "")
                            if (argument.first + 1 == argument.last) emit(argument.last, argument.last, "□", script(true))
                            render(argument.first + 1, argument.last, script(true), depth + 1)
                            emit(argument.last, denominator.first + 1, "⁄", style)
                            if (denominator.first + 1 == denominator.last) emit(denominator.last, denominator.last, "□", script(false))
                            render(denominator.first + 1, denominator.last, script(false), depth + 1)
                            emit(denominator.last, denominator.last + 1, "", moveCaret = false)
                            at = denominator.last + 1
                            continue
                        }
                    }
                    if (command == "sqrt" && argument != null) {
                        emit(at, argument.first + 1, "√(", style)
                        if (argument.first + 1 == argument.last) emit(argument.last, argument.last, "□", style)
                        render(argument.first + 1, argument.last, style, depth + 1)
                        emit(argument.last, argument.last + 1, ")", style)
                        at = argument.last + 1
                        continue
                    }
                    if (command in setOf("operatorname", "mathrm", "mathit", "text") && argument != null) {
                        emit(at, argument.first + 1, "")
                        render(argument.first + 1, argument.last, SpanStyle(fontStyle = FontStyle.Normal), depth + 1)
                        emit(argument.last, argument.last + 1, "", moveCaret = false)
                        at = argument.last + 1
                        continue
                    }
                    val value = when {
                        command in setOf("left", "right", "displaystyle", "textstyle") -> ""
                        command in symbols -> symbols.getValue(command)
                        command in functions -> command
                        else -> source.substring(at, commandEnd)
                    }
                    emit(at, commandEnd, value, style)
                    at = commandEnd
                    continue
                }
                if (source.startsWith("sqrt(", at) && (at == 0 || !source[at - 1].isLetterOrDigit())) {
                    emit(at, at + 4, "√", style)
                    at += 4
                    continue
                }
                if (c == '^' || c == '_') {
                    val argument = group(at + 1, end)
                    if (argument != null) {
                        emit(at, argument.first + 1, "")
                        if (argument.first + 1 == argument.last) emit(argument.last, argument.last, "□", script(c == '^'))
                        render(argument.first + 1, argument.last, script(c == '^'), depth + 1)
                        emit(argument.last, argument.last + 1, "", moveCaret = false)
                        at = argument.last + 1
                        continue
                    }
                    var last = at + 1
                    while (last < end && source[last].isDigit()) last++
                    if (last == at + 1 && last < end && source[last].isLetter()) last++
                    if (last > at + 1) {
                        emit(at, at + 1, "")
                        render(at + 1, last, script(c == '^'), depth + 1)
                        at = last
                        continue
                    }
                }
                emit(at, at + 1, when (c) { '*' -> "×"; else -> c.toString() }, style)
                at++
            }
        }
        val wrapper = when {
            source.length >= 4 && source.startsWith("$$") && source.endsWith("$$") -> 2
            source.length >= 2 && source.startsWith('$') && source.endsWith('$') -> 1
            source.length >= 4 && ((source.startsWith("\\(") && source.endsWith("\\)")) ||
                (source.startsWith("\\[") && source.endsWith("\\]"))) -> 2
            else -> 0
        }
        if (wrapper > 0) emit(0, wrapper, "")
        render(wrapper, source.length - wrapper)
        if (wrapper > 0) emit(source.length - wrapper, source.length, "", moveCaret = false)
        return TransformedText(rendered.toAnnotatedString(), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = original[offset.coerceIn(0, source.length)]
            override fun transformedToOriginal(offset: Int) = transformed[offset.coerceIn(0, transformed.lastIndex)]
        })
    }
}
