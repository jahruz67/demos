package dev.demos.graphing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.demos.graphing.plot.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File

@RunWith(AndroidJUnit4::class)
class NativeViewport3DTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    @Test fun rendersColoredSurfacesAndContrastWithoutABrowser() {
        val rendering = GraphRenderState()
        val settings = mutableStateOf(PlotSettings())
        compose.setContent {
            GraphPlot(listOf(
                PlotExpression(1,"z=sin(x)*cos(y)",0xFF2D70B3.toInt()),
                PlotExpression(2,"x^2+y^2+z^2=9",0xFFC74440.toInt()),
            ),true,settings=settings.value,renderState=rendering,showControls=false,
                modifier=Modifier.requiredSize(1000.dp,700.dp))
        }
        compose.waitUntil(20_000) { rendering.ready }
        fun containsBrowser(view: View): Boolean = view is WebView ||
            (view is ViewGroup && (0 until view.childCount).any { containsBrowser(view.getChildAt(it)) })
        compose.runOnIdle { assertFalse(containsBrowser(compose.activity.window.decorView)) }
        val bitmap=capture()
        try {
            val pixels=IntArray(bitmap.width*bitmap.height)
            bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
            val blue=pixels.count { Color.blue(it)>Color.red(it)*1.1 && Color.blue(it)>Color.green(it)*1.05 }
            val red=pixels.count { Color.red(it)>Color.blue(it)*1.15 && Color.red(it)>Color.green(it)*1.1 }
            assertTrue("Blue surface has visible area: $blue",blue>1500)
            assertTrue("Red sphere has visible area: $red",red>1500)
            File(compose.activity.cacheDir,"native-3d").mkdirs()
            File(compose.activity.cacheDir,"native-3d/viewport.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        } finally { bitmap.recycle() }
        compose.runOnIdle { settings.value=settings.value.copy(appearance=GraphAppearance(reverseContrast=true)) }
        compose.waitForIdle()
        compose.waitUntil(20_000) { rendering.ready }
        val dark=capture()
        try {
            val pixels=IntArray(dark.width*dark.height)
            dark.getPixels(pixels,0,dark.width,0,0,dark.width,dark.height)
            assertTrue(pixels.count { Color.red(it)<8 && Color.green(it)<8 && Color.blue(it)<8 }>100_000)
        } finally { dark.recycle() }
    }

    @Test fun floatingSettingsExposeTheReferenceControlsNatively() {
        val changes=mutableListOf<Viewport3DSettings>()
        compose.setContent {
            GraphPlot(emptyList(),true,modifier=Modifier.requiredSize(380.dp,700.dp),on3DSettingsChange=changes::add)
        }
        compose.onNodeWithContentDescription("Zoom in").assertIsDisplayed()
        compose.onNodeWithContentDescription("Zoom out").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reset view").assertIsDisplayed()
        compose.onNodeWithContentDescription("Graph settings").performClick()
        compose.onNodeWithText("Graph Settings").assertIsDisplayed()
        compose.onNodeWithText("Grid").performClick()
        compose.runOnIdle { assertFalse(changes.last().showGrid) }
        compose.onNodeWithText("Reverse Contrast").performClick()
        compose.runOnIdle { assertTrue(changes.last().reverseContrast) }
        compose.onNodeWithContentDescription("Close graph settings").performClick()
        compose.onNodeWithText("Graph Settings").assertDoesNotExist()
    }

    @Test fun zoomButtonsMoveTheRenderedPointAndHomeRestoresIt() {
        val rendering=GraphRenderState()
        val camera=GeoGebraCamera()
        compose.setContent {
            GraphPlot(listOf(PlotExpression(1,"(2,2,2)",0xFF8040B0.toInt())),true,
                modifier=Modifier.requiredSize(600.dp).testTag("viewport"),renderState=rendering,camera3D=camera)
        }
        fun point():Offset {
            compose.waitUntil(20_000) { rendering.ready }
            val b=capture()
            try {
                var x=0L;var y=0L;var count=0
                for(py in 0 until b.height) for(px in 0 until b.width) {
                    val c=b.getPixel(px,py)
                    if(Color.red(c) in 70..180 && Color.blue(c)>Color.green(c)*1.5 && Color.red(c)>Color.green(c)*1.4) {
                        x+=px;y+=py;count++
                    }
                }
                assertTrue("A native point is visible",count>0)
                return Offset(x.toFloat()/count,y.toFloat()/count)
            } finally { b.recycle() }
        }
        val before=point()
        compose.onNodeWithContentDescription("Zoom in").performClick()
        compose.waitForIdle()
        val after=point()
        assertTrue((before-after).getDistance()>4f)
        compose.onNodeWithContentDescription("Reset view").performClick()
        compose.waitForIdle()
        val restored=point()
        assertEquals(before.x,restored.x,2f)
        assertEquals(before.y,restored.y,2f)
        compose.onNodeWithTag("viewport").performTouchInput {
            down(0,Offset(200f,200f));down(1,Offset(400f,400f))
            for (step in 1..10) {
                updatePointerTo(0,Offset(200f+step*4f,200f+step*2f))
                updatePointerTo(1,Offset(400f+step*4f,400f+step*2f))
                move(16)
            }
            up(0);up(1)
        }
        compose.waitForIdle()
        val panned=point()
        assertTrue("Two-finger translation orbits, as in EuclidianController3DW",(panned-restored).getDistance()>4f)
        compose.onNodeWithTag("viewport").performTouchInput { swipe(Offset(70f,100f),Offset(140f,130f),200) }
        compose.waitForIdle()
        assertTrue("Dragging orbits the native camera",(point()-panned).getDistance()>4f)
    }
}
