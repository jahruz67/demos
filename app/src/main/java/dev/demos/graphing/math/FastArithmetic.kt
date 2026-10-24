package dev.demos.graphing.math

import dev.demos.graphing.cas.CasResult
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/** Bounded, exact arithmetic without starting the native symbolic process. */
object FastArithmetic {
    fun evaluate(source: String, digits: Int): CasResult? {
        if (source.length > 512) return null
        val normalized = runCatching { LatexSyntax.normalize(source) }.getOrNull() ?: return null
        if (normalized.any { it !in "0123456789.+-*/^() \t\n\r−×÷" }) return null
        return runCatching {
            val value = Parser(normalized.replace('−', '-').replace('×', '*').replace('÷', '/')).parse()
            val exact = if (value.d == BigInteger.ONE) value.n.toString() else "${value.n}/${value.d}"
            val approximate = if (value.d == BigInteger.ONE) "" else
                BigDecimal(value.n).divide(BigDecimal(value.d), MathContext(digits.coerceIn(10, 1000)))
                    .stripTrailingZeros().toString()
            CasResult(source, exact, approximate)
        }.getOrNull()
    }

    private data class Fraction(val n: BigInteger, val d: BigInteger) {
        operator fun plus(b: Fraction) = fraction(n * b.d + b.n * d, d * b.d)
        operator fun minus(b: Fraction) = fraction(n * b.d - b.n * d, d * b.d)
        operator fun times(b: Fraction) = fraction(n * b.n, d * b.d)
        operator fun div(b: Fraction) = fraction(n * b.d, d * b.n)
        fun negate() = Fraction(-n, d)
        fun power(b: Fraction): Fraction {
            require(b.d == BigInteger.ONE && b.n.abs() <= BigInteger.valueOf(64))
            val exponent = b.n.toInt()
            require(n != BigInteger.ZERO || exponent != 0)
            return if (exponent >= 0) fraction(n.pow(exponent), d.pow(exponent))
            else fraction(d.pow(-exponent), n.pow(-exponent))
        }
    }

    private fun fraction(n: BigInteger, d: BigInteger): Fraction {
        require(d != BigInteger.ZERO && n.bitLength() <= 2048 && d.bitLength() <= 2048)
        val gcd = n.gcd(d) * BigInteger.valueOf(d.signum().toLong())
        return Fraction(n / gcd, d / gcd)
    }

    private class Parser(val text: String) {
        private var at = 0
        private var depth = 0
        private fun take(c: Char): Boolean {
            while (at < text.length && text[at].isWhitespace()) at++
            return (at < text.length && text[at] == c).also { if (it) at++ }
        }
        fun parse(): Fraction {
            val result = sum()
            while (at < text.length && text[at].isWhitespace()) at++
            require(at == text.length)
            return result
        }
        private fun sum(): Fraction {
            var a = product()
            while (true) a = when { take('+') -> a + product(); take('-') -> a - product(); else -> return a }
        }
        private fun product(): Fraction {
            var a = unary()
            while (true) a = when { take('*') -> a * unary(); take('/') -> a / unary(); else -> return a }
        }
        private fun unary(): Fraction {
            require(++depth <= 64)
            try { return when { take('+') -> unary(); take('-') -> unary().negate(); else -> power() } }
            finally { depth-- }
        }
        private fun power(): Fraction {
            val a = atom()
            return if (take('^')) a.power(unary()) else a
        }
        private fun atom(): Fraction {
            if (take('(')) return sum().also { require(take(')')) }
            val start = at
            while (at < text.length && (text[at].isDigit() || text[at] == '.')) at++
            require(at > start)
            val value = BigDecimal(text.substring(start, at))
            return fraction(value.unscaledValue(), BigInteger.TEN.pow(value.scale()))
        }
    }
}
