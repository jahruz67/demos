package dev.demos.graphing.math

/** Notation shared by arithmetic, plotting and Giac. Names remain whole identifiers. */
object MathSyntax {
    val unaryFunctions = setOf("sin", "cos", "tan", "asin", "acos", "atan", "arcsin", "arccos", "arctan",
        "sinh", "cosh", "tanh", "asinh", "acosh", "atanh", "sec", "csc", "cot", "ln", "log", "log2", "log10",
        "exp", "sqrt", "cbrt", "abs", "floor", "ceil", "ceiling", "round", "sign", "signum", "factorial")
    val functions = unaryFunctions + setOf("surd", "nthroot", "root", "min", "max", "mod", "pow", "hypot", "atan2",
        "integrate", "integral", "int", "diff", "derive", "simplify", "factor", "expand", "normal", "partfrac",
        "solve", "fsolve", "desolve", "limit", "if", "det", "sum", "product", "abs")
    private val coordinateNames = setOf("x", "y", "z", "t", "u", "v", "r", "theta", "pi", "e", "C")
    private val keywords = setOf("and", "or", "not", "xor", "in", "when", "then", "else", "for", "from", "to", "do", "end", "local", "return")
    private val nativeAliases = mapOf("arcsin" to "asin", "arccos" to "acos", "arctan" to "atan",
        "signum" to "sign", "integral" to "integrate", "derive" to "diff", "root" to "surd", "nthroot" to "surd")
    private val superscripts = "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻".zip("0123456789+-").toMap()
    private val subscripts = "₀₁₂₃₄₅₆₇₈₉".zip("0123456789").toMap()
    private val greek = mapOf('α' to "alpha", 'β' to "beta", 'γ' to "gamma", 'δ' to "delta", 'ε' to "epsilon",
        'ϵ' to "epsilon", 'ζ' to "zeta", 'η' to "eta", 'θ' to "theta", 'ϑ' to "theta", 'ι' to "iota",
        'κ' to "kappa", 'λ' to "lambda", 'μ' to "mu", 'µ' to "mu", 'ν' to "nu", 'ξ' to "xi", 'π' to "pi",
        'ρ' to "rho", 'ϱ' to "rho", 'σ' to "sigma", 'ς' to "sigma", 'τ' to "tau", 'υ' to "upsilon",
        'φ' to "phi", 'ϕ' to "phi", 'χ' to "chi", 'ψ' to "psi", 'ω' to "omega", 'Γ' to "Gamma",
        'Δ' to "Delta", 'Θ' to "Theta", 'Λ' to "Lambda", 'Ξ' to "Xi", 'Π' to "Pi", 'Σ' to "Sigma",
        'Υ' to "Upsilon", 'Φ' to "Phi", 'Ψ' to "Psi", 'Ω' to "Omega")

    data class Symbols(val variables: Set<String> = emptySet(), val functions: Set<String> = emptySet())
    data class Definition(val name: String, val parameters: List<String>?, val body: String) {
        val target get() = name + (parameters?.joinToString(",", "(", ")") ?: "")
    }

