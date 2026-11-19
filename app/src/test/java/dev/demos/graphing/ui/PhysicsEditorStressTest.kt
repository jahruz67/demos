package dev.demos.graphing.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.demos.graphing.math.NumericCompiler
import dev.demos.graphing.stress.PhysicsStressCases
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w800dp-h1280dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class PhysicsEditorStressTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun tenEquationsCanBePastedScrolledEditedAndRestoredOnATablet() = exercise(740)

    @Test @Config(qualifiers = "w360dp-h800dp-mdpi")
    fun tenEquationsCanBePastedScrolledEditedAndRestoredOnAPhone() = exercise(330)

    private fun exercise(width: Int) {
        val value = mutableStateOf(TextFieldValue())
        val title = mutableStateOf("")
        compose.setContent {
            Column(Modifier.width(width.dp)) {
                Text(title.value)
                MathEditor(value.value, true, 1, onInput = { value.value = MathInputRules.apply(value.value, it) },
                    onFocus = {}, onSelect = {})
            }
        }
        val editor = compose.onNodeWithContentDescription("Equation 1")
        for (case in PhysicsStressCases.all) for (source in listOf("x="+case.expression, case.latex)) {
            compose.runOnIdle { title.value = case.name }
            editor.performTextReplacement(source)
            compose.runOnIdle { assertEquals(case.name, source, value.value.text) }
            editor.assertIsDisplayed()
            compose.onAllNodesWithText("□", substring = true, useUnmergedTree = true).assertCountEquals(0)
            editor.performTouchInput { swipeLeft() }
            // Return to the document end after scrolling, then edit through real key events.
            compose.runOnIdle { value.value = value.value.copy(selection = TextRange(value.value.text.length)) }
            editor.performTextInput("+0")
            editor.performKeyInput {
                pressKey(Key.DirectionLeft); pressKey(Key.DirectionLeft)
                pressKey(Key.DirectionRight); pressKey(Key.DirectionRight)
            }
            compose.runOnIdle {
                assertEquals(case.name, source+"+0", value.value.text)
                assertEquals(case.name, case.expected, NumericCompiler.compile(value.value.text.substringAfter('=')).evaluate(),
                    max(1e-12, abs(case.expected)*1e-11))
            }
            editor.performKeyInput { pressKey(Key.Backspace); pressKey(Key.Backspace) }
            compose.runOnIdle { assertEquals(case.name, source, value.value.text) }
            val offsets = editableMath(source).caretOffsets(source)
            assertTrue(case.name, offsets.first() == 0 && offsets.last() == source.length)
            assertTrue(case.name, offsets.all { it in 0..source.length })
        }
    }

    @Test fun rapidNavigationUsesTheNewOffsetsAfterAnExponentGainsBraces() {
        val value = mutableStateOf(TextFieldValue("2^3", TextRange(3)))
        compose.setContent {
            MathEditor(value.value, true, 1, onInput = { value.value = it }, onFocus = {}, onSelect = {})
        }
        compose.onNodeWithContentDescription("Equation 1").performKeyInput {
            pressKey(Key.DirectionRight)
            keyDown(Key.ShiftLeft)
            pressKey(Key.DirectionLeft)
            keyUp(Key.ShiftLeft)
        }
        compose.runOnIdle {
            assertEquals("2^{3}", value.value.text)
            assertEquals(TextRange(5, 4), value.value.selection)
        }
    }
}
