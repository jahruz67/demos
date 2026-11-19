package dev.demos.graphing

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.demos.graphing.math.NumericCompiler
import dev.demos.graphing.stress.PhysicsStressCases
import dev.demos.graphing.ui.CalculatorAction
import dev.demos.graphing.ui.CalculatorLayout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.max

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PhysicsWorksheetStressTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun twentyLongInputsRenderAndCalculateThroughTheActualWorksheet() {
        lateinit var model: CalculatorViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[CalculatorViewModel::class.java] }
        compose.waitUntil(30_000) { !model.state.value.busy }
        compose.runOnIdle { model.onAction(CalculatorAction.New) }
        compose.waitUntil(10_000) { model.state.value.rows.size == 1 && model.state.value.rows.first().text.isEmpty() }
        compose.runOnIdle {
            model.onAction(CalculatorAction.SetLayout(CalculatorLayout.Calculator))
            model.onAction(CalculatorAction.SetKeyboardVisible(false))
            model.onAction(CalculatorAction.SetDegreeMode(false))
        }
        for (case in PhysicsStressCases.all) for (source in listOf(case.expression, case.latex.substringAfter('='))) {
            val started = System.nanoTime()
            compose.onNodeWithContentDescription("Equation 1").performTextReplacement(source)
            compose.waitUntil(30_000) {
                val row = model.state.value.rows.first()
                row.text == source && !row.isEvaluating && (row.error != null || row.result.isNotEmpty() || row.approximate.isNotEmpty())
            }
            val row = model.state.value.rows.first()
            assertNull("${case.name}: ${row.error}", row.error)
            val number = NumericCompiler.compile(row.approximate.ifBlank { row.result }).evaluate()
            assertEquals(case.name, case.expected, number, max(1e-12, abs(case.expected)*1e-10))
            compose.onNodeWithContentDescription("Equation 1").assertIsDisplayed()
            compose.onNodeWithContentDescription("Result 1").assertIsDisplayed()
            compose.onAllNodesWithText("□", substring = true, useUnmergedTree = true).assertCountEquals(0)
            Log.i("PhysicsWorksheet", "PASS ${case.id}: ${case.name}, ${source.length} characters in ${(System.nanoTime()-started)/1_000_000} ms")
        }
        val last = PhysicsStressCases.all.last()
        val source = last.latex.substringAfter('=')
        val editor = compose.onNodeWithContentDescription("Equation 1")
        editor.performTextInput("+0")
        editor.performKeyInput { pressKey(Key.Backspace); pressKey(Key.Backspace) }
        compose.runOnIdle { assertEquals("Fast Backspace preserves the long expression", source, model.state.value.rows.first().text) }
        compose.waitUntil(30_000) {
            val row = model.state.value.rows.first()
            !row.isEvaluating && (row.error != null || row.result.isNotBlank() || row.approximate.isNotBlank())
        }
        val restored = model.state.value.rows.first()
        assertNull(restored.error)
        assertEquals(last.expected, NumericCompiler.compile(restored.approximate.ifBlank { restored.result }).evaluate(), 1e-12)
        compose.onNodeWithContentDescription("Result 1").assertIsDisplayed()
        compose.waitForIdle()
        val image = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = File(compose.activity.getExternalFilesDir(null), "verification").apply { mkdirs() }
        try { File(directory, "physics-stress.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { image.recycle() }
    }
}