    fun normalize(source: String): String {
        val text = LatexSyntax.normalize(source)
        val out = StringBuilder()
        var at = 0
        while (at < text.length) {
            val c = text[at]
            if (c == '"' || c == '\'') {
                val end = quotedEnd(text, at)
                out.append(text.substring(at, end)); at = end; continue
            }
            if (c in superscripts) {
                out.append("^(")
                while (at < text.length && text[at] in superscripts) out.append(superscripts.getValue(text[at++]))
                out.append(')'); continue
            }
            if (c in subscripts) {
                if (out.lastOrNull() != '_') out.append('_')
                while (at < text.length && text[at] in subscripts) out.append(subscripts.getValue(text[at++]))
                continue
            }
            when {
                c in greek -> {
                    out.append(' ').append(greek.getValue(c))
                    if (text.getOrNull(at + 1) !in subscripts.keys && text.getOrNull(at + 1) != '_') out.append(' ')
                }
                c in "−–﹣－" -> out.append('-')
                c in "×·⋅∙" -> out.append('*')
                c == '÷' -> out.append('/')
                c == '≤' -> out.append("<=")
                c == '≥' -> out.append(">=")
                c == '≠' -> out.append("!=")
                c == '∞' -> out.append(" infinity ")
                c == '√' -> out.append("sqrt ")
                c == '［' -> out.append('[')
                c == '］' -> out.append(']')
                c == '（' -> out.append('(')
                c == '）' -> out.append(')')
                c == '＊' -> out.append('*')
                c == '／' -> out.append('/')
                c.isWhitespace() || Character.isSpaceChar(c) -> out.append(' ')
                c == '*' && text.getOrNull(at + 1) == '*' -> { out.append('^'); at++ }
                else -> out.append(c)
            }
            at++
        }
        return functionNotation(absoluteNotation(out.toString().trim()))
    }

    /** Token boundaries distinguish 2e-3 from 2*e, and v_0 from v*0. */
    fun explicitProducts(source: String, symbols: Symbols = Symbols()): String {
        val tokens = tokenize(source)
        val out = StringBuilder()
        var previous: Token? = null
        var end = 0
        for (token in tokens) {
            out.append(source.substring(end, token.start))
            val left = previous
            if (left != null && productBetween(left, token, symbols)) out.append('*')
            out.append(token.text)
            previous = token
            end = token.end
        }
        out.append(source.substring(end))
        return out.toString()
    }

    private fun productBetween(left: Token, right: Token, symbols: Symbols): Boolean {
        if (left.text in keywords || right.text in keywords) return false
        val leftAtom = left.kind in setOf(Kind.Number, Kind.Name) || left.text in setOf(")", "]", "!")
        val rightAtom = right.kind in setOf(Kind.Number, Kind.Name) || right.text == "("
        if (!leftAtom || !rightAtom) return false
        if (left.kind == Kind.Number && right.kind == Kind.Number && left.end == right.start) return false
        if (left.kind == Kind.Name) {
            if (right.text == "(") return (left.text in coordinateNames || left.text in symbols.variables) &&
                left.text !in symbols.functions && left.text !in functions
            if (left.text in unaryFunctions) return false
        }
        return true
    }

    /** Scientific exponents are part of a number, rather than symbolic variables. */
    fun isScalarNumber(source: String): Boolean {
        val tokens = tokenize(source)
        return tokens.isNotEmpty() && tokens.all {
            it.kind == Kind.Number || it.kind == Kind.Name && it.text in setOf("i", "infinity") ||
                it.kind == Kind.Operator && it.text in setOf("+", "-", "*", "/", "^", "(", ")")
        } && tokens.any { it.kind == Kind.Number || it.kind == Kind.Name }
    }

    private fun absoluteNotation(source: String): String {
        val tokens = tokenize(source)
        var opening: Token? = null
        val replacements = mutableMapOf<Int, String>()
        for ((index, token) in tokens.withIndex()) {
            if (token.text != "|") continue
            val previous = tokens.getOrNull(index - 1)
            val canClose = previous?.let { it.kind in setOf(Kind.Number, Kind.Name) || it.text in setOf(")", "]", "!") } == true
            val start = opening
            if (start != null && canClose) {
                replacements[start.start] = "abs("
                replacements[token.start] = ")"
                opening = null
            } else if (start == null) opening = token
        }
        if (replacements.isEmpty()) return source
        return buildString {
            source.forEachIndexed { index, c -> append(replacements[index] ?: c.toString()) }
        }
    }

