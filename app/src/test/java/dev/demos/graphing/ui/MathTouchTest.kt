package dev.demos.graphing.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
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
class MathTouchTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val value = mutableStateOf(TextFieldValue())
    private val clipboard = object : ClipboardManager {
        var contents: AnnotatedString? = null
        override fun getText() = contents
        override fun setText(annotatedString: AnnotatedString) { contents = annotatedString }
    }
    private val toolbar = object : TextToolbar {
        override var status = TextToolbarStatus.Hidden
        var copy: (() -> Unit)? = null
        var cut: (() -> Unit)? = null
        var paste: (() -> Unit)? = null
        override fun hide() { status = TextToolbarStatus.Hidden }
        override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?,
            onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {
            status = TextToolbarStatus.Shown
            copy = onCopyRequested; cut = onCutRequested; paste = onPasteRequested
        }
    }

    private fun editor(source: String, cursor: Int = 0) {
        value.value = TextFieldValue(source, TextRange(cursor))
        compose.setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard, LocalTextToolbar provides toolbar) {
                MathEditor(value.value, true, 1, Modifier.width(300.dp),
                    onInput = { value.value = MathInputRules.apply(value.value, it) }, onFocus = {}, onSelect = {})
            }
        }
    }

    @Test fun tappingWhitespaceAfterARadicalPlacesTheCaretOutsideIt() {
        editor("sqrt(552252)", 5)
        val equation = compose.onNodeWithContentDescription("Equation 1")
        equation.performTouchInput { click(Offset(width - 8f, center.y)) }
        compose.runOnIdle { assertEquals(value.value.text.length, value.value.selection.end) }
        equation.performTextInput("7")
        compose.runOnIdle { assertEquals("sqrt(552252)7", value.value.text) }
    }

    @Test fun tappingBeforeARadicalCanPlaceTheCaretBeforeTheWholeStructure() {
        editor("sqrt(552252)", 8)
        compose.onNodeWithContentDescription("Equation 1").performTouchInput { click(Offset(1f, center.y)) }
        compose.runOnIdle { assertEquals(0, value.value.selection.end) }
    }

    @Test fun longPressingTrailingWhitespaceOffersSelectionHandlesAndWorkingCopyCutPaste() {
        editor("900")
        val equation = compose.onNodeWithContentDescription("Equation 1")
        equation.performTouchInput { longClick(Offset(width - 8f, center.y)) }
        compose.runOnIdle {
            assertEquals(TextRange(0, 3), value.value.selection)
            assertEquals(TextToolbarStatus.Shown, toolbar.status)
            assertNotNull(toolbar.copy)
            assertNotNull(toolbar.cut)
            toolbar.copy!!.invoke()
            assertEquals("900", clipboard.contents?.text)
        }
        compose.onNodeWithContentDescription("Selection start").assertExists()
        compose.onNodeWithContentDescription("Selection end").assertExists()
        equation.performTouchInput { longClick(Offset(width - 8f, center.y)) }
        compose.runOnIdle { toolbar.cut!!.invoke() }
        compose.runOnIdle { assertEquals("", value.value.text) }
        equation.performTouchInput { longClick(center) }
        compose.runOnIdle { assertNotNull(toolbar.paste); toolbar.paste!!.invoke() }
        compose.runOnIdle { assertEquals("900", value.value.text) }
    }

    @Test fun cursorHandleMovesTheInsertionPoint() {
        editor("900")
        compose.onNodeWithContentDescription("Equation 1").performTouchInput { click(Offset(width - 8f, center.y)) }
        compose.onNodeWithContentDescription("Move cursor").performTouchInput {
            down(Offset(center.x, 8f)); moveBy(Offset(-100f, 0f)); up()
        }
        compose.runOnIdle { assertEquals(0, value.value.selection.end) }
    }

    @Test fun aSwipeThroughTheExpressionDoesNotTriggerLongPressSelection() {
        editor("900")
        compose.onNodeWithContentDescription("Equation 1").performTouchInput {
            swipe(Offset(250f, center.y), Offset(100f, center.y), 150L)
        }
        compose.runOnIdle { assertTrue(value.value.selection.collapsed); assertEquals(TextToolbarStatus.Hidden, toolbar.status) }
    }

    @Test fun inactiveRowsCannotDismissTheActiveSelectionMenuWhenTheyRedraw() {
        value.value = TextFieldValue("900")
        val other = mutableStateOf(TextFieldValue("100"))
        compose.setContent {
            CompositionLocalProvider(LocalTextToolbar provides toolbar) {
                Column {
                    MathEditor(value.value, true, 1, Modifier.width(300.dp),
                        onInput = { value.value = it }, onFocus = {}, onSelect = {})
                    MathEditor(other.value, false, 2, Modifier.width(300.dp),
                        onInput = {}, onFocus = {}, onSelect = {})
                }
            }
        }
        compose.onNodeWithContentDescription("Equation 1").performTouchInput { longClick(center) }
        compose.runOnIdle {
            assertEquals(TextToolbarStatus.Shown, toolbar.status)
            other.value = TextFieldValue("10000000")
        }
        compose.runOnIdle { assertEquals(TextToolbarStatus.Shown, toolbar.status) }
    }
}
