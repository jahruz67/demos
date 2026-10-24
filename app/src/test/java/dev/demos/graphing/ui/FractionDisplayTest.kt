package dev.demos.graphing.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.input.key.Key
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w800dp-h1280dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class FractionDisplayTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun exponentSlotsAndVerticalKeyboardNavigationWorkDirectly() {
        val value = mutableStateOf(TextFieldValue("2^{}", TextRange(3)))
        compose.setContent { MathEditor(value.value, true, 1, Modifier.width(300.dp),
            onInput = { value.value = MathInputRules.apply(value.value, it) }, onFocus = {}, onSelect = {}) }
        val equation = compose.onNodeWithContentDescription("Equation 1")
        equation.performTextInput("3")
        val base = compose.onNodeWithText("2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val exponent = compose.onNodeWithText("3", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(exponent.center.y < base.center.y)
        assertTrue(exponent.height < base.height)
        compose.onNodeWithText("3", useUnmergedTree = true).performTouchInput { click(Offset(width - 1f, center.y)) }
        equation.performTextInput("4")
        compose.runOnIdle { assertEquals("2^{34}", value.value.text) }
        equation.performKeyInput { pressKey(Key.DirectionRight) }
        equation.performTextInput("+1")
        compose.runOnIdle { assertEquals("2^{34}+1", value.value.text) }
        compose.runOnIdle { value.value = TextFieldValue("\\frac{900}{8}", TextRange(12)) }
        equation.performKeyInput { pressKey(Key.DirectionUp) }
        compose.runOnIdle { assertTrue(value.value.selection.end in 6..9) }
        equation.performKeyInput { pressKey(Key.DirectionDown) }
        compose.runOnIdle { assertTrue(value.value.selection.end in 11..12) }
    }

    @Test fun numeratorAndDenominatorAreTheEditableField() {
        val state = mutableStateOf(CalculatorUiState(
            rows = listOf(ExpressionRow(1, "", result = "")),
            activeId = 1, layout = CalculatorLayout.Calculator,
        ))
        compose.setContent {
            CalculatorScreen(state.value, onAction = { action ->
                if (action is CalculatorAction.EditRow) state.value = state.value.copy(
                    rows = state.value.rows.map { if (it.id == action.id) it.copy(text = action.text) else it },
                )
            }, graph = { _, _ -> })
        }
        val equation = compose.onNodeWithContentDescription("Equation 1")
        equation.performTextInput("900")
        compose.onNodeWithContentDescription("÷").performClick()
        equation.performTextInput("8")
        compose.runOnIdle { assertEquals("\\frac{900}{8}", state.value.rows.first().text) }
        compose.onNodeWithContentDescription("Formatted equation 1").assertDoesNotExist()
        // These are the actual editable slots; no separate source line is displayed.
        val numerator = compose.onAllNodesWithText("900", useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot
        val denominator = compose.onAllNodesWithText("8", useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot
        assertTrue(numerator.bottom <= denominator.top)
        assertEquals(numerator.center.x, denominator.center.x, 1f)

        compose.onNodeWithText("900", useUnmergedTree = true).performTouchInput { click(Offset(1f, center.y)) }
        equation.performTextInput("1")
        compose.runOnIdle { assertEquals("\\frac{1900}{8}", state.value.rows.first().text) }
        compose.onAllNodesWithText("8", useUnmergedTree = true).onFirst().performTouchInput { click(Offset(width - 1f, center.y)) }
        equation.performTextInput("6")
        compose.runOnIdle { assertEquals("\\frac{1900}{86}", state.value.rows.first().text) }
        compose.onNodeWithContentDescription("→").performClick()
        compose.onNodeWithContentDescription("+").performClick()
        equation.performTextInput("2")
        compose.runOnIdle { assertEquals("\\frac{1900}{86}+2", state.value.rows.first().text) }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val output = File("build/outputs/verification/fraction-display.png")
                checkNotNull(output.parentFile).mkdirs()
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        }
    }
}
