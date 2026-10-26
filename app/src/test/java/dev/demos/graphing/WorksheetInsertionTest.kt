package dev.demos.graphing

import androidx.lifecycle.ViewModelStore
import dev.demos.graphing.ui.CalculatorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
class WorksheetInsertionTest {
    private val store = ViewModelStore()
    private lateinit var model: CalculatorViewModel

    @Before fun create() {
        Dispatchers.setMain(StandardTestDispatcher())
        model = CalculatorViewModel(RuntimeEnvironment.getApplication())
        store.put("worksheet", model)
        model.onAction(CalculatorAction.EditRow(1, "100"))
        model.onAction(CalculatorAction.AddRow)
        model.onAction(CalculatorAction.EditRow(model.state.value.activeId, "200"))
        model.onAction(CalculatorAction.SelectRow(1))
    }

    @After fun close() { store.clear(); Dispatchers.resetMain() }

    @Test fun bottomTapAppendsAfterTheLastExpressionAndUndoRestoresTheDocument() {
        model.onAction(CalculatorAction.AppendRow)
        assertEquals(listOf("100", "200", ""), model.state.value.rows.map { it.text })
        assertEquals(model.state.value.rows.last().id, model.state.value.activeId)
        model.onAction(CalculatorAction.Undo)
        assertEquals(listOf("100", "200"), model.state.value.rows.map { it.text })
        assertEquals(1L, model.state.value.activeId)
    }

    @Test fun returnStillInsertsImmediatelyAfterTheActiveExpression() {
        model.onAction(CalculatorAction.AddRow)
        assertEquals(listOf("100", "", "200"), model.state.value.rows.map { it.text })
        assertEquals(model.state.value.rows[1].id, model.state.value.activeId)
    }
}
