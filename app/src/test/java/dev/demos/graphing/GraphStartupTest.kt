package dev.demos.graphing

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.demos.graphing.plot.GraphPlot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GraphStartupTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun graphSurvivesSmallAndEmptyBoundsThenExpands() {
        val dimensions = mutableStateOf(300.dp to 300.dp)
        compose.setContent {
            Box {
                GraphPlot(
                    expressions = emptyList(), is3D = false, showControls = false,
                    modifier = Modifier.requiredSize(dimensions.value.first, dimensions.value.second),
                )
            }
        }
        // Window resizing can temporarily leave less space than the axis label margins.
        for ((width, height) in listOf(300 to 16, 16 to 300, 0 to 0, 800 to 500)) {
            compose.runOnIdle { dimensions.value = width.dp to height.dp }
            compose.waitForIdle()
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                try { view.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
            }
        }
    }
}