    /** Read conventional sin^2(x) and log_2(x) without regex bracket matching. */
    private fun functionNotation(source: String, depth: Int = 0): String {
        require(depth <= 64) { "Function nesting is too deep" }
        val tokens = tokenize(source)
        fun atomEnd(start: Int): Int? {
            var at = start
            if (tokens.getOrNull(at)?.text in setOf("+", "-")) at++
            val first = tokens.getOrNull(at) ?: return null
            if (first.text == "(") {
                var nesting = 1
                at++
                while (at < tokens.size && nesting > 0) {
                    if (tokens[at].text == "(") nesting++
                    if (tokens[at].text == ")") nesting--
                    at++
                }
                return at.takeIf { nesting == 0 }
            }
            return (at + 1).takeIf { first.kind == Kind.Number || first.kind == Kind.Name }
        }
        fun powerEnd(start: Int, nesting: Int = 0): Int? {
            if (nesting > 64) return null
            val token = tokens.getOrNull(start) ?: return null
            var end = atomEnd(start) ?: return null
            if (token.kind == Kind.Name && tokens.getOrNull(end)?.text == "(" && token.text !in coordinateNames) {
                end = atomEnd(end) ?: return null
            } else if (token.text in unaryFunctions && tokens.getOrNull(end)?.text != "(") {
                end = powerEnd(end, nesting + 1) ?: return null
            }
            return if (tokens.getOrNull(end)?.text == "^") powerEnd(end + 1, nesting + 1) else end
        }
        val out = StringBuilder()
        var at = 0
        var end = 0
        while (at < tokens.size) {
            val token = tokens[at]
            out.append(source.substring(end, token.start))
            if (token.kind == Kind.Name && token.text.startsWith("log_") && token.text.length > 4 && tokens.getOrNull(at + 1)?.text == "(") {
                val argumentEnd = atomEnd(at + 1)
                if (argumentEnd != null) {
                    val argument = functionNotation(source.substring(tokens[at + 1].end, tokens[argumentEnd - 1].start), depth + 1)
                    out.append("log($argument,${token.text.drop(4)})")
                    end = tokens[argumentEnd - 1].end; at = argumentEnd; continue
                }
            }
            if (token.text in unaryFunctions && tokens.getOrNull(at + 1)?.text == "^") {
                val exponentEnd = atomEnd(at + 2)
                val argumentEnd = exponentEnd?.let { powerEnd(it) }
                if (exponentEnd != null && argumentEnd != null) {
                    val exponent = source.substring(tokens[at + 2].start, tokens[exponentEnd - 1].end)
                    val argument = functionNotation(source.substring(tokens[exponentEnd].start, tokens[argumentEnd - 1].end), depth + 1)
                    val inverse = mapOf("sin" to "asin", "cos" to "acos", "tan" to "atan",
                        "sinh" to "asinh", "cosh" to "acosh", "tanh" to "atanh")[token.text]
                    val power = exponent.filterNot { it.isWhitespace() }.removeSurrounding("(", ")")
                    if (inverse != null && power == "-1") out.append("$inverse($argument)")
                    else out.append("(${token.text}($argument))^($exponent)")
                    end = tokens[argumentEnd - 1].end; at = argumentEnd; continue
                }
            }
            if (token.text in unaryFunctions && tokens.getOrNull(at + 1)?.text != "(") {
                val argumentEnd = powerEnd(at + 1)
                if (argumentEnd != null) {
                    val argument = functionNotation(source.substring(tokens[at + 1].start, tokens[argumentEnd - 1].end), depth + 1)
                    out.append("${token.text}($argument)")
                    end = tokens[argumentEnd - 1].end; at = argumentEnd; continue
                }
            }
            out.append(token.text)
            end = token.end
            at++
        }
        out.append(source.substring(end))
        return out.toString()
    }

