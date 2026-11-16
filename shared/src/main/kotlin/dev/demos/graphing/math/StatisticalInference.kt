package dev.demos.graphing.math

import kotlin.math.*

data class InferenceResult(val estimate: Double, val statistic: Double, val probability: Double, val lower: Double, val upper: Double, val degreesOfFreedom: Int?)

object StatisticalInference {
    fun mean(mean: Double, deviation: Double, count: Int, nullValue: Double, confidence: Double, alternative: String): InferenceResult? {
        if (count < 2 || deviation <= 0 || !deviation.isFinite() || !mean.isFinite() || !nullValue.isFinite() || confidence !in .001.. .9999) return null
        val error = deviation / sqrt(count.toDouble()); val df = count - 1
        val statistic = (mean - nullValue) / error
        val cdf = student(statistic, df)
        val critical = quantile((1 + confidence) / 2) { student(it, df) }
        return InferenceResult(mean, statistic, tail(cdf, alternative), mean - critical * error, mean + critical * error, df)
    }

    fun proportion(successes: Double, count: Int, nullValue: Double, confidence: Double, alternative: String): InferenceResult? {
        if (count < 1 || !successes.isFinite() || successes % 1 != 0.0 || successes !in 0.0..count.toDouble() || nullValue <= 0 || nullValue >= 1 || confidence !in .001.. .9999) return null
        val p = successes / count
        val statistic = (p - nullValue) / sqrt(nullValue * (1 - nullValue) / count)
        val z = quantile((1 + confidence) / 2, ::normal)
        // Wilson interval remains well-behaved at zero and all successes.
        val center = (p + z * z / (2 * count)) / (1 + z * z / count)
        val radius = z * sqrt(p * (1 - p) / count + z * z / (4.0 * count * count)) / (1 + z * z / count)
        return InferenceResult(p, statistic, tail(normal(statistic), alternative), center - radius, center + radius, null)
    }

    private fun tail(cdf: Double, alternative: String) = when (alternative) { "<" -> cdf; ">" -> 1 - cdf; else -> 2 * min(cdf, 1 - cdf) }.coerceIn(0.0, 1.0)
    private fun normal(value: Double): Double {
        val x = abs(value); val t = 1 / (1 + .2316419 * x)
        val upper = exp(-x * x / 2) / sqrt(2 * PI) * t * (.319381530 + t * (-.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
        return if (value >= 0) 1 - upper else upper
    }
    private fun student(t: Double, df: Int): Double {
        val beta = regularizedBeta(df / (df + t * t), df / 2.0, .5) / 2
        return if (t >= 0) 1 - beta else beta
    }
    private fun quantile(probability: Double, cdf: (Double) -> Double): Double {
        var lo = -1e6; var hi = 1e6
        repeat(100) { val mid = (lo + hi) / 2; if (cdf(mid) < probability) lo = mid else hi = mid }
        return (lo + hi) / 2
    }
    private fun logGamma(value: Double): Double {
        val coefficients = doubleArrayOf(676.5203681218851, -1259.1392167224028, 771.3234287776531, -176.6150291621406, 12.507343278686905, -.13857109526572012, 9.984369578019572e-6, 1.5056327351493116e-7)
        if (value < .5) return ln(PI) - ln(sin(PI * value)) - logGamma(1 - value)
        val z = value - 1; var x = .9999999999998099
        coefficients.forEachIndexed { i, c -> x += c / (z + i + 1) }
        val t = z + 7.5
        return .5 * ln(2 * PI) + (z + .5) * ln(t) - t + ln(x)
    }
    private fun regularizedBeta(x: Double, a: Double, b: Double): Double {
        if (x <= 0) return 0.0; if (x >= 1) return 1.0
        if (x > (a + 1) / (a + b + 2)) return 1 - regularizedBeta(1 - x, b, a)
        val front = exp(logGamma(a + b) - logGamma(a) - logGamma(b) + a * ln(x) + b * ln1p(-x)) / a
        fun safe(v: Double) = if (abs(v) < 1e-30) 1e-30 else v
        var c = 1.0; var d = 1 / safe(1 - (a + b) * x / (a + 1)); var h = d
        for (m in 1..200) {
            val m2 = 2.0 * m
            var aa = m * (b - m) * x / ((a + m2 - 1) * (a + m2))
            d = 1 / safe(1 + aa * d); c = safe(1 + aa / c); h *= d * c
            aa = -(a + m) * (a + b + m) * x / ((a + m2) * (a + m2 + 1))
            d = 1 / safe(1 + aa * d); c = safe(1 + aa / c)
            val delta = d * c; h *= delta
            if (abs(delta - 1) < 3e-14) break
        }
        return (front * h).coerceIn(0.0, 1.0)
    }
}
