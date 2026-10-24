package dev.demos.graphing.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w800dp-h1280dp-mdpi")
class ModeKeypadTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun portraitKeypadFollowsCalculatorAndGraphModes() = verifyModes()

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun landscapeKeypadFollowsCalculatorAndGraphModes() = verifyModes()

    private fun verifyModes() {
        val state = mutableStateOf(CalculatorUiState(listOf(ExpressionRow(1, "")), 1, layout = CalculatorLayout.Calculator))
        compose.setContent { CalculatorScreen(state.value, onAction = { action ->
            if (action is CalculatorAction.EditRow) state.value = state.value.copy(rows = listOf(ExpressionRow(1, action.text)))
        }, graph = { _, _ -> }) }
        compose.onNodeWithContentDescription("x").assertDoesNotExist()
        compose.onNodeWithContentDescription("y").assertDoesNotExist()
        compose.onNodeWithContentDescription("aᵇ").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("^{}", state.value.rows.first().text) }
        compose.onNodeWithContentDescription("Equation 1").performTextReplacement("")
        compose.runOnIdle { state.value = state.value.copy(layout = CalculatorLayout.Split) }
        compose.onNodeWithContentDescription("x").assertIsDisplayed()
        compose.onNodeWithContentDescription("y").assertIsDisplayed()
        compose.onNodeWithContentDescription("z").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(is3D = true) }
        compose.onNodeWithContentDescription("z").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("z", state.value.rows.first().text) }
    }
}
