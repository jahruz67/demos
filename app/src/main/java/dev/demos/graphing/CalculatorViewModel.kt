package dev.demos.graphing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.demos.graphing.cas.CasEngine
import dev.demos.graphing.data.GraphRepository
import dev.demos.graphing.platform.AndroidCalculatorPreferences
import dev.demos.graphing.ui.CalculatorAction
import dev.demos.graphing.ui.CalculatorUiState

/** Android lifecycle owner for the calculator shared with Windows. */
class CalculatorViewModel(application: Application) : AndroidViewModel(application) {
    private val calculator = CalculatorController(
        GraphRepository(application), CasEngine(application),
        AndroidCalculatorPreferences(application), viewModelScope,
    )
    val state = calculator.state
    val effects = calculator.effects
    fun loadLibrary() = calculator.loadLibrary()
    fun onAction(action: CalculatorAction) = calculator.onAction(action)
    fun importDocument(contents: String) = calculator.importDocument(contents)
    fun importTable(contents: String) = calculator.importTable(contents)
    fun importImage(uri: String, name: String, aspect: Double) = calculator.importImage(uri, name, aspect)
    fun showMessage(message: String) = calculator.showMessage(message)
    fun plotExpressions(current: CalculatorUiState) = calculator.plotExpressions(current)
    fun plotParameters(current: CalculatorUiState) = calculator.plotParameters(current)
    override fun onCleared() { calculator.close(); super.onCleared() }
}