    fun definition(source: String): Definition? {
        val text = normalize(source)
        val tokens = tokenize(text)
        val first = tokens.firstOrNull()?.takeIf { it.kind == Kind.Name } ?: return null
        var at = 1
        var parameters: List<String>? = null
        if (tokens.getOrNull(at)?.text == "(") {
            if (first.text in functions) return null
            at++
            val names = mutableListOf<String>()
            if (tokens.getOrNull(at)?.text == ")") at++
            else {
                while (true) {
                    val parameter = tokens.getOrNull(at++)?.takeIf { it.kind == Kind.Name } ?: return null
                    names += parameter.text
                    when (tokens.getOrNull(at++)?.text) { "," -> Unit; ")" -> break; else -> return null }
                }
            }
            if (names.distinct().size != names.size) return null
            parameters = names
        }
        val operator = tokens.getOrNull(at)?.takeIf { it.text == "=" || it.text == ":=" } ?: return null
        val body = text.substring(operator.end).trim().takeIf { it.isNotEmpty() } ?: return null
        // A chained equality remains a relation, rather than binding a boolean.
        var depth = 0
        for (token in tokens.drop(at + 1)) {
            when (token.text) { "(", "[", "{" -> depth++; ")", "]", "}" -> depth-- }
            if (depth == 0 && token.text in setOf("=", ":=")) return null
        }
        return Definition(first.text, parameters, body)
    }

    fun symbols(sources: List<String>): Symbols {
        val definitions = sources.flatMap { source ->
            statements(source).mapNotNull { runCatching { definition(it) }.getOrNull() }
        }
        return Symbols(definitions.filter { it.parameters == null }.map { it.name }.toSet(),
            definitions.filter { it.parameters != null }.map { it.name }.toSet())
    }

    /** Load transitive dependencies, so an unrelated unfinished row cannot poison the CAS. */
    fun relevantDefinitions(input: String, sources: List<String>): List<String> {
        fun names(source: String): Set<String> = tokenize(normalize(source)).filter { it.kind == Kind.Name }.map { it.text }.toSet()
        fun references(source: String): Set<String> {
            val definition = runCatching { definition(source) }.getOrNull()
            return if (definition == null) names(source) else names(definition.body) - definition.parameters.orEmpty()
        }
        val providers = mutableMapOf<String, Int>()
        val dependencies = ArrayList<Set<String>>()
        val included = BooleanArray(sources.size)
        sources.forEachIndexed { index, source ->
            val parts = statements(source)
            val parsed = parts.map { runCatching { definition(it) }.getOrNull() }
            parsed.filterNotNull().forEach { providers[it.name] = index }
            dependencies += parts.flatMap { runCatching { references(it) }.getOrDefault(emptySet()) }.toSet()
            // Configuration and native context commands apply to every request.
            included[index] = parsed.any { it == null || it.name in setOf("Digits", "angle_radian", "complex_mode") }
        }
        val needed = ArrayDeque<String>()
        needed.addAll(references(input))
        included.indices.filter { included[it] }.forEach { needed.addAll(dependencies[it]) }
        val visited = mutableSetOf<String>()
        while (needed.isNotEmpty()) {
            val name = needed.removeFirst()
            if (!visited.add(name)) continue
            val index = providers[name] ?: continue
            if (!included[index]) {
                included[index] = true
                needed.addAll(dependencies[index])
            }
        }
        return sources.filterIndexed { index, _ -> included[index] }
    }

    private fun statements(source: String): List<String> {
        var depth = 0
        var start = 0
        val result = mutableListOf<String>()
        for (token in tokenize(source)) {
            when (token.text) { "(", "[", "{" -> depth++; ")", "]", "}" -> depth-- }
            if (depth == 0 && token.text == ";") {
                result += source.substring(start, token.start)
                start = token.end
            }
        }
        if (source.substring(start).isNotBlank()) result += source.substring(start)
        return result
    }

