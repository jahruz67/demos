package dev.demos.graphing.math

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import dev.demos.graphing.plot.*
import org.junit.Assert.*
import org.junit.Test

class PlotGesturesTest {
    private val size = IntSize(800, 600)
    private val view = Viewport(0.0, 0.0, 20.0, 15.0)

    @Test fun onlyTouchesAlongTheSameVisibleAxisSelectAnAxis() {
        assertEquals(PlotZoomAxes.X, plotZoomAxes(listOf(Offset(100f, 300f), Offset(600f, 310f)), view, size, 28f))
        assertEquals(PlotZoomAxes.Y, plotZoomAxes(listOf(Offset(405f, 100f), Offset(400f, 500f)), view, size, 28f))
        assertEquals(PlotZoomAxes.Both, plotZoomAxes(listOf(Offset(200f, 100f), Offset(600f, 500f)), view, size, 28f))
        // A centroid on an axis alone must not accidentally stretch the graph.
        assertEquals(PlotZoomAxes.Both, plotZoomAxes(listOf(Offset(200f, 200f), Offset(600f, 400f)), view, size, 28f))
        assertEquals(PlotZoomAxes.Both, plotZoomAxes(listOf(Offset(390f, 290f), Offset(410f, 310f)), view, size, 28f))
        assertEquals(PlotZoomAxes.Both, plotZoomAxes(listOf(Offset(100f, 0f), Offset(600f, 0f)), view.copy(centerY = 12.0), size, 28f))
    }

    @Test fun axisHitTestingTracksPanningAndUnequalScales() {
        val shifted = Viewport(5.0, -3.0, 40.0, 10.0)
        assertEquals(PlotZoomAxes.X, plotZoomAxes(listOf(Offset(100f, 120f), Offset(650f, 120f)), shifted, size, 28f))
        assertEquals(PlotZoomAxes.Y, plotZoomAxes(listOf(Offset(300f, 230f), Offset(300f, 550f)), shifted, size, 28f))
    }

    @Test fun horizontalPinchLeavesYBoundsExactlyUnchanged() {
        val next = view.transformPlot(2f, Offset(30f, 40f), Offset(500f, 300f), size, PlotZoomAxes.X)
        assertEquals(10.0, next.width, 1e-9)
        assertEquals(view.bottom, next.bottom, 0.0)
        assertEquals(view.top, next.top, 0.0)
        assertEquals(0.875, next.centerX, 1e-9)
    }

    @Test fun verticalPinchLeavesXBoundsExactlyUnchanged() {
        val next = view.transformPlot(0.5f, Offset(30f, 40f), Offset(400f, 200f), size, PlotZoomAxes.Y)
        assertEquals(30.0, next.height, 1e-9)
        assertEquals(view.left, next.left, 0.0)
        assertEquals(view.right, next.right, 0.0)
        assertEquals(-0.5, next.centerY, 1e-9)
    }

    @Test fun normalPinchScalesBothAxesEquallyAndAnchorsUnderMovingFingers() {
        val centroid = Offset(500f, 200f)
        val pan = Offset(30f, 40f)
        val next = view.transformPlot(2f, pan, centroid, size, PlotZoomAxes.Both)
        assertEquals(10.0, next.width, 1e-9)
        assertEquals(7.5, next.height, 1e-9)
        assertEquals(view.width / view.height, next.width / next.height, 1e-9)
        val oldPoint = worldPoint(view, centroid)
        val newPoint = worldPoint(next, centroid + pan)
        assertEquals(oldPoint.first, newPoint.first, 1e-9)
        assertEquals(oldPoint.second, newPoint.second, 1e-9)
    }

    @Test fun normalPinchPreservesAnIntentionalAxisStretchAndCanBeReversed() {
        val stretched = view.copy(height = 40.0)
        val anchor = Offset(600f, 150f)
        val next = stretched.transformPlot(2f, Offset.Zero, anchor, size, PlotZoomAxes.Both)
        assertEquals(10.0, next.width, 1e-9)
        assertEquals(20.0, next.height, 1e-9)
        assertEquals(stretched, next.transformPlot(0.5f, Offset.Zero, anchor, size, PlotZoomAxes.Both))
    }

    @Test fun axisSeparationIgnoresMotionInTheOtherDirection() {
        val previous = listOf(Offset(100f, 300f), Offset(600f, 300f))
        assertEquals(2f, plotAxisZoom(previous, listOf(Offset(-150f, 200f), Offset(850f, 400f)), PlotZoomAxes.X), 0f)
        assertEquals(1f, plotAxisZoom(previous, listOf(Offset(100f, 200f), Offset(600f, 400f)), PlotZoomAxes.X), 0f)
        assertEquals(0.5f, plotAxisZoom(listOf(Offset(400f, 100f), Offset(400f, 500f)),
            listOf(Offset(200f, 200f), Offset(600f, 400f)), PlotZoomAxes.Y), 0f)
    }

    @Test fun zoomLimitsPreserveUniformScaling() {
        val huge = view.copy(width = 9e9, height = 6.75e9)
        val next = huge.transformPlot(0.1f, Offset.Zero, Offset(400f, 300f), size, PlotZoomAxes.Both)
        assertEquals(1e10, next.width, 0.001)
        assertEquals(huge.width / huge.height, next.width / next.height, 1e-9)
        val tiny = view.copy(width = 4e-7, height = 3e-7)
        val smaller = tiny.transformPlot(10f, Offset.Zero, Offset(400f, 300f), size, PlotZoomAxes.Both)
        assertEquals(1e-7, smaller.height, 1e-16)
        assertEquals(tiny.width / tiny.height, smaller.width / smaller.height, 1e-9)
        val extreme = view.copy(width = 1e10, height = 1e-10)
        assertEquals(extreme, extreme.transformPlot(2f, Offset.Zero, Offset(400f, 300f), size, PlotZoomAxes.Both))
    }

    @Test fun panningStillMovesBothAxesAndEmptyFramesAreIgnored() {
        val moved = view.transformPlot(1f, Offset(40f, 60f), Offset(300f, 200f), size, PlotZoomAxes.Both)
        assertEquals(-1.0, moved.centerX, 1e-9)
        assertEquals(1.5, moved.centerY, 1e-9)
        assertEquals(view, view.transformPlot(2f, Offset.Zero, Offset.Zero, IntSize.Zero, PlotZoomAxes.Both))
        assertEquals(view, view.transformPlot(Float.NaN, Offset.Zero, Offset.Zero, size, PlotZoomAxes.Both))
    }

    private fun worldPoint(viewport: Viewport, pixel: Offset) =
        (viewport.left + pixel.x * viewport.width / size.width) to
            (viewport.top - pixel.y * viewport.height / size.height)
}
