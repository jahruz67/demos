package dev.demos.graphing.math

/** Native math-LaTeX normalization shared by the symbolic and plotting engines. */
object LatexSyntax {
    private val commands = mapOf(
        "pi" to "pi", "theta" to "theta", "infty" to "infinity", "cdot" to "*", "times" to "*", "div" to "/",
        "le" to "<=", "leq" to "<=", "ge" to ">=", "geq" to ">=", "ne" to "!=", "neq" to "!=",
        "sin" to "sin", "cos" to "cos", "tan" to "tan", "arcsin" to "asin", "arccos" to "acos", "arctan" to "atan",
        "sinh" to "sinh", "cosh" to "cosh", "tanh" to "tanh", "sec" to "sec", "csc" to "csc", "cot" to "cot",
        "ln" to "ln", "log" to "log", "exp" to "exp", "int" to "∫", "partial" to "diff",
        "alpha" to "alpha", "beta" to "beta", "gamma" to "gamma", "delta" to "delta", "lambda" to "lambda",
        "mu" to "mu", "sigma" to "sigma", "omega" to "omega", "phi" to "phi", "rho" to "rho",
        "epsilon" to "epsilon", "varepsilon" to "epsilon", "zeta" to "zeta", "eta" to "eta", "iota" to "iota",
        "kappa" to "kappa", "nu" to "nu", "xi" to "xi", "tau" to "tau", "upsilon" to "upsilon", "chi" to "chi",
        "psi" to "psi", "varphi" to "phi", "vartheta" to "theta", "varrho" to "rho", "varsigma" to "sigma",
        "Gamma" to "Gamma", "Delta" to "Delta", "Theta" to "Theta", "Lambda" to "Lambda", "Xi" to "Xi",
        "Pi" to "Pi", "Sigma" to "Sigma", "Upsilon" to "Upsilon", "Phi" to "Phi", "Psi" to "Psi", "Omega" to "Omega",
        "asinh" to "asinh", "acosh" to "acosh", "atanh" to "atanh", "arcsinh" to "asinh", "arccosh" to "acosh", "arctanh" to "atanh",
        "min" to "min", "max" to "max", "det" to "det", "lim" to "limit",
        "vert" to "|", "lvert" to "|", "rvert" to "|", "quad" to " ", "qquad" to " ",
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

        private fun argument(depth: Int): String {
            spaces()
            require(at < source.length) { "Complete the LaTeX argument" }
            return if (source[at] == '{') group(depth) else read(depth + 1, single = true)
        }

        private fun parenthesized(depth: Int): String {
            val start = ++at
            var nesting = 1
            while (at < source.length && nesting > 0) {
                if (source[at] == '(') nesting++
                if (source[at] == ')') nesting--
                if (nesting > 0) at++
            }
            require(nesting == 0) { "Complete the function argument" }
            return Reader(source.substring(start, at++)).read(depth + 1)
        }

        fun read(depth: Int, single: Boolean = false): String {
            require(depth <= 64) { "LaTeX nesting is too deep" }
            val out = StringBuilder()
            while (at < source.length) {
                val c = source[at++]
                when {
                    c == '"' -> {
                        out.append(c)
                        while (at < source.length) {
                            val quoted = source[at++]
                            out.append(quoted)
                            if (quoted == '\\' && at < source.length) out.append(source[at++])
                            else if (quoted == '"') break
                        }
                    }
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
                            "left", "right", "displaystyle", "textstyle", "big", "Big", "bigg", "Bigg", "limits", "nolimits" -> Unit
                            "frac", "dfrac", "tfrac" -> {
                                val numerator = argument(depth)
                                val denominator = argument(depth)
                                out.append("(($numerator)/($denominator))")
                            }
                            "sqrt" -> {
                                spaces()
                                val index = if (source.getOrNull(at) == '[') {
                                    val end = source.indexOf(']', at + 1)
                                    require(end >= 0) { "Complete the root index" }
                                    source.substring(at + 1, end).also { at = end + 1 }
                                } else null
                                spaces()
                                val body = if (source.getOrNull(at) == '(') parenthesized(depth) else argument(depth)
                                if (index == null) out.append("sqrt($body)")
                                else out.append("surd($body,${Reader(index).read(depth + 1)})")
                            }
                            "log" -> {
                                spaces()
                                if (source.getOrNull(at) == '_') {
                                    at++
                                    val base = argument(depth)
                                    spaces()
                                    val value = if (source.getOrNull(at) == '(') parenthesized(depth) else argument(depth)
                                    out.append("log($value,$base)")
                                } else out.append(" log ")
                            }
                            "operatorname", "mathrm", "mathit", "text" -> out.append(' ').append(argument(depth)).append(' ')
                            else -> {
                                val command = commands[name] ?: throw IllegalArgumentException("Unsupported math LaTeX command: \\$name")
                                // Command boundaries must not merge \\pi\\alpha into pialpha.
                                if (command.firstOrNull()?.isLetter() == true) out.append(' ').append(command).append(' ')
                                else out.append(command)
                            }
                        }
                    }
                    c == '^' || c == '_' -> {
                        while (out.lastOrNull()?.isWhitespace() == true) out.setLength(out.length - 1)
                        if (source.getOrNull(at) == '{') {
                            val argument = group(depth)
                            if (c == '_' && out.lastOrNull() == '∫') out.append("_{$argument}")
                            else if (c == '_') out.append('_').append(argument)
                            else out.append("^($argument)")
                        } else out.append(c)
                    }
                    c == '{' -> { at--; out.append('{').append(group(depth)).append('}') }
                    else -> out.append(c)
                }
                if (single) break
            }
            return out.toString()
        }
    }
}