    /** Canonical logarithms: log(x) is base ten; log(x,b) has an explicit base. */
    fun giacFunctions(source: String, nesting: Int = 0): String {
        require(nesting <= 64) { "Function nesting is too deep" }
        val tokens = tokenize(source)
        val out = StringBuilder()
        var end = 0
        var at = 0
        while (at < tokens.size) {
            val token = tokens[at]
            out.append(source.substring(end, token.start))
            val call = tokens.getOrNull(at + 1)?.text == "("
            if (token.text in setOf("log", "log2", "cbrt", "pow", "hypot") && call) {
                var depth = 0
                var close = at + 1
                val commas = mutableListOf<Int>()
                while (close < tokens.size) {
                    val t = tokens[close].text
                    if (t in setOf("(", "[", "{")) depth++
                    if (t in setOf(")", "]", "}")) depth--
                    if (depth == 1 && t == ",") commas += close
                    if (depth == 0) break
                    close++
                }
                if (close < tokens.size) {
                    var argumentStart = tokens[at + 1].end
                    val arguments = (commas + close).map { index ->
                        giacFunctions(source.substring(argumentStart, tokens[index].start), nesting + 1).also {
                            argumentStart = tokens[index].end
                        }
                    }
                    val a = arguments.first()
                    val b = arguments.getOrNull(1)
                    val replacement = when {
                        token.text == "log" && arguments.size == 1 -> "log10($a)"
                        token.text == "log" && arguments.size == 2 -> "(ln($a)/ln($b))"
                        token.text == "log2" && arguments.size == 1 -> "(ln($a)/ln(2))"
                        token.text == "cbrt" && arguments.size == 1 -> "surd($a,3)"
                        token.text == "pow" && arguments.size == 2 -> "(($a)^($b))"
                        token.text == "hypot" && arguments.size == 2 -> "sqrt(($a)^2+($b)^2)"
                        else -> null
                    }
                    if (replacement != null) {
                        out.append(replacement)
                        end = tokens[close].end; at = close + 1; continue
                    }
                }
            }
            out.append(if (call) nativeAliases[token.text] ?: token.text else token.text)
            end = token.end
            at++
        }
        out.append(source.substring(end))
        return out.toString()
    }

    private enum class Kind { Number, Name, Quoted, Operator }
    private data class Token(val text: String, val start: Int, val end: Int, val kind: Kind)
    private fun quotedEnd(source: String, start: Int): Int {
        var at = start + 1
        while (at < source.length) {
            if (source[at] == '\\') { at = minOf(at + 2, source.length); continue }
            if (source[at++] == source[start]) return at
        }
        // A lone apostrophe can also be native derivative/quote notation.
        return if (source[start] == '\'') start + 1 else source.length
    }

    private fun tokenize(source: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var at = 0
        while (at < source.length) {
            if (source[at].isWhitespace()) { at++; continue }
            val start = at
            val c = source[at++]
            val kind = when {
                c == '"' || c == '\'' -> { at = quotedEnd(source, start); Kind.Quoted }
                c.isDigit() || c == '.' && source.getOrNull(at)?.isDigit() == true -> {
                    while (at < source.length && source[at].isDigit()) at++
                    if (c != '.' && source.getOrNull(at) == '.') { at++; while (at < source.length && source[at].isDigit()) at++ }
                    if (source.getOrNull(at) in listOf('e', 'E')) {
                        var exponent = at + 1
                        if (source.getOrNull(exponent) in listOf('+', '-')) exponent++
                        if (source.getOrNull(exponent)?.isDigit() == true) {
                            at = exponent + 1
                            while (at < source.length && source[at].isDigit()) at++
                        }
                    }
                    Kind.Number
                }
                c.isLetter() || c == '_' -> {
                    while (at < source.length && (source[at].isLetterOrDigit() || source[at] == '_')) at++
                    Kind.Name
                }
                else -> {
                    if (source.substring(start, minOf(start + 2, source.length)) in setOf(":=", "<=", ">=", "!=", "==", "->", "..", "&&", "||")) at++
                    Kind.Operator
                }
            }
            tokens += Token(source.substring(start, at), start, at, kind)
        }
        return tokens
    }
}
