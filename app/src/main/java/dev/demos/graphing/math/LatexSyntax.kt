package dev.demos.graphing.math

/** Native math-LaTeX normalization shared by the symbolic and plotting engines. */
object LatexSyntax {
    private val commands = mapOf(
        "pi" to "pi", "theta" to "theta", "infty" to "infinity", "cdot" to "*", "times" to "*", "div" to "/",
        "le" to "<=", "leq" to "<=", "ge" to ">=", "geq" to ">=", "ne" to "!=", "neq" to "!=",
        "sin" to "sin", "cos" to "cos", "tan" to "tan", "arcsin" to "asin", "arccos" to "acos", "arctan" to "atan",
        "sinh" to "sinh", "cosh" to "cosh", "tanh" to "tanh", "sec" to "sec", "csc" to "csc", "cot" to "cot",
        "ln" to "ln", "log" to "log10", "exp" to "exp", "int" to "∫", "partial" to "diff",
        "alpha" to "alpha", "beta" to "beta", "gamma" to "gamma", "delta" to "delta", "lambda" to "lambda",
        "mu" to "mu", "sigma" to "sigma", "omega" to "omega", "phi" to "phi", "rho" to "rho",
    )

    fun normalize(source: String): String {
        if ('\\' !in source && !source.trim().startsWith('$') && "^{" !in source && "_{" !in source) return source
        val text = source.trim().removeSurrounding("$$").removeSurrounding("$")
            .removeSurrounding("\\(", "\\)").removeSurrounding("\\[", "\\]")
        return Reader(text).read(0)
    }

    private class Reader(val source: String) {
        private var at = 0
        private fun spaces() { while (at < source.length && source[at].isWhitespace()) at++ }
        private fun group(depth: Int): String {
            spaces()
            require(at < source.length && source[at] == '{') { "Complete the LaTeX argument in braces" }
            at++
            val start = at
            var nesting = 1
            while (at < source.length && nesting > 0) {
                if (source[at] == '{') nesting++
                if (source[at] == '}') nesting--
                if (nesting > 0) at++
            }
            require(nesting == 0) { "Complete the LaTeX argument in braces" }
            val content = source.substring(start, at++)
            return Reader(content).read(depth + 1)
        }

        fun read(depth: Int): String {
            require(depth <= 64) { "LaTeX nesting is too deep" }
            val out = StringBuilder()
            while (at < source.length) {
                val c = source[at++]
                when {
                    c == '\\' -> {
                        require(at < source.length) { "Complete the LaTeX command" }
                        if (!source[at].isLetter()) {
                            val escaped = source[at++]
                            if (escaped in ",;! ") out.append(' ') else out.append(escaped)
                            continue
                        }
                        val start = at
                        while (at < source.length && source[at].isLetter()) at++
                        val name = source.substring(start, at)
                        when (name) {
                            "left", "right", "displaystyle", "textstyle" -> Unit
                            "frac", "dfrac", "tfrac" -> {
                                val numerator = group(depth)
                                val denominator = group(depth)
                                out.append("(($numerator)/($denominator))")
                            }
                            "sqrt" -> {
                                spaces()
                                val index = if (source.getOrNull(at) == '[') {
                                    val end = source.indexOf(']', at + 1)
                                    require(end >= 0) { "Complete the root index" }
                                    source.substring(at + 1, end).also { at = end + 1 }
                                } else null
                                val argument = if (source.getOrNull(at) == '(') null else group(depth)
                                if (argument == null) out.append("sqrt")
                                else if (index == null) out.append("sqrt($argument)")
                                else out.append("surd($argument,${Reader(index).read(depth + 1)})")
                            }
                            "operatorname", "mathrm", "mathit", "text" -> out.append(group(depth))
                            else -> out.append(commands[name] ?: throw IllegalArgumentException("Unsupported math LaTeX command: \\$name"))
                        }
                    }
                    (c == '^' || c == '_') && source.getOrNull(at) == '{' -> {
                        val argument = group(depth)
                        if (c == '_' && out.lastOrNull() == '∫') out.append("_{$argument}")
                        else if (c == '_') out.append('_').append(argument)
                        else out.append("^($argument)")
                    }
                    c == '{' -> { at--; out.append('(').append(group(depth)).append(')') }
                    else -> out.append(c)
                }
            }
            return out.toString()
        }
    }
}
