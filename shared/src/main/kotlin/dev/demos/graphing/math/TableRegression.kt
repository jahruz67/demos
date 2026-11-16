package dev.demos.graphing.math

import dev.demos.graphing.ui.TableData
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.*

@Serializable
enum class RegressionModel(val label: String) {
    Linear("Linear Regression"), Quadratic("Quadratic Regression"), Cubic("Cubic Regression"),
    Quartic("Quartic Regression"), Exponential("Exponential Regression"), Logarithmic("Logarithmic Regression"),
    Power("Power Regression"), Logistic("Logistic Regression"), Sinusoidal("Sinusoidal Regression"),
}

data class RegressionFit(
    val expression: String,
    val x: List<Double>,
    val y: List<Double>,
    val predicted: List<Double>,
    val rSquared: Double,
    val correlation: Double?,
) {
    val residuals get() = y.zip(predicted) { observed, fitted -> observed - fitted }
}

/** Table cells retain their source; plotting, inference and fitting share the evaluated values. */
object TableEvaluation {
    fun columns(table: TableData, number: Int, parameters: Map<String, Double> = emptyMap(), radians: Boolean = true): List<List<Double?>> {
        val columns = mutableListOf<List<Double?>>()
        repeat(table.columnCount) { column ->
            val source = table.column(column)
            val header = table.header(column, number)
            val formula = header.substringAfter('=', header).takeIf {
                source.all(String::isBlank) && (it != header || columns.indices.any { index -> it.contains(table.header(index, number)) })
            }
            columns.add(List(table.size) { row ->
                val values = parameters.toMutableMap()
                columns.forEachIndexed { index, previous -> previous.getOrNull(row)?.let { values[table.header(index, number).substringBefore('=').trim()] = it } }
                val cell = formula ?: source.getOrElse(row) { "" }
                if (cell.isBlank()) null else runCatching {
                    val expression = NumericCompiler.compile(cell, radians, MathSyntax.Symbols(variables = values.keys))
                    if (expression.variables.any { it !in values }) Double.NaN else expression.evaluate(values)
                }.getOrNull()?.takeIf(Double::isFinite)
            })
        }
        return columns
    }

    fun pairs(table: TableData, number: Int, parameters: Map<String, Double> = emptyMap(), radians: Boolean = true): List<Pair<Double, Double>> {
        val columns = columns(table, number, parameters, radians)
        return columns[0].indices.mapNotNull { i -> columns[0][i]?.let { x -> columns[1].getOrNull(i)?.let { x to it } } }
    }
}

