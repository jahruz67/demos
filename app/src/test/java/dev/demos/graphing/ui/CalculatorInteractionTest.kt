package dev.demos.graphing.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
@OptIn(ExperimentalTestApi::class)
class CalculatorInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun worksheet(rows: List<ExpressionRow>, keyboard: Boolean = true) {
        val state = mutableStateOf(CalculatorUiState(rows, rows.first().id,
            layout = CalculatorLayout.Calculator, keyboardVisible = keyboard))
        compose.setContent {
            CalculatorScreen(state.value, onAction = { action ->
                state.value = when (action) {
                    is CalculatorAction.SelectRow -> state.value.copy(activeId = action.id)
                    is CalculatorAction.EditRow -> state.value.copy(rows = state.value.rows.map {
                        if (it.id == action.id) it.copy(text = action.text) else it
                    })
                    is CalculatorAction.SetKeyboardVisible -> state.value.copy(keyboardVisible = action.visible)
                    CalculatorAction.AddRow, CalculatorAction.AppendRow -> {
                        val id = state.value.rows.maxOf { it.id } + 1
                        state.value.copy(rows = state.value.rows + ExpressionRow(id, ""), activeId = id)
                    }
                    else -> state.value
                }
            }, graph = { _, _ -> })
        }
    }

    @Test fun tappingBetweenZerosInAnInactiveNumberInsertsThere() {
        worksheet(listOf(ExpressionRow(1, "1"), ExpressionRow(2, "900")))
        compose.onAllNodesWithText("900", useUnmergedTree = true).onLast().performTouchInput {
            click(Offset(width * 2f / 3f, center.y))
        }
        compose.onNodeWithContentDescription("Equation 2").performTextInput("5")
        compose.onAllNodesWithText("9050", useUnmergedTree = true).onLast().assertExists()
    }

    @Test fun graphControlsFollowPlotsEvenWhileResultsArePending() {
        worksheet(listOf(
            ExpressionRow(1, "100", isEvaluating = true),
            ExpressionRow(2, "900", result = "900"),
            ExpressionRow(3, "2+3"),
            ExpressionRow(4, "x^2"),
            ExpressionRow(5, "y=100"),
            ExpressionRow(6, "(1,2)"),
            ExpressionRow(7, "a=2"),
        ), keyboard = false)
        for (number in listOf(1, 2, 3, 7)) {
            compose.onNodeWithContentDescription("Hide expression $number on graph; hold to change color").assertDoesNotExist()
        }
        for (number in listOf(4, 5, 6)) {
            compose.onNodeWithContentDescription("Hide expression $number on graph; hold to change color").assertExists()
        }
    }

    @Test fun focusingBottomRowTypingAndAddingRowsPreserveManualScrollPosition() {
        worksheet((1L..30L).map { ExpressionRow(it, (900 + it).toString()) })
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToIndex(8)
        compose.waitForIdle()
        val listBounds = list.fetchSemanticsNode().boundsInRoot
        val visible = compose.onAllNodes(hasContentDescription("Equation ", substring = true))
            .fetchSemanticsNodes().filter {
                it.boundsInRoot.top >= listBounds.top && it.boundsInRoot.bottom <= listBounds.bottom
            }
        val description = visible.last().config[SemanticsProperties.ContentDescription].first()
        fun scrollPosition() = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val before = scrollPosition()
        compose.onNodeWithContentDescription(description).performTouchInput { click(center) }
        compose.onNodeWithContentDescription(description).performTextInput("5")
        compose.waitForIdle()
        assertEquals(before, scrollPosition(), 0.001f)
        compose.onNodeWithContentDescription(description).performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals(before, scrollPosition(), 0.001f)
        list.performTouchInput { swipe(Offset(10f, height - 10f), Offset(10f, 10f), 300L) }
        compose.waitForIdle()
        assertTrue(scrollPosition() > before)
    }

    @Test fun tappingTheBottomAreaRequestsAppendRatherThanInsertAfterSelection() {
        val actions = mutableListOf<CalculatorAction>()
        compose.setContent {
            CalculatorScreen(CalculatorUiState(listOf(ExpressionRow(1, "100"), ExpressionRow(2, "200")), 1,
                layout = CalculatorLayout.Calculator), onAction = { actions += it }, graph = { _, _ -> })
        }
        compose.onNodeWithContentDescription("Add expression at end").performTouchInput { click(center) }
        compose.runOnIdle {
            assertTrue(actions.contains(CalculatorAction.AppendRow))
            assertFalse(actions.contains(CalculatorAction.AddRow))
        }
    }

    @Test fun decimalChoiceSurvivesScrollingTheResultOutOfTheList() {
        worksheet(listOf(ExpressionRow(1, "sqrt(2)", result = "sqrt(2)", approximate = "1.414213562373095")) +
            (2L..30L).map { ExpressionRow(it, it.toString()) })
        compose.onNodeWithContentDescription("Result 1").performClick()
        compose.onNodeWithText("1.414213562373095", useUnmergedTree = true).assertIsDisplayed()
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToIndex(29)
        list.performScrollToIndex(0)
        compose.onNodeWithText("1.414213562373095", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun anApproximationRemainsVisibleWhenTheExactAnswerIsUnchangedOrAbsent() {
        worksheet(listOf(ExpressionRow(1, "pi", approximate = "0.3141592653589793e1")))
        compose.onNodeWithContentDescription("Result 1").assertIsDisplayed()
        compose.onNodeWithText("3.141592653589793", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun piKeyKeepsTheConstantSeparateFromAdjacentText() {
        worksheet(listOf(ExpressionRow(1, "x")))
        compose.onNodeWithContentDescription("π").performClick()
        compose.onNodeWithContentDescription("π").performClick()
        compose.onNodeWithContentDescription("Equation 1").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("x*π*π")))
    }
}
