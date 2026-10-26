package dev.demos.graphing

import android.content.Context
import androidx.lifecycle.ViewModelStore
import dev.demos.graphing.ui.CalculatorAction
import dev.demos.graphing.ui.CalculatorLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class DisplayPreferencesTest {
    private val store = ViewModelStore()
    private val application get() = RuntimeEnvironment.getApplication()
    private val preferences get() = application.getSharedPreferences("calculator-layout", Context.MODE_PRIVATE)

    @Before fun create() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        preferences.edit().clear().putString("layout", "Graph").commit()
    }

    @After fun close() {
        store.clear()
        preferences.edit().clear().commit()
        Dispatchers.resetMain()
    }

    private fun restoredModel(key: String): CalculatorViewModel {
        val model = CalculatorViewModel(application)
        store.put(key, model)
        runBlocking { withTimeout(5000) { model.state.first { it.layout == CalculatorLayout.Graph } } }
        return model
    }

    @Test fun defaultsAreDecimalAndNormalHeightAndChoicesSurviveReopening() {
        val model = restoredModel("first")
        assertTrue(model.state.value.defaultDecimal)
        assertEquals(1f, model.state.value.keyboardHeightScale)
        model.onAction(CalculatorAction.SetDefaultDecimal(false))
        model.onAction(CalculatorAction.SetKeyboardHeightScale(1.25f))
        assertFalse(model.state.value.dirty)
        val reopened = restoredModel("reopened")
        assertFalse(reopened.state.value.defaultDecimal)
        assertEquals(1.25f, reopened.state.value.keyboardHeightScale)
        reopened.onAction(CalculatorAction.SetDefaultDecimal(true))
        assertTrue(restoredModel("decimal").state.value.defaultDecimal)
    }

    @Test fun keyboardHeightRejectsNonfiniteValuesAndClampsToItsRange() {
        val model = restoredModel("height")
        model.onAction(CalculatorAction.SetKeyboardHeightScale(10f))
        assertEquals(1.4f, model.state.value.keyboardHeightScale)
        model.onAction(CalculatorAction.SetKeyboardHeightScale(Float.NaN))
        model.onAction(CalculatorAction.SetKeyboardHeightScale(Float.POSITIVE_INFINITY))
        assertEquals(1.4f, model.state.value.keyboardHeightScale)
        model.onAction(CalculatorAction.SetKeyboardHeightScale(-1f))
        assertEquals(.8f, restoredModel("clamped").state.value.keyboardHeightScale)
    }
}
