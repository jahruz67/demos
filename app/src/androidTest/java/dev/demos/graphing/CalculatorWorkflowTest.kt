package dev.demos.graphing

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import dev.demos.graphing.data.GraphRepository
import dev.demos.graphing.ui.CalculatorAction
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CalculatorWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: CalculatorViewModel

    @Before fun newDocument() {
        compose.activityRule.scenario.onActivity {
            model = ViewModelProvider(it)[CalculatorViewModel::class.java]
        }
        compose.waitUntil(30_000) { !model.state.value.busy }
        compose.runOnIdle { model.onAction(CalculatorAction.New) }
        compose.waitUntil(10_000) { model.state.value.rows.size == 1 && model.state.value.rows.first().text.isEmpty() }
    }

    @Test fun indefiniteIntegralHasExactAnswerAndAdjustableConstant() {
        edit("integrate(x^2,x)")
        compose.waitUntil(30_000) { model.state.value.rows.first().result.contains("C") }
        assertEquals("x^3/3 + C", model.state.value.rows.first().result)
        assertTrue(model.state.value.sliders.any { it.name == "C" })
        compose.runOnIdle { model.onAction(CalculatorAction.SetSlider("C", 3.0)) }
        assertEquals(3.0, model.state.value.sliders.first { it.name == "C" }.value, 0.0)
        assertEquals("x^3/3+C", model.plotExpressions(model.state.value).first().text)
    }

    @Test fun screenshotIntegralEvaluatesAndUndoRestoresInput() {
        edit("integrate(87x,x,0,100)")
        compose.waitUntil(30_000) { model.state.value.rows.first().result == "435000" }
        // Independent edits must be outside the controller's 750 ms typing group.
        Thread.sleep(800)
        edit("solve(x^2-2=0,x)")
        compose.waitUntil(30_000) { model.state.value.rows.first().result.contains("sqrt(2)") }
        compose.runOnIdle { model.onAction(CalculatorAction.Undo) }
        compose.waitUntil(10_000) { model.state.value.rows.first().text == "integrate(87x,x,0,100)" }
    }

    @Test fun topButtonSwitchesDimensionWithoutChangingExpressions() {
        edit("z=x^2+y^2")
        compose.onNodeWithContentDescription("Switch to 3D graph").performClick()
        compose.waitUntil(5_000) { model.state.value.is3D }
        assertEquals("z=x^2+y^2", model.state.value.rows.first().text)
        compose.onNodeWithContentDescription("Switch to 2D graph").performClick()
        compose.waitUntil(5_000) { !model.state.value.is3D }
        assertEquals("z=x^2+y^2", model.state.value.rows.first().text)
    }

    @Test fun saveAndReopenPreserves3DAndIntegrationConstant() {
        edit("integrate(x,x)")
        compose.waitUntil(30_000) { model.state.value.sliders.any { it.name == "C" } }
        compose.runOnIdle {
            model.onAction(CalculatorAction.SetSlider("C", 4.0))
            model.onAction(CalculatorAction.Toggle3D)
        }
        compose.waitUntil(30_000) {model.calculator.viewport3D.xml.contains("coordSystem")}
        val originalCamera=model.calculator.viewport3D.xml
        compose.onNodeWithContentDescription("Zoom in").performClick()
        compose.waitUntil(30_000) {model.calculator.viewport3D.xml!=originalCamera}
        fun waitForStableCamera() {
            var previous="";var changed=System.nanoTime()
            compose.waitUntil(30_000) {
                val current=model.calculator.viewport3D.xml
                if(current!=previous){previous=current;changed=System.nanoTime()}
                current.contains("coordSystem") && System.nanoTime()-changed>1_000_000_000L
            }
        }
        waitForStableCamera()
        compose.runOnIdle {
            model.onAction(CalculatorAction.Save("Workflow integration check"))
        }
        compose.waitUntil(10_000) { model.state.value.savedGraphs.any { it.title == "Workflow integration check" } }
        val id = model.state.value.savedGraphs.first { it.title == "Workflow integration check" }.id
        val savedCamera=Regex("<coordSystem[^>]+>").find(GraphRepository(compose.activity).load(id).viewport3DXml!!)!!.value
        compose.runOnIdle { model.onAction(CalculatorAction.New) }
        compose.waitUntil(10_000) { model.state.value.rows.first().text.isEmpty() }
        compose.runOnIdle { model.onAction(CalculatorAction.Open(id)) }
        compose.waitUntil(10_000) { model.state.value.title == "Workflow integration check" }
        assertTrue(model.state.value.is3D)
        assertEquals("integrate(x,x)", model.state.value.rows.first().text)
        compose.waitUntil(30_000) { model.state.value.sliders.any { it.name == "C" } }
        assertEquals(4.0, model.state.value.sliders.first { it.name == "C" }.value, 0.0)
        waitForStableCamera()
        assertEquals(savedCamera,Regex("<coordSystem[^>]+>").find(model.calculator.viewport3D.xml)!!.value)
        GraphRepository(compose.activity).delete(id)
    }

    @Test fun captureVisualEvidence() {
        capture("01-blank.png")
        edit("integrate(x^2,x)")
        compose.waitUntil(30_000) { model.state.value.rows.first().result.contains("C") }
        compose.runOnIdle { model.onAction(CalculatorAction.AddRow) }
        val second = model.state.value.activeId
        compose.runOnIdle { model.onAction(CalculatorAction.EditRow(second, "integrate(87x,x,0,100)")) }
        compose.waitUntil(30_000) { model.state.value.rows.any { it.result == "435000" } }
        capture("02-integrals.png")
        compose.runOnIdle { model.onAction(CalculatorAction.New) }
        compose.waitUntil(10_000) { model.state.value.rows.size == 1 && model.state.value.rows.first().text.isEmpty() }
        edit("x^2+y^2+z^2=9")
        compose.onNodeWithContentDescription("Switch to 3D graph").performClick()
        compose.waitUntil(5_000) { model.state.value.is3D }
        // Software GLES in the emulator needs a settled mesh and a rendered frame.
        Thread.sleep(2000)
        capture("03-sphere-3d.png")
        assertTrue(model.state.value.is3D)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        checkNotNull(image) { "Android did not provide a screenshot" }
        val directory = File(compose.activity.getExternalFilesDir(null), "verification").apply { mkdirs() }
        File(directory, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    private fun edit(source: String) {
        compose.runOnIdle {
            model.onAction(CalculatorAction.EditRow(model.state.value.rows.first().id, source))
        }
    }
}
