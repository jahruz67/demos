package dev.demos.graphing.plot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

internal enum class PlotZoomAxes { Both, X, Y }

/** Choose once when a pinch begins. Both fingers must be near the same visible axis. */
internal fun plotZoomAxes(points: List<Offset>, view: Viewport, size: IntSize, tolerance: Float): PlotZoomAxes {
    if (points.size < 2 || size.width <= 0 || size.height <= 0) return PlotZoomAxes.Both
    val axisX = (-view.left / view.width * size.width).toFloat()
    val axisY = (view.top / view.height * size.height).toFloat()
    val nearX = 0.0 in view.bottom..view.top && points.all { abs(it.y - axisY) <= tolerance }
    val nearY = 0.0 in view.left..view.right && points.all { abs(it.x - axisX) <= tolerance }
    return when {
        nearX && !nearY -> PlotZoomAxes.X
        nearY && !nearX -> PlotZoomAxes.Y
        else -> PlotZoomAxes.Both // The origin is shared by both axes.
    }
}

/** Measure separation along the locked axis; sideways motion must not affect its scale. */
internal fun plotAxisZoom(previous: List<Offset>, current: List<Offset>, axes: PlotZoomAxes): Float {
    if (previous.size < 2 || previous.size != current.size) return 1f
    fun spread(points: List<Offset>): Float {
        val values = points.map { if (axes == PlotZoomAxes.X) it.x else it.y }
        val center = values.average().toFloat()
        return values.sumOf { abs(it - center).toDouble() }.toFloat() / values.size
    }
    val before = spread(previous)
    val after = spread(current)
    return if (before >= 1f && after >= 1f) (after / before).takeIf { it.isFinite() } ?: 1f else 1f
}

/** Keep the world point under the moving pinch center fixed, including simultaneous panning. */
internal fun Viewport.transformPlot(zoom: Float, pan: Offset, centroid: Offset, size: IntSize, axes: PlotZoomAxes): Viewport {
    if (size.width <= 0 || size.height <= 0 || !zoom.isFinite() || zoom <= 0f) return this
    // A shared factor preserves the aspect ratio even at the zoom limits.
    val factor = when (axes) {
        PlotZoomAxes.X -> zoom.toDouble().coerceIn(width / 1e10, width / 1e-7)
        PlotZoomAxes.Y -> zoom.toDouble().coerceIn(height / 1e10, height / 1e-7)
        PlotZoomAxes.Both -> {
            val minimum = maxOf(width, height) / 1e10
            val maximum = minOf(width, height) / 1e-7
            // Manually entered bounds may have a ratio too large for both limits.
            if (minimum <= maximum) zoom.toDouble().coerceIn(minimum, maximum) else 1.0
        }
    }
    val newWidth = if (axes == PlotZoomAxes.Y) width else width / factor
    val newHeight = if (axes == PlotZoomAxes.X) height else height / factor
    return Viewport(
        centerX = if (axes == PlotZoomAxes.Y) centerX else centerX +
            (centroid.x - size.width / 2.0) * (width - newWidth) / size.width - pan.x * newWidth / size.width,
        centerY = if (axes == PlotZoomAxes.X) centerY else centerY +
            (size.height / 2.0 - centroid.y) * (height - newHeight) / size.height + pan.y * newHeight / size.height,
        width = newWidth,
        height = newHeight,
    )
}
