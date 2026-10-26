package dev.demos.graphing.cas

import javagiac.giacJNI
import dev.demos.graphing.math.MathSyntax

data class CasResult(
    val input: String,
    val exact: String,
    val approximate: String = "",
    val error: String? = null,
    val plotExpression: String? = null,
)

/** Genuine Giac evaluation. Call only from the isolated CAS process worker. */
internal object NativeGiac {
    @Volatile private var loaded = false

    @Synchronized fun load() {
        if (!loaded) {
            System.loadLibrary("c++_shared")
            System.loadLibrary("javagiac")
            loaded = true
        }
    }

    fun evaluate(input: String, definitions: List<String> = emptyList()): CasResult {
        load()
        val requiredDefinitions = MathSyntax.relevantDefinitions(input, definitions)
        val symbols = MathSyntax.symbols(requiredDefinitions)
        val prepared = CasSyntax.prepare(input, symbols)
        val context = giacJNI.new_context__SWIG_0()
        check(context != 0L) { "Could not create the CAS context" }
        try {
            // A fresh context ensures deleted/edited definitions never leak into later results.
            requiredDefinitions.forEach { definition ->
                val value = evaluatePointer(CasSyntax.normalize(definition, symbols), context)
                try {
                    val error = errorMessage(value, context)
                    require(error == null) { "Definition: $error" }
                } finally { giacJNI.delete_gen(value) }
            }
            val value = evaluatePointer(prepared.expression, context)
            try {
                val error = errorMessage(value, context)
                if (error != null) return CasResult(input, "", error = error)
                val raw = formatGiacOutput(giacJNI.gen_print__SWIG_0(value, null, context, null).trim())
                if (prepared.regressionParameters.isNotEmpty()) {
                    val coefficients = CasSyntax.splitArguments(raw.removePrefix("[").removeSuffix("]"))
                    require(coefficients.size == prepared.regressionParameters.size) { "The regression could not determine its parameters" }
                    val formula = "y=(${coefficients[0]})*x+(${coefficients.getOrElse(1) { "0" }})"
                    return CasResult(input, prepared.regressionParameters.zip(coefficients).joinToString(", ") { (name, coefficient) -> "$name=$coefficient" }, plotExpression = formula)
                }
                val exact = if (prepared.indefiniteIntegral && !raw.contains("integrate(") && !raw.contains("int(")) "$raw + C" else raw
                var approximate = ""
                if (!prepared.indefiniteIntegral) {
                    val decimal = giacJNI.gen_evalf(value, null, 1, context, null)
                    try {
                        val printed = giacJNI.gen_print__SWIG_0(decimal, null, context, null).trim()
                        // Show a decimal only for a scalar constant, not a rounded symbolic formula.
                        if (printed != raw && MathSyntax.isScalarNumber(printed)) {
                            approximate = printed
                        }
                    } finally { giacJNI.delete_gen(decimal) }
                }
                return CasResult(input, exact, approximate)
            } finally { giacJNI.delete_gen(value) }
        } finally { giacJNI.delete_context(context) }
    }

    private fun evaluatePointer(expression: String, context: Long): Long {
        val parsed = giacJNI.new_gen__SWIG_25(expression, context, null)
        check(parsed != 0L) { "Could not parse the expression" }
        try {
            return giacJNI.gen_eval(parsed, null, 1, context, null).also {
                check(it != 0L) { "The CAS returned no result" }
            }
        } finally { giacJNI.delete_gen(parsed) }
    }

    /** Giac prints a leading '?' for square roots in its compact text format. */
    private fun formatGiacOutput(source: String): String =
        Regex("[?√]([A-Za-z0-9_]+)").replace(source) { "sqrt(${it.groupValues[1]})" }

    private fun errorMessage(value: Long, context: Long): String? {
        val text = giacJNI.gen_print__SWIG_0(value, null, context, null).trim()
        if (giacJNI.gen_type_get(value, null).toInt() == 12 && giacJNI.gen_subtype_get(value, null).toInt() == -1) {
            return text.trim('"').take(500)
        }
        return when {
            text.equals("undef", ignoreCase = true) -> "Undefined result. Check the expression and its domain."
            text.contains("error", ignoreCase = true) && text.startsWith('"') -> text.trim('"').take(500)
            else -> null
        }
    }
}