/** Small least-squares fits run off the UI thread and never modify the data being fitted. */
object TableRegression {
    fun fit(model: RegressionModel, pairs: List<Pair<Double, Double>>, logMode: Boolean = false): RegressionFit? = runCatching {
        require(pairs.size >= 2)
        val x = pairs.map { it.first }; val y = pairs.map { it.second }
        require(x.distinct().size >= 2)
        val formula: String
        val predict: (Double) -> Double
        when (model) {
            RegressionModel.Linear, RegressionModel.Quadratic, RegressionModel.Cubic, RegressionModel.Quartic -> {
                val degree = model.ordinal + 1
                require(x.distinct().size >= degree + 1)
                val center = x.average(); val scale = x.maxOf { abs(it - center) }.coerceAtLeast(1e-12)
                val matrix = x.map { value -> DoubleArray(degree + 1) { ((value - center) / scale).pow(it) } }
                val coefficients = leastSquares(matrix, y.toDoubleArray()) ?: return null
                predict = { value -> coefficients.indices.sumOf { coefficients[it] * ((value - center) / scale).pow(it) } }
                // Expand the normalized basis for a familiar ax² + bx + c equation.
                val expanded = DoubleArray(degree + 1)
                for (k in coefficients.indices) for (j in 0..k) {
                    var choose = 1.0
                    for (i in 1..j) choose = choose * (k - i + 1) / i
                    expanded[j] += coefficients[k] * choose * (-center).pow(k - j) / scale.pow(k)
                }
                formula = expanded.indices.reversed().joinToString("+") { i -> "(${number(expanded[i])})" + if (i == 0) "" else if (i == 1) "*x" else "*x^$i" }
            }
            RegressionModel.Logarithmic -> {
                require(x.all { it > 0 })
                val p = leastSquares(x.map { doubleArrayOf(1.0, ln(it)) }, y.toDoubleArray()) ?: return null
                predict = { p[0] + p[1] * ln(it) }
                formula = "${number(p[0])}+(${number(p[1])})*ln(x)"
            }
            RegressionModel.Exponential, RegressionModel.Power -> {
                require(y.all { it > 0 })
                if (model == RegressionModel.Power) require(x.all { it > 0 })
                val transformedX = if (model == RegressionModel.Power) x.map(::ln) else x
                val initial = leastSquares(transformedX.map { doubleArrayOf(1.0, it) }, y.map(::ln).toDoubleArray()) ?: return null
                val power = model == RegressionModel.Power
                val function: (Double, DoubleArray) -> Double = { value, p -> p[0] * exp(p[1] * if (power) ln(value) else value) }
                val p = if (logMode) doubleArrayOf(exp(initial[0]), initial[1]) else
                    nonlinear(x, y, doubleArrayOf(exp(initial[0]), initial[1]), function)
                predict = { function(it, p) }
                formula = if (power) "${number(p[0])}*x^(${number(p[1])})" else "${number(p[0])}*exp((${number(p[1])})*x)"
            }
            RegressionModel.Logistic -> {
                require(x.size >= 3 && y.max() > 0)
                val function: (Double, DoubleArray) -> Double = { value, p -> p[0] / (1.0 + exp(-(p[1] * value + p[2]).coerceIn(-700.0, 700.0))) }
                val maxY = y.max(); val span = (x.max() - x.min()).coerceAtLeast(1e-6)
                val starts = listOf(1.05, 1.5, 3.0, 10.0).flatMap { factor -> listOf(1.0, -1.0).map { sign ->
                    doubleArrayOf(maxY * factor, sign * 4 / span, -sign * 4 * x.average() / span)
                } }
                val p = starts.map { nonlinear(x, y, it, function) }.filter { it[0] > 0 }
                    .minByOrNull { error(x, y, it, function) } ?: return null
                predict = { function(it, p) }
                formula = "${number(p[0])}/(1+exp(-((${number(p[1])})*x+(${number(p[2])}))))"
            }
            RegressionModel.Sinusoidal -> {
                require(x.size >= 4)
                val span = (x.max() - x.min()).coerceAtLeast(1e-6)
                val function: (Double, DoubleArray) -> Double = { value, p -> p[0] * sin(p[1] * value + p[2]) + p[3] }
                val starts = (1..16).map { frequency ->
                    doubleArrayOf((y.max() - y.min()) / 2, PI * frequency / span, 0.0, y.average())
                }
                val p = starts.map { nonlinear(x, y, it, function) }.minBy { error(x, y, it, function) }
                predict = { function(it, p) }
                formula = "${number(p[0])}*sin((${number(p[1])})*x+(${number(p[2])}))+(${number(p[3])})"
            }
        }
        val predicted = x.map(predict)
        require(predicted.all(Double::isFinite))
        val sse = y.zip(predicted).sumOf { (a, b) -> (a - b).pow(2) }
        val mean = y.average(); val total = y.sumOf { (it - mean).pow(2) }
        val rSquared = if (total < 1e-24) if (sse < 1e-20) 1.0 else 0.0 else 1 - sse / total
        val xm = x.average()
        val denominator = sqrt(x.sumOf { (it - xm).pow(2) } * total)
        val r = if (model == RegressionModel.Linear && denominator > 0) x.zip(y).sumOf { (a, b) -> (a - xm) * (b - mean) } / denominator else null
        RegressionFit(formula, x, y, predicted, rSquared, r)
    }.getOrNull()

