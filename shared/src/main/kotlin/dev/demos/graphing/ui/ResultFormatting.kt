package dev.demos.graphing.ui

import java.math.BigDecimal
import java.math.RoundingMode

/** Round displayed numbers to a maximum number of decimal places, without padding zeros. */
internal fun formatResultNumber(source: String, decimalPlaces: Int? = null): String {
    if (decimalPlaces == null && !Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)[eE][+-]?[0-9]+").matches(source)) return source
    val number = source.toBigDecimalOrNull() ?: return source
    val places = decimalPlaces?.coerceIn(0, 1000)
    val rounded = when {
        places == null || number.scale() <= places -> number
        number.precision().toLong() - number.scale() < -places.toLong() -> BigDecimal.ZERO
        else -> number.setScale(places, RoundingMode.HALF_UP)
    }
    val magnitude = rounded.abs()
    return if (magnitude.signum() == 0 || magnitude < BigDecimal("1000000000000") &&
        (places != null || magnitude >= BigDecimal("0.000001"))) rounded.stripTrailingZeros().toPlainString()
    else if (places == null) source
    else rounded.stripTrailingZeros().toString().replace("E+", "e").replace("E", "e")
}
