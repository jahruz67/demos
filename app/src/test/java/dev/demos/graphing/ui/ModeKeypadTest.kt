package dev.demos.graphing.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
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

    @Test fun portraitKeypadFollowsCalculatorAndGraphModes() = verifyModes(800, 1280)

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun landscapeKeypadFollowsCalculatorAndGraphModes() = verifyModes(1280, 800)

    @Test @Config(qualifiers = "w360dp-h800dp-mdpi")
    fun portraitPhoneKeypadFollowsCalculatorAndGraphModes() = verifyModes(360, 800)

    @Test @Config(qualifiers = "w800dp-h360dp-land-mdpi")
    fun landscapePhoneKeypadFollowsCalculatorAndGraphModes() = verifyModes(800, 360)

    private fun verifyModes(width: Int, height: Int) {
        val state = mutableStateOf(CalculatorUiState(listOf(ExpressionRow(1, "")), 1, layout = CalculatorLayout.Calculator))
        compose.setContent {
            CompositionLocalProvider(LocalAppConfiguration provides AppConfiguration(width, height)) {
                CalculatorScreen(state.value, onAction = { action ->
                    if (action is CalculatorAction.EditRow) state.value = state.value.copy(rows = listOf(ExpressionRow(1, action.text)))
                }, graph = { _, _ -> })
            }
        }
        compose.onNodeWithContentDescription("x").assertDoesNotExist()
        compose.onNodeWithContentDescription("y").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("aᵇ")[0].assertIsDisplayed().performClick()
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
