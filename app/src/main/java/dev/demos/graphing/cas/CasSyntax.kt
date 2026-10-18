package dev.demos.graphing.cas

import dev.demos.graphing.math.LatexSyntax

/** Normalizes symbolic input for Giac; basic arithmetic also has a local exact path. */
object CasSyntax {
    data class Prepared(val expression: String, val indefiniteIntegral: Boolean, val regressionParameters: List<String> = emptyList())

    private data class LinearFit(val xs: String, val ys: String, val slope: String, val intercept: String?) {
        val parameters get() = listOfNotNull(slope, intercept)
    }

    fun prepare(input: String): Prepared {
        var text = LatexSyntax.normalize(input).trim()
            .replace("\\left", "").replace("\\right", "")
            .replace("\\cdot", "*").replace("\\times", "*")
            .replace("\\pi", "pi").replace("\\infty", "infinity")
            .replace("\\mathrm{d}", "d")
            .replace("\\ln", "ln").replace("\\sin", "sin")
            .replace("\\cos", "cos").replace("\\tan", "tan")
            .replace("\\,", " ").replace("\\!", "")
            .replace('×', '*').replace('·', '*').replace('÷', '/')
            .replace('−', '-').replace("π", "pi")
            .replace("∞", "infinity").replace("≤", "<=").replace("≥", ">=")
            .replace("≠", "!=").replace("²", "^2").replace("³", "^3")
            .replace("⁴", "^4").replace("⁵", "^5")
        text = replaceSimpleLatex(text)
        text = text.replace("\\int", "∫")
        var isIndefinite = false
        if (text.startsWith("∫")) {
            val integral = parseIntegral(text)
            text = integral.first
            isIndefinite = integral.second
        } else {
            val m = Regex("^(?:integrate|int)\\s*\\((.*)\\)$", RegexOption.DOT_MATCHES_ALL).matchEntire(text)
            if (m != null) isIndefinite = splitArguments(m.groupValues[1]).size == 2
        }
        val regression = linearFit(text)
        if (regression != null) {
            val command = fitCommand(regression)
            text = if (regression.intercept != null) "[$command]" else "[($command)[0]]"
        } else require('~' !in text) { "For a linear fit use y_1~m*x_1+b; other fits are in the Statistics functions menu" }
        require(text.isNotBlank()) { "Enter an expression" }
        require(text.length <= 16_000) { "Expression is too long" }
        validateDelimiters(text)
        return Prepared(text, isIndefinite, regression?.parameters.orEmpty())
    }

    fun normalize(input: String): String = prepare(input).expression

    /** Bind fit parameters in the same native context as the dependent expression. */
    fun regressionDefinition(input: String): String? {
        val fit = linearFit(input.trim().replace('×', '*').replace('−', '-')) ?: return null
        val command = "demos_fit:=${fitCommand(fit)}"
        return "$command;${fit.slope}:=demos_fit[0]" + (fit.intercept?.let { ";$it:=demos_fit[1]" } ?: "")
    }

    private fun linearFit(text: String): LinearFit? {
        val m = Regex("^\\s*(y_[0-9]+)\\s*~\\s*([A-Za-z][A-Za-z0-9_]*)\\s*\\*?\\s*(x_[0-9]+)\\s*(?:\\+\\s*([A-Za-z][A-Za-z0-9_]*))?\\s*$").matchEntire(text) ?: return null
        return LinearFit(m.groupValues[3], m.groupValues[1], m.groupValues[2], m.groupValues[4].ifEmpty { null })
    }

    private fun fitCommand(fit: LinearFit): String {
        if (fit.intercept != null) return "linear_regression(${fit.xs},${fit.ys})"
        // Fit constrained to pass through the origin, rather than dropping the
        // intercept after computing an unconstrained fit.
        val k = "demos_fit_index"
        val range = "$k,0,size(${fit.xs})-1"
        val slope = "sum(seq(${fit.xs}[$k]*${fit.ys}[$k],$range))/sum(seq(${fit.xs}[$k]^2,$range))"
        return "[$slope,0]"
    }

