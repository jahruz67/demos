package dev.demos.graphing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w800dp-h1280dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletStartupTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun opensInPortraitAndRecreates() {
        compose.onNodeWithContentDescription("Equation 1").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Equation 1").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun opensInLandscape() {
        compose.onNodeWithContentDescription("Equation 1").assertIsDisplayed()
    }
}
