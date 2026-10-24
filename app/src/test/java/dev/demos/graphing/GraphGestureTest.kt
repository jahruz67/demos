package dev.demos.graphing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.demos.graphing.plot.GraphPlot
import dev.demos.graphing.plot.PlotExpression
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w800dp-h1280dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GraphGestureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun graph() {
        compose.setContent {
            GraphPlot(listOf(PlotExpression(1, "(2,2)", Color.RED)), false, showControls = false,
                modifier = Modifier.requiredSize(600.dp).testTag("graph"))
        }
        compose.waitUntil(10_000) { pointCenter() != null }
    }

    private fun pinch(fromA: Offset, fromB: Offset, toA: Offset, toB: Offset) {
        compose.onNodeWithTag("graph").performTouchInput {
            down(0, fromA)
            down(1, fromB)
            // Include several movements below touch slop to exercise exact scaling.
            for (step in 1..20) {
                val fraction = step / 20f
                updatePointerTo(0, fromA + (toA - fromA) * fraction)
                updatePointerTo(1, fromB + (toB - fromB) * fraction)
                move(16L)
            }
            up(0)
            up(1)
        }
        compose.waitForIdle()
    }

    @Test fun interiorPinchScalesBothAxesOneToOne() {
        graph()
        val before = checkNotNull(pointCenter())
        pinch(Offset(200f, 200f), Offset(400f, 400f), Offset(100f, 100f), Offset(500f, 500f))
        val after = checkNotNull(pointCenter())
        assertEquals(60f, after.x - before.x, 1f)
        assertEquals(-60f, after.y - before.y, 1f)
        pinch(Offset(100f, 100f), Offset(500f, 500f), Offset(200f, 200f), Offset(400f, 400f))
        val restored = checkNotNull(pointCenter())
        assertEquals(before.x, restored.x, 1f)
        assertEquals(before.y, restored.y, 1f)
    }

    @Test fun horizontalAxisPinchLocksYEvenWhenFingersLeaveTheAxis() {
        graph()
        val before = checkNotNull(pointCenter())
        pinch(Offset(200f, 300f), Offset(400f, 300f), Offset(100f, 340f), Offset(500f, 340f))
        val after = checkNotNull(pointCenter())
        assertEquals(60f, after.x - before.x, 1f)
        assertEquals(before.y, after.y, 1f)
        // A later interior pinch must return to uniform zoom, keeping the intentional stretch.
        pinch(Offset(200f, 200f), Offset(400f, 400f), Offset(100f, 100f), Offset(500f, 500f))
        val uniform = checkNotNull(pointCenter())
        assertEquals(120f, uniform.x - after.x, 1f)
        assertEquals(-60f, uniform.y - after.y, 1f)
    }

    @Test fun verticalAxisPinchLocksXEvenWhenFingersLeaveTheAxis() {
        graph()
        val before = checkNotNull(pointCenter())
        pinch(Offset(300f, 200f), Offset(300f, 400f), Offset(340f, 100f), Offset(340f, 500f))
        val after = checkNotNull(pointCenter())
        assertEquals(before.x, after.x, 1f)
        assertEquals(-60f, after.y - before.y, 1f)
    }

    /** Measure the actual rendered point, so the assertions cover gesture wiring and drawing. */
    private fun pointCenter(): Offset? {
        var result: Offset? = null
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                var x = 0L; var y = 0L; var count = 0
                pixels.forEachIndexed { index, color ->
                    if (Color.red(color) > 200 && Color.green(color) < 80 && Color.blue(color) < 80) {
                        x += index % bitmap.width; y += index / bitmap.width; count++
                    }
                }
                if (count > 0) result = Offset(x.toFloat() / count, y.toFloat() / count)
            } finally { bitmap.recycle() }
        }
        return result
    }
}