    private fun error(x: List<Double>, y: List<Double>, p: DoubleArray, f: (Double, DoubleArray) -> Double) =
        x.indices.sumOf { (y[it] - f(x[it], p)).pow(2) }.let { if (it.isFinite()) it else Double.POSITIVE_INFINITY }

    private fun nonlinear(x: List<Double>, y: List<Double>, initial: DoubleArray, f: (Double, DoubleArray) -> Double): DoubleArray {
        var p = initial.copyOf(); var loss = error(x, y, p, f); var damping = 0.001
        repeat(180) {
            val n = p.size
            val matrix = Array(n) { DoubleArray(n) }; val rhs = DoubleArray(n)
            x.indices.forEach { i ->
                val fitted = f(x[i], p); val residual = y[i] - fitted
                val derivative = DoubleArray(n) { j ->
                    val h = 1e-5 * max(1.0, abs(p[j])); val probe = p.copyOf(); probe[j] += h
                    (f(x[i], probe) - fitted) / h
                }
                for (a in 0 until n) {
                    rhs[a] += derivative[a] * residual
                    for (b in 0 until n) matrix[a][b] += derivative[a] * derivative[b]
                }
            }
            for (a in 0 until n) matrix[a][a] += damping * max(1e-12, matrix[a][a])
            val delta = solve(matrix, rhs) ?: return p
            val candidate = DoubleArray(n) { p[it] + delta[it] }
            val next = error(x, y, candidate, f)
            if (next < loss) {
                val improvement = loss - next; p = candidate; loss = next; damping = max(1e-12, damping / 3)
                if (improvement < 1e-20 || delta.all { abs(it) < 1e-10 }) return p
            } else damping = min(1e14, damping * 10)
        }
        return p
    }

    // Modified Gram-Schmidt avoids squaring the condition number for polynomial fits.
    private fun leastSquares(rows: List<DoubleArray>, y: DoubleArray): DoubleArray? {
        val n = rows.first().size; if (rows.size < n) return null
        val q = Array(n) { j -> DoubleArray(rows.size) { rows[it][j] } }
        val r = Array(n) { DoubleArray(n) }
        for (j in 0 until n) {
            for (k in 0 until j) {
                r[k][j] = q[j].indices.sumOf { q[k][it] * q[j][it] }
                q[j].indices.forEach { q[j][it] -= r[k][j] * q[k][it] }
            }
            r[j][j] = sqrt(q[j].sumOf { it * it }); if (r[j][j] < 1e-12) return null
            q[j].indices.forEach { q[j][it] /= r[j][j] }
        }
        val result = DoubleArray(n)
        for (j in n - 1 downTo 0) result[j] = (q[j].indices.sumOf { q[j][it] * y[it] } - (j + 1 until n).sumOf { r[j][it] * result[it] }) / r[j][j]
        return result
    }

    private fun solve(matrix: Array<DoubleArray>, rhs: DoubleArray): DoubleArray? {
        val n = rhs.size; val a = Array(n) { matrix[it].copyOf() }; val b = rhs.copyOf()
        for (j in 0 until n) {
            val pivot = (j until n).maxBy { abs(a[it][j]) }
            if (!a[pivot][j].isFinite() || abs(a[pivot][j]) < 1e-24) return null
            val swap = a[j]; a[j] = a[pivot]; a[pivot] = swap
            val value = b[j]; b[j] = b[pivot]; b[pivot] = value
            for (i in j + 1 until n) {
                val factor = a[i][j] / a[j][j]
                for (k in j until n) a[i][k] -= factor * a[j][k]
                b[i] -= factor * b[j]
            }
        }
        val result = DoubleArray(n)
        for (i in n - 1 downTo 0) result[i] = (b[i] - (i + 1 until n).sumOf { a[i][it] * result[it] }) / a[i][i]
        return result.takeIf { it.all(Double::isFinite) }
    }

    fun number(value: Double): String = if (abs(value) < 1e-13) "0" else String.format(Locale.US, "%.10g", value)
}
