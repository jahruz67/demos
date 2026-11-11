package dev.demos.graphing.plot

import kotlinx.serialization.Serializable
import kotlin.math.log10
import kotlin.math.pow

@Serializable
data class GraphAppearance(
    val xLabel: String = "x",
    val yLabel: String = "y",
    val zLabel: String = "z",
    val logX: Boolean = false,
    val logY: Boolean = false,
    val perspective: Float = 1f,
    val numbers: Boolean = true,
    val labels: Boolean = true,
)

internal fun axisCoordinate(value: Double, logarithmic: Boolean): Double =
    if (logarithmic) if (value > 0.0) log10(value) else Double.NaN else value

internal fun axisValue(coordinate: Double, logarithmic: Boolean): Double =
    if (logarithmic) 10.0.pow(coordinate) else coordinate
