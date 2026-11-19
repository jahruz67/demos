package dev.demos.graphing.plot

import kotlinx.serialization.Serializable

@Serializable
data class Viewport3DSettings(
    val showGrid: Boolean = true,
    val showXAxis: Boolean = true,
    val showYAxis: Boolean = true,
    val showZAxis: Boolean = true,
    val xAxisLabel: String = "",
    val yAxisLabel: String = "",
    val zAxisLabel: String = "",
    val reverseContrast: Boolean = false,
    val angleMode: String = "rad",
    val bounds3D: Viewport3DBounds = Viewport3DBounds(),
)

@Serializable
data class Viewport3DBounds(
    val xmin: Double = -6.0, val xmax: Double = 6.0,
    val ymin: Double = -6.0, val ymax: Double = 6.0,
    val zmin: Double = -6.0, val zmax: Double = 6.0,
) {
    fun isValid() = listOf(xmin, xmax, ymin, ymax, zmin, zmax).all { it.isFinite() } &&
        xmin < xmax && ymin < ymax && zmin < zmax
}

internal fun PlotSettings.viewport3DSettings() = Viewport3DSettings(
    showGrid = showGrid,
    showXAxis = showAxes && appearance.showXAxis,
    showYAxis = showAxes && appearance.showYAxis,
    showZAxis = showAxes && appearance.showZAxis,
    xAxisLabel = if (appearance.labels) appearance.xLabel else "",
    yAxisLabel = if (appearance.labels) appearance.yLabel else "",
    zAxisLabel = if (appearance.labels) appearance.zLabel else "",
    reverseContrast = appearance.reverseContrast,
    angleMode = if (radians) "rad" else "deg",
    bounds3D = Viewport3DBounds(xMin ?: -6.0, xMax ?: 6.0, yMin ?: -6.0, yMax ?: 6.0, zMin ?: -6.0, zMax ?: 6.0)
        .takeIf { it.isValid() } ?: Viewport3DBounds(),
)

/** Match the viewport's reverse-contrast colors without a browser filter. */
internal fun reversedGraphColor(color: Int): Int {
    val r = 255 - (color ushr 16 and 255)
    val g = 255 - (color ushr 8 and 255)
    val b = 255 - (color and 255)
    fun channel(a: Double, c: Double, d: Double) = kotlin.math.round(a * r + c * g + d * b).toInt().coerceIn(0, 255)
    return (color and -0x1000000) or (channel(-.574, 1.43, .144) shl 16) or
        (channel(.426, .43, .144) shl 8) or channel(.426, 1.43, -.856)
}
