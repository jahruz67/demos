package dev.demos.graphing.ui

import java.math.BigDecimal

/** Keep everyday decimal results readable without rounding away CAS precision. */
internal fun formatResultNumber(source: String): String {
    if (!Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)[eE][+-]?[0-9]+").matches(source)) return source
    val number = source.toBigDecimalOrNull() ?: return source
    val magnitude = number.abs()
    return if (magnitude.signum() == 0 || magnitude >= BigDecimal("0.000001") && magnitude < BigDecimal("1000000000000"))
        number.stripTrailingZeros().toPlainString() else source
}