    private fun parseIntegral(text: String): Pair<String, Boolean> {
        var rest = text.removePrefix("∫").trim()
        var lower: String? = null
        var upper: String? = null
        if (rest.startsWith("_")) {
            val bound = consumeBound(rest.drop(1))
            lower = bound.first
            rest = bound.second.trim()
            require(rest.startsWith("^")) { "Enter an upper integral bound" }
            val top = consumeBound(rest.drop(1))
            upper = top.first
            rest = top.second.trim()
        }
        val differential = Regex("d\\s*([A-Za-z][A-Za-z0-9_]*)\\s*$").find(rest)
            ?: throw IllegalArgumentException("End an integral with its variable, for example dx")
        val variable = differential.groupValues[1]
        val integrand = rest.substring(0, differential.range.first).trim()
        require(integrand.isNotEmpty()) { "Enter the expression to integrate" }
        if (lower == null) return "integrate($integrand,$variable)" to true
        // Giac handles the binding scope directly, including a bound named x in
        // integrate(x^2,x,0,x). Do not rename identifiers with text replacement:
        // that would mis-handle implicit multiplication and nested integrals.
        return "integrate($integrand,$variable,$lower,$upper)" to false
    }

    private fun consumeBound(value: String): Pair<String, String> {
        val text = value.trimStart()
        require(text.isNotEmpty()) { "Enter the integral bound" }
        if (text[0] == '{' || text[0] == '(') {
            val close = if (text[0] == '{') '}' else ')'
            var depth = 0
            for (i in text.indices) {
                if (text[i] == text[0]) depth++
                if (text[i] == close && --depth == 0) {
                    val bound = text.substring(1, i).trim()
                    require(bound.isNotEmpty()) { "Enter the integral bound" }
                    return bound to text.substring(i + 1)
                }
            }
            throw IllegalArgumentException("Complete the integral bound")
        }
        val token = Regex("^[+-]?(?:[0-9]+(?:\\.[0-9]+)?|[A-Za-z][A-Za-z0-9_]*|infinity)").find(text)
            ?: throw IllegalArgumentException("Enter the integral bound")
        return token.value to text.substring(token.value.length)
    }

    internal fun splitArguments(text: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        var quoted = false
        text.forEachIndexed { index, c ->
            if (c == '"' && (index == 0 || text[index - 1] != '\\')) quoted = !quoted
            if (!quoted) {
                if (c in "([{") depth++
                if (c in ")]}") depth--
                if (c == ',' && depth == 0) { parts += text.substring(start, index).trim(); start = index + 1 }
            }
        }
        parts += text.substring(start).trim()
        return parts
    }

    private fun replaceSimpleLatex(text: String): String {
        var value = text
        // Escape closing braces explicitly; Android's regex engine rejects the
        // otherwise accepted bare '}' used by some desktop Java runtimes.
        val fraction = Regex("\\\\frac\\{([^{}]*)\\}\\{([^{}]*)\\}")
        val root = Regex("\\\\sqrt\\{([^{}]*)\\}")
        repeat(8) {
            value = value.replace(fraction) { "((${it.groupValues[1]})/(${it.groupValues[2]}))" }
            value = value.replace(root) { "sqrt(${it.groupValues[1]})" }
        }
        return value.replace(Regex("\\^\\{([^{}]*)\\}")) { "^(${it.groupValues[1]})" }
    }

    private fun validateDelimiters(text: String) {
        val stack = ArrayDeque<Char>()
        var quoted = false
        text.forEachIndexed { index, c ->
            if (c == '"' && (index == 0 || text[index - 1] != '\\')) quoted = !quoted
            if (!quoted) when (c) {
                '(', '[', '{' -> stack.addLast(c)
                ')', ']', '}' -> {
                    val opening = stack.removeLastOrNull()
                    require(opening == when (c) { ')' -> '('; ']' -> '['; else -> '{' }) { "Check matching brackets" }
                }
            }
        }
        require(!quoted && stack.isEmpty()) { "Complete the expression" }
    }
}
