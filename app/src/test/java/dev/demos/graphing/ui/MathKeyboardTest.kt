package dev.demos.graphing.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.awaitCancellation
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
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class MathKeyboardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun tappingAndRefocusingEquationsNeverStartsThePlatformKeyboard() {
        var platformRequests = 0
        val value = mutableStateOf(TextFieldValue("900", TextRange(3)))
        compose.setContent {
            // Observe requests that would reach Android, without opening a real IME in the test.
            InterceptPlatformTextInput(interceptor = { _, _ -> platformRequests++; awaitCancellation() }) {
                Column {
                    MathEditor(value.value, true, 1, Modifier.width(300.dp),
                        onInput = { value.value = it }, onFocus = {}, onSelect = {})
                    BasicTextField("Notes", {}, Modifier.semantics { contentDescription = "Native notes" })
                }
            }
        }
        val equation = compose.onNodeWithContentDescription("Equation 1")
        repeat(3) {
            compose.onAllNodesWithText(value.value.text, useUnmergedTree = true).onLast().performTouchInput { click(center) }
        }
        equation.performClick()
        compose.runOnIdle { assertEquals(0, platformRequests) }
        // Normal text fields may still use the system keyboard.
        compose.onNodeWithContentDescription("Native notes").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, platformRequests) }
        compose.onAllNodesWithText("900", useUnmergedTree = true).onLast().performTouchInput { click(center) }
        equation.performKeyInput { pressKey(Key.Five) }
        compose.runOnIdle {
            assertEquals(1, platformRequests)
            assertTrue(value.value.text.contains('5'))
            assertEquals(4, value.value.text.length)
        }
        equation.performTextInput("7")
        compose.runOnIdle { assertTrue(value.value.text.contains('7')) }
    }
}
