package dev.demos.graphing.ui

import kotlinx.serialization.Serializable
data class ExpressionRow(
    val id: Long,
    val text: String,
    val result: String = "",
    val approximate: String = "",
    val error: String? = null,
    val color: Int = 0xFFC74440.toInt(),
    val isEvaluating: Boolean = false,
    val hidden: Boolean = false,
    val folderId: Long? = null,
)

data class SavedGraph(val id: String, val title: String, val updatedAt: Long)

enum class CalculatorLayout { Split, Graph, Calculator }
enum class GraphExportFormat(val extension: String, val mimeType: String) {
    PNG("png", "image/png"), PDF("pdf", "application/pdf")
}
enum class TableFileFormat(val extension: String, val mimeType: String) {
    CSV("csv", "text/csv"), TSV("tsv", "text/tab-separated-values"), JSON("json", "application/json")
}

data class SliderUi(
    val name: String,
    val value: Double,
    val min: Double = -10.0,
    val max: Double = 10.0,
    val step: Double = 0.1,
    val playing: Boolean = false,
    val animation: SliderAnimation = SliderAnimation.Oscillate,
    val speed: Double = 1.0,
)

@Serializable
enum class SliderAnimation { Oscillate, Forward, Reverse, Once }

@Serializable
data class SliderConfiguration(val min: Double = -10.0, val max: Double = 10.0, val step: Double = 0.0,
    val animation: SliderAnimation = SliderAnimation.Oscillate, val speed: Double = 1.0)

data class GraphBounds(
    val xMin: Double? = null, val xMax: Double? = null,
    val yMin: Double? = null, val yMax: Double? = null,
    val zMin: Double? = null, val zMax: Double? = null,
    val tMin: Double = -10.0, val tMax: Double = 10.0,
    val uMin: Double = -5.0, val uMax: Double = 5.0,
    val vMin: Double = -5.0, val vMax: Double = 5.0,
)

data class CalculatorUiState(
    val rows: List<ExpressionRow>,
    val activeId: Long,
    val title: String = "Untitled Graph",
    val is3D: Boolean = false,
    val dirty: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val busy: Boolean = false,
    val savedGraphs: List<SavedGraph> = emptyList(),
    val sliders: List<SliderUi> = emptyList(),
    val degreeMode: Boolean = false,
    val showGrid: Boolean = true,
    val showAxes: Boolean = true,
    val bounds: GraphBounds = GraphBounds(),
    val precisionDigits: Int = 15,
    val complexMode: Boolean = false,
    val statusMessage: String? = null,
    val layout: CalculatorLayout = CalculatorLayout.Split,
    val keyboardVisible: Boolean = true,
    val sliderSettings: Map<String, SliderConfiguration> = emptyMap(),
    val tableFits: Map<Long, dev.demos.graphing.math.RegressionFit> = emptyMap(),
    val documentRevision: Long = 0,
    val tableFitting: Set<Long> = emptySet(),
)

sealed interface CalculatorAction {
    data class EditRow(val id: Long, val text: String) : CalculatorAction
    data class EditTableCell(val id: Long, val row: Int, val column: Int, val text: String) : CalculatorAction
    data class EditTableHeader(val id: Long, val column: Int, val text: String) : CalculatorAction
    data class DeleteTableRow(val id: Long, val row: Int) : CalculatorAction
    data class SetTableOptions(val id: Long, val options: TableOptions) : CalculatorAction
    data class SortTable(val id: Long) : CalculatorAction
    data class SelectRow(val id: Long) : CalculatorAction
    data object AddRow : CalculatorAction
    data object AppendRow : CalculatorAction
    data class AddItem(val source: String) : CalculatorAction
    data object ImportImage : CalculatorAction
    data class CreateSliders(val names: List<String>) : CalculatorAction
    data class ConfigureSlider(val name: String, val configuration: SliderConfiguration) : CalculatorAction
    data class DeleteRow(val id: Long) : CalculatorAction
    data class MoveRow(val id: Long, val direction: Int) : CalculatorAction
    data class ToggleVisibility(val id: Long) : CalculatorAction
    data class SetColor(val id: Long, val color: Int) : CalculatorAction
    data object Undo : CalculatorAction
    data object Redo : CalculatorAction
    data object Toggle3D : CalculatorAction
    data class Save(val title: String) : CalculatorAction
    data class Open(val id: String) : CalculatorAction
    data object New : CalculatorAction
    data class DeleteGraph(val id: String) : CalculatorAction
    data object ExportImage : CalculatorAction
    data object ExportDocument : CalculatorAction
    data object ImportDocument : CalculatorAction
    data class ExportGraph(val format: GraphExportFormat) : CalculatorAction
    data class ExportTable(val id: Long, val format: TableFileFormat) : CalculatorAction
    data object ImportTable : CalculatorAction
    data class SetLayout(val layout: CalculatorLayout) : CalculatorAction
    data class SetKeyboardVisible(val visible: Boolean) : CalculatorAction
    data class SetDegreeMode(val enabled: Boolean) : CalculatorAction
    data class SetGrid(val enabled: Boolean) : CalculatorAction
    data class SetAxes(val enabled: Boolean) : CalculatorAction
    data class SetBounds(val bounds: GraphBounds) : CalculatorAction
    data class SetPrecisionDigits(val digits: Int) : CalculatorAction
    data class SetComplexMode(val enabled: Boolean) : CalculatorAction
    data class SetSlider(val name: String, val value: Double) : CalculatorAction
    data class ToggleSlider(val name: String) : CalculatorAction
}
