package dev.demos.graphing

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.demos.graphing.cas.CasEngine
import dev.demos.graphing.cas.CasResult
import dev.demos.graphing.cas.CasSyntax
import dev.demos.graphing.data.GraphDocument
import dev.demos.graphing.data.GraphRepository
import dev.demos.graphing.data.StoredExpression
import dev.demos.graphing.data.TableFiles
import dev.demos.graphing.math.FastArithmetic
import dev.demos.graphing.math.MathSyntax
import dev.demos.graphing.math.NumericCompiler
import dev.demos.graphing.math.TableEvaluation
import dev.demos.graphing.math.TableRegression
import dev.demos.graphing.plot.PlotExpression
import dev.demos.graphing.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

sealed interface CalculatorEffect {
    data class ExportDocument(val name: String, val contents: String) : CalculatorEffect
    data object ExportImage : CalculatorEffect
    data class ExportGraph(val format: GraphExportFormat) : CalculatorEffect
    data class ExportTable(val name: String, val contents: String, val format: TableFileFormat) : CalculatorEffect
    data object ImportTable : CalculatorEffect
    data object ImportDocument : CalculatorEffect
    data object ImportImage : CalculatorEffect
}

class CalculatorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GraphRepository(application)
    private val engine = CasEngine(application)
    private val preferences by lazy { application.getSharedPreferences("calculator-layout", Context.MODE_PRIVATE) }
    private val _state = MutableStateFlow(CalculatorUiState(
        rows = listOf(newRow(1)), activeId = 1,
    ))
    val state = _state.asStateFlow()
    private val effectsChannel = Channel<CalculatorEffect>(Channel.BUFFERED)
    val effects = effectsChannel.receiveAsFlow()
    private var graphId = UUID.randomUUID().toString()
    private var nextId = 2L
    private var evaluationJob: Job? = null
    private var recoveryJob: Job? = null
    private val animations = mutableMapOf<String, Job>()
    private val undo = ArrayDeque<GraphDocument>()
    private val redo = ArrayDeque<GraphDocument>()
    private var lastEditedId = -1L
    private var lastEditTarget: String? = null
    private var lastEditTime = 0L
    private var generation = 0L
    private var integrationConstant = 0.0
    private val derivedPlots = mutableMapOf<Long, Pair<String, String>>()

    private data class EvaluationKey(val source: String, val definitions: List<String>)
    private val completed = mutableMapOf<Long, Pair<EvaluationKey, CasResult>>()
    private val pending = mutableMapOf<Long, EvaluationKey>()
    private val requested = mutableMapOf<Long, EvaluationKey>()
    private data class TableFitKey(val sources: List<Pair<Long, String>>, val parameters: Map<String, Double>, val radians: Boolean)
    private var tableFitCache: Pair<TableFitKey, Map<Long, dev.demos.graphing.math.RegressionFit>>? = null
    private var restorePending = true

    private data class DisplayPreferences(val layout: CalculatorLayout, val keyboardVisible: Boolean,
        val defaultDecimal: Boolean, val keyboardHeightScale: Float)

    init {
        // Restore only the current document. Saved graphs are loaded when the library opens.
        // CasEngine binds lazily on the first expression that needs symbolic evaluation.
        viewModelScope.launch {
            val recoveryGeneration = generation
            // SharedPreferences can wait for disk on its first read. Keep both
            // preference loading and recovery decoding off the first-frame path.
            val (display, recovered) = withContext(Dispatchers.IO) {
                val layout = runCatching {
                    CalculatorLayout.valueOf(preferences.getString("layout", "Split") ?: "Split")
                }.getOrDefault(CalculatorLayout.Split)
                DisplayPreferences(layout, preferences.getBoolean("keyboard-visible", layout != CalculatorLayout.Graph),
                    preferences.getBoolean("default-decimal", true),
                    preferences.getFloat("keyboard-height-scale", 1f).takeIf { it.isFinite() }?.coerceIn(.8f, 1.4f) ?: 1f
                ) to repository.restoreRecovery()
            }
            _state.value = _state.value.copy(layout = display.layout, keyboardVisible = display.keyboardVisible,
                defaultDecimal = display.defaultDecimal, keyboardHeightScale = display.keyboardHeightScale)
            if (generation == recoveryGeneration && !_state.value.dirty && recovered != null) applyDocument(recovered)
            restorePending = false
            if (_state.value.dirty) changed() else scheduleEvaluation()
        }
    }

    fun loadLibrary() = viewModelScope.launch {
        runCatching { refreshSavedGraphs() }.onFailure { showMessage("Could not load saved graphs: ${it.message}") }
    }

    fun onAction(action: CalculatorAction) {
        when (action) {
            is CalculatorAction.EditTableCell -> editTable(action.id, "cell:${action.row}:${action.column}") { table ->
                if (action.row !in 0..table.size || action.column !in 0 until table.columnCount) table
                else table.withCell(action.row, action.column, action.text)
            }
            is CalculatorAction.EditTableHeader -> editTable(action.id, "header:${action.column}") { table ->
                if (action.column !in 0 until table.columnCount) table else table.withHeader(action.column, action.text)
            }
            is CalculatorAction.DeleteTableRow -> editTable(action.id) { table ->
                if (action.row !in 0 until table.size) table else table.withoutRow(action.row)
            }
            is CalculatorAction.SetTableOptions -> editTable(action.id) { table ->
                table.copy(options = action.options.copy(
                    xHeader = table.options.xHeader, yHeader = table.options.yHeader,
                    extraColumns = action.options.extraColumns.mapIndexed { index, column ->
                        column.copy(values = table.options.extraColumns.getOrNull(index)?.values ?: column.values)
                    },
                ))
            }
            is CalculatorAction.SortTable -> editTable(action.id) { table ->
                val snapshot = _state.value
                val number = snapshot.rows.takeWhile { it.id != action.id }.count { parseTableData(it.text) != null } + 1
                val x = TableEvaluation.columns(table, number, snapshot.sliders.associate { it.name to it.value }, !snapshot.degreeMode)[0]
                val order = (0 until table.size).sortedWith(compareBy { x[it] ?: Double.POSITIVE_INFINITY })
                table.copy(x = order.map { table.x.getOrElse(it) { "" } }, y = order.map { table.y.getOrElse(it) { "" } },
                    options = table.options.copy(extraColumns = table.options.extraColumns.map { column -> column.copy(values = order.map { column.values.getOrElse(it) { "" } }) }))
            }
            is CalculatorAction.EditRow -> {
                val row = _state.value.rows.find { it.id == action.id } ?: return
                if (row.text == action.text) return
                rememberUndo(action.id)
                _state.value = _state.value.copy(rows = _state.value.rows.map {
                    if (it.id == action.id) it.copy(text = action.text, result = "", approximate = "", error = null) else it
                }, activeId = action.id, dirty = true, statusMessage = null,
                    tableFits = if (parseTableData(row.text) != null) _state.value.tableFits - action.id else _state.value.tableFits)
                changed()
            }
            is CalculatorAction.SelectRow -> if (_state.value.rows.any { it.id == action.id }) _state.value = _state.value.copy(activeId = action.id)
            CalculatorAction.AddRow -> insertItem("", reuseEmpty = false)
            CalculatorAction.AppendRow -> insertItem("", reuseEmpty = false, append = true)
            is CalculatorAction.AddItem -> insertItem(action.source)
            CalculatorAction.ImportImage -> viewModelScope.launch { effectsChannel.send(CalculatorEffect.ImportImage) }
            is CalculatorAction.CreateSliders -> {
                rememberUndo()
                val rows = _state.value.rows.toMutableList()
                var index = rows.indexOfFirst { it.id == _state.value.activeId }.let { if (it < 0) rows.size else it + 1 }
                val parent = rows.firstOrNull { it.id == _state.value.activeId }?.folderId
                action.names.distinct().filter { name -> name.matches(Regex("[A-Za-z][A-Za-z0-9_]*")) && rows.none { MathSyntax.definition(it.text)?.name == name } }.forEach { name ->
                    rows.add(index++, newRow(nextId++).copy(text = "$name=1", folderId = parent))
                }
                _state.value = _state.value.copy(rows = rows, activeId = rows.getOrNull(index - 1)?.id ?: _state.value.activeId, dirty = true)
                changed()
            }
            is CalculatorAction.ConfigureSlider -> {
                val config = action.configuration
                if (!config.min.isFinite() || !config.max.isFinite() || config.min >= config.max || !config.step.isFinite() || config.step < 0 || !config.speed.isFinite() || config.speed <= 0) return
                val restart = action.name in animations && _state.value.sliders.firstOrNull { it.name == action.name }?.animation != config.animation
                rememberUndo()
                _state.value = _state.value.copy(sliderSettings = _state.value.sliderSettings + (action.name to config), dirty = true)
                changed(evaluate = false)
                _state.value.sliders.find { it.name == action.name }?.let { setSlider(it.name, it.value.coerceIn(config.min, config.max)) }
                if (restart) { toggleAnimation(action.name); toggleAnimation(action.name) }
            }
            is CalculatorAction.DeleteRow -> {
                if (_state.value.rows.none { it.id == action.id }) return
                rememberUndo()
                val oldRows = _state.value.rows
                val position = oldRows.indexOfFirst { it.id == action.id }
                val rows = oldRows.filterNot { it.id == action.id || it.folderId == action.id }.ifEmpty { listOf(newRow(nextId++)) }
                val active = _state.value.activeId.takeIf { id -> rows.any { it.id == id } } ?: rows[position.coerceIn(0, rows.lastIndex)].id
                _state.value = _state.value.copy(rows = rows, activeId = active, dirty = true)
                changed()
            }
            is CalculatorAction.MoveRow -> {
                val rows = _state.value.rows
                val row = rows.firstOrNull { it.id == action.id } ?: return
                val siblings = rows.filter { it.folderId == row.folderId }
                val index = siblings.indexOf(row)
                val neighbor = siblings.getOrNull(index + if (action.direction < 0) -1 else 1) ?: return
                val block = rows.filter { it.id == row.id || it.folderId == row.id }
                val remaining = rows.filterNot { item -> block.any { it.id == item.id } }.toMutableList()
                val destination = if (action.direction < 0) remaining.indexOfFirst { it.id == neighbor.id } else
                    remaining.indexOfLast { it.id == neighbor.id || it.folderId == neighbor.id } + 1
                rememberUndo()
                remaining.addAll(destination.coerceIn(0, remaining.size), block)
                _state.value = _state.value.copy(rows = remaining, dirty = true)
                changed()
            }
            is CalculatorAction.ToggleVisibility -> {
                rememberUndo()
                _state.value = _state.value.copy(rows = _state.value.rows.map {
                    if (it.id == action.id) it.copy(hidden = !it.hidden) else it
                }, dirty = true)
                changed(evaluate = false)
            }
            is CalculatorAction.SetColor -> {
                rememberUndo()
                _state.value = _state.value.copy(rows = _state.value.rows.map {
                    if (it.id == action.id) it.copy(color = action.color) else it
                }, dirty = true)
                changed(evaluate = false)
            }
            CalculatorAction.Undo -> if (undo.isNotEmpty()) {
                redo.addLast(document())
                stopAnimations()
                applyDocument(undo.removeLast(), dirty = true)
                changed()
            }
            CalculatorAction.Redo -> if (redo.isNotEmpty()) {
                undo.addLast(document())
                stopAnimations()
                applyDocument(redo.removeLast(), dirty = true)
                changed()
            }
            CalculatorAction.Toggle3D -> {
                rememberUndo()
                _state.value = _state.value.copy(is3D = !_state.value.is3D, dirty = true)
                changed(evaluate = false)
            }
            is CalculatorAction.SetLayout -> {
                val showKeyboard = when (action.layout) {
                    CalculatorLayout.Calculator -> true
                    CalculatorLayout.Graph -> false
                    CalculatorLayout.Split -> _state.value.keyboardVisible
                }
                _state.value = _state.value.copy(layout = action.layout, keyboardVisible = showKeyboard)
                preferences.edit().putString("layout", action.layout.name).putBoolean("keyboard-visible", showKeyboard).apply()
            }
            is CalculatorAction.SetKeyboardVisible -> {
                if (_state.value.keyboardVisible == action.visible) return
                _state.value = _state.value.copy(keyboardVisible = action.visible)
                preferences.edit().putBoolean("keyboard-visible", action.visible).apply()
            }
            is CalculatorAction.SetDefaultDecimal -> {
                _state.value = _state.value.copy(defaultDecimal = action.enabled)
                preferences.edit().putBoolean("default-decimal", action.enabled).apply()
            }
            is CalculatorAction.SetKeyboardHeightScale -> {
                if (!action.scale.isFinite()) return
                val scale = action.scale.coerceIn(.8f, 1.4f)
                _state.value = _state.value.copy(keyboardHeightScale = scale)
                preferences.edit().putFloat("keyboard-height-scale", scale).apply()
            }
            is CalculatorAction.ExportGraph -> viewModelScope.launch { effectsChannel.send(CalculatorEffect.ExportGraph(action.format)) }
            CalculatorAction.ImportTable -> viewModelScope.launch { effectsChannel.send(CalculatorEffect.ImportTable) }
            is CalculatorAction.ExportTable -> {
                val snapshot = _state.value
                val table = snapshot.rows.firstOrNull { it.id == action.id }?.let { parseTableData(it.text) } ?: return
                val index = snapshot.rows.takeWhile { it.id != action.id }.count { parseTableData(it.text) != null } + 1
                viewModelScope.launch {
                    val contents = withContext(Dispatchers.Default) { TableFiles.encode(table, action.format) }
                    effectsChannel.send(CalculatorEffect.ExportTable("${safeFileName(snapshot.title)}-table-$index.${action.format.extension}", contents, action.format))
                }
            }
            is CalculatorAction.Save -> save(action.title)
            is CalculatorAction.Open -> viewModelScope.launch {
                runCatching {
                    saveBeforeSwitch()
                    val loaded = withContext(Dispatchers.IO) { repository.load(action.id) }
                    stopAnimations(); undo.clear(); redo.clear()
                    applyDocument(loaded); changed()
                }.onFailure { showMessage("Could not open graph: ${it.message}") }
            }
            CalculatorAction.New -> viewModelScope.launch {
                saveBeforeSwitch(); stopAnimations(); undo.clear(); redo.clear()
                applyDocument(GraphDocument(expressions = listOf(StoredExpression(1, "", palette[0]))))
                changed()
            }
            is CalculatorAction.DeleteGraph -> viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { repository.delete(action.id) }; refreshSavedGraphs() }
                    .onFailure { showMessage("Could not delete graph: ${it.message}") }
            }
            CalculatorAction.ExportDocument -> viewModelScope.launch {
                val encoded = withContext(Dispatchers.Default) { repository.encode(document()) }
                effectsChannel.send(CalculatorEffect.ExportDocument("${safeFileName(_state.value.title)}.demos.json", encoded))
            }
            CalculatorAction.ExportImage -> viewModelScope.launch { effectsChannel.send(CalculatorEffect.ExportImage) }
            CalculatorAction.ImportDocument -> viewModelScope.launch { effectsChannel.send(CalculatorEffect.ImportDocument) }
            is CalculatorAction.SetDegreeMode -> {
                rememberUndo(); _state.value = _state.value.copy(degreeMode = action.enabled, dirty = true); changed()
            }
            is CalculatorAction.SetGrid -> {
                rememberUndo(); _state.value = _state.value.copy(showGrid = action.enabled, dirty = true); changed(evaluate = false)
            }
            is CalculatorAction.SetAxes -> {
                rememberUndo(); _state.value = _state.value.copy(showAxes = action.enabled, dirty = true); changed(evaluate = false)
            }
            is CalculatorAction.SetBounds -> {
                rememberUndo(); _state.value = _state.value.copy(bounds = action.bounds, dirty = true); changed(evaluate = false)
            }
            is CalculatorAction.SetPrecisionDigits -> {
                rememberUndo(); _state.value = _state.value.copy(precisionDigits = action.digits.coerceIn(10, 1000), dirty = true); changed()
            }
            is CalculatorAction.SetComplexMode -> {
                rememberUndo(); _state.value = _state.value.copy(complexMode = action.enabled, dirty = true); changed()
            }
            is CalculatorAction.SetSlider -> setSlider(action.name, action.value)
            is CalculatorAction.ToggleSlider -> toggleAnimation(action.name)
        }
    }

    fun importDocument(contents: String) {
        viewModelScope.launch {
            runCatching {
                val imported = withContext(Dispatchers.IO) { repository.decode(contents).copy(id = UUID.randomUUID().toString()) }
                saveBeforeSwitch(); stopAnimations(); undo.clear(); redo.clear()
                applyDocument(imported, dirty = true); changed()
                showMessage("Graph imported")
            }.onFailure { showMessage("Could not import graph: ${it.message}") }
        }
    }

    fun importTable(contents: String) {
        viewModelScope.launch {
            runCatching {
                val table = withContext(Dispatchers.Default) { TableFiles.decode(contents) }
                rememberUndo()
                val row = newRow(nextId++).copy(text = encodeTableData(table))
                val rows = _state.value.rows
                _state.value = _state.value.copy(
                    rows = if (rows.size == 1 && rows[0].text.isBlank()) listOf(row) else rows + row,
                    activeId = row.id, dirty = true,
                    layout = if (_state.value.layout == CalculatorLayout.Graph) CalculatorLayout.Split else _state.value.layout,
                )
                changed()
                showMessage("Imported ${table.size} table rows")
            }.onFailure { showMessage("Could not import table: ${it.message}") }
        }
    }

    fun showMessage(message: String) { _state.value = _state.value.copy(statusMessage = message) }

    private fun editTable(id: Long, target: String? = null, transform: (TableData) -> TableData) {
        val row = _state.value.rows.firstOrNull { it.id == id } ?: return
        val table = parseTableData(row.text) ?: return
        val next = transform(table)
        if (next == table) return
        rememberUndo(if (target == null) -1 else id, target)
        _state.value = _state.value.copy(rows = _state.value.rows.map {
            if (it.id == id) it.copy(text = encodeTableData(next), result = "", error = null) else it
        }, activeId = id, dirty = true, statusMessage = null, tableFits = _state.value.tableFits - id)
        changed()
    }

    private fun insertItem(source: String, reuseEmpty: Boolean = true, append: Boolean = false) {
        rememberUndo()
        val snapshot = _state.value
        val rows = snapshot.rows.toMutableList()
        val position = if (append) rows.lastIndex else rows.indexOfFirst { it.id == snapshot.activeId }.coerceAtLeast(0)
        val active = rows[position]
        val folder = parseFolderData(active.text)
        val parent = if (append) null else if (folder != null) active.id else active.folderId
        if (!append && folder?.collapsed == true) rows[position] = active.copy(text = encodeFolderData(folder.copy(collapsed = false)))
        val isTable = parseTableData(source) != null
        val row = if (reuseEmpty && active.text.isBlank()) active.copy(text = source,
            color = if (isTable) 0xFF2D70B3.toInt() else active.color) else
            newRow(nextId++).copy(text = source, folderId = parent, color = if (isTable) 0xFF2D70B3.toInt() else newRow(nextId - 1).color)
        if (row.id == active.id) rows[position] = row else rows.add(position + 1, row)
        _state.value = snapshot.copy(rows = rows, activeId = row.id, dirty = true,
            layout = if (snapshot.layout == CalculatorLayout.Graph) CalculatorLayout.Split else snapshot.layout)
        changed()
    }

    fun importImage(uri: String, name: String, aspect: Double) {
        insertItem(encodeImageData(ImageData(uri = uri, name = name, height = 10.0 / aspect.coerceIn(0.01, 100.0))))
    }

    fun plotExpressions(current: CalculatorUiState): List<PlotExpression> = current.rows.filterNot { (it.hidden && parseTableData(it.text) == null) || current.rows.any { parent -> parent.id == it.folderId && parent.hidden } }.flatMap { row ->
        val table = parseTableData(row.text)
        if (table != null) {
            val number = current.rows.takeWhile { it.id != row.id }.count { parseTableData(it.text) != null } + 1
            val columns = TableEvaluation.columns(table, number, plotParameters(current), !current.degreeMode)
            return@flatMap buildList {
                for (column in 1 until columns.size) {
                    if (column == 1 && row.hidden) continue
                    if (column > 1 && table.options.extraColumns[column - 2].hidden) continue
                    val pairs = columns[0].indices.mapNotNull { i -> columns[0][i]?.let { x -> columns[column].getOrNull(i)?.let { x to it } } }
                    if (pairs.isNotEmpty()) add(PlotExpression(row.id, "table([${pairs.joinToString(",") { TableRegression.number(it.first) }}],[${pairs.joinToString(",") { TableRegression.number(it.second) }}])",
                        if (column == 1) row.color else table.options.extraColumns[column - 2].color))
                }
                current.tableFits[row.id]?.let { fit ->
                    if (!table.options.regressionHidden) add(PlotExpression(row.id, "y=${fit.expression}", table.options.regressionColor))
                    if (table.options.residuals) add(PlotExpression(row.id, "table([${fit.x.joinToString(",", transform = TableRegression::number)}],[${fit.residuals.joinToString(",", transform = TableRegression::number)}])", 0xFF388C46.toInt()))
                }
            }
        }
        if (parseNoteText(row.text) != null || parseFolderData(row.text) != null || parseInferenceData(row.text) != null) return@flatMap emptyList()
        val normalized = runCatching { MathSyntax.normalize(row.text) }.getOrDefault(row.text).trim()
        val symbolic = normalized.startsWith("∫") || normalized.startsWith("\\int") ||
            symbolicPrefixRegex.containsMatchIn(normalized)
        val derived = derivedPlots[row.id]?.takeIf { it.first == row.text }?.second
        val plotSource = derived ?: if (symbolic && row.result.isNotBlank() && row.error == null) row.result else row.text
        listOf(PlotExpression(row.id, plotSource.replace(integralTrailingCRegex, "+C"), row.color))
    }

    fun plotParameters(current: CalculatorUiState): Map<String, Double> {
        val fitted = current.rows.filter { it.text.contains('~') && it.error == null }.flatMap { row ->
            row.result.split(',').mapNotNull { assignment ->
                val parts = assignment.split('=', limit = 2)
                if (parts.size != 2) null else runCatching { parts[0].trim() to NumericCompiler.compile(parts[1]).evaluate() }.getOrNull()?.takeIf { it.second.isFinite() }
            }
        }.toMap()
        return fitted + current.sliders.associate { it.name to it.value } + ("C" to integrationConstant)
    }

    private fun changed(evaluate: Boolean = true) {
        _state.value = _state.value.copy(canUndo = undo.isNotEmpty(), canRedo = redo.isNotEmpty())
        updateSliders()
        if (evaluate) scheduleEvaluation()
        recoveryJob?.cancel()
        val snapshot = document()
        recoveryJob = viewModelScope.launch {
            delay(450)
            if (restorePending) return@launch
            runCatching { withContext(Dispatchers.IO) { repository.saveRecovery(snapshot) } }
                .onFailure { showMessage("Could not recover graph automatically: ${it.message}") }
        }
    }

    private fun scheduleEvaluation() {
        evaluationJob?.cancel()
        requested.clear()
        val currentGeneration = ++generation
        val snapshot = _state.value
        val parameters = snapshot.sliders.associate { it.name to it.value }
        val fitKey = TableFitKey(snapshot.rows.filter { parseTableData(it.text) != null }.map { it.id to it.text }, parameters, !snapshot.degreeMode)
        _state.value = _state.value.copy(tableFitting = if (tableFitCache?.first == fitKey) emptySet() else snapshot.rows.filter { parseTableData(it.text)?.options?.regression != null }.map { it.id }.toSet())
        evaluationJob = viewModelScope.launch {
            val fits = tableFitCache?.takeIf { it.first == fitKey }?.second ?: withContext(Dispatchers.Default) {
                snapshot.rows.mapNotNull { row ->
                    val table = parseTableData(row.text) ?: return@mapNotNull null
                    val model = table.options.regression ?: return@mapNotNull null
                    val index = snapshot.rows.takeWhile { it.id != row.id }.count { parseTableData(it.text) != null } + 1
                    TableRegression.fit(model, TableEvaluation.pairs(table, index, parameters, !snapshot.degreeMode), table.options.logMode)?.let { row.id to it }
                }.toMap()
            }
            if (currentGeneration != generation) return@launch
            tableFitCache = fitKey to fits
            _state.value = _state.value.copy(tableFits = fits, tableFitting = emptySet())
            val evaluations = withContext(Dispatchers.Default) {
                val tableDefinitions = snapshot.rows.mapNotNull { parseTableData(it.text) }.flatMapIndexed { index, table ->
                    val number = index + 1
                    val columns = TableEvaluation.columns(table, number, parameters, !snapshot.degreeMode)
                    columns.mapIndexedNotNull { column, values ->
                        val name = table.header(column, number).substringBefore('=').trim()
                        if (!Regex("[A-Za-z][A-Za-z0-9_]*").matches(name)) null else "$name:=[${values.filterNotNull().joinToString(",", transform = TableRegression::number)}]"
                    }
                } + snapshot.rows.mapIndexedNotNull { index, row ->
                    val fit = fits[row.id] ?: return@mapIndexedNotNull null
                    val number = snapshot.rows.take(index + 1).count { parseTableData(it.text) != null }
                    "e_$number:=[${fit.residuals.joinToString(",", transform = TableRegression::number)}]"
                }
                val definitions = snapshot.rows.mapNotNull { row -> definition(row.text)?.let { row.id to it } }
                val regressions = snapshot.rows.mapNotNull { row -> CasSyntax.regressionDefinition(row.text)?.let { row.id to it } }
                snapshot.rows.filter {
                    it.text.isNotBlank() && parseTableData(it.text) == null && parseNoteText(it.text) == null && parseFolderData(it.text) == null && parseImageData(it.text) == null && parseInferenceData(it.text) == null
                }.map { row ->
                    val numeric = if (!row.text.contains('=') && !row.text.contains('~')) runCatching {
                        val compiled = NumericCompiler.compile(row.text, !snapshot.degreeMode, MathSyntax.Symbols(variables = parameters.keys))
                        if (compiled.variables.isNotEmpty() && compiled.variables.all { it in parameters }) compiled.evaluate(parameters).takeIf { it.isFinite() }?.let { CasResult(row.text, TableRegression.number(it)) } else null
                    }.getOrNull() else null
                    val isSlider = runCatching { MathSyntax.definition(row.text)?.name?.let { name -> snapshot.sliders.any { it.name == name } } == true }.getOrDefault(false)
                    val fast = if (isSlider) CasResult(row.text, "") else numeric ?: FastArithmetic.evaluate(row.text, snapshot.precisionDigits) ?: incompleteResult(row.text)
                    val context = if (fast != null) listOf("Digits:=${snapshot.precisionDigits}") else
                        listOf("angle_radian:=${if (snapshot.degreeMode) 0 else 1}",
                            "complex_mode:=${if (snapshot.complexMode) 1 else 0}", "Digits:=${snapshot.precisionDigits}") +
                            tableDefinitions + definitions.filter { it.first != row.id }.map { it.second } +
                            regressions.filter { it.first != row.id }.map { it.second }
                    Triple(row, EvaluationKey(row.text, context), fast)
                }
            }
            if (currentGeneration != generation) return@launch
            requested.putAll(evaluations.associate { it.first.id to it.second })
            pending.toMap().forEach { (id, key) ->
                if (requested[id] != key) { pending.remove(id); engine.cancel("row:$id") }
            }
            val ids = snapshot.rows.map { it.id }.toSet()
            completed.keys.retainAll(ids)
            derivedPlots.keys.retainAll(ids)
            _state.value = _state.value.copy(rows = _state.value.rows.map {
                if (pending[it.id] == null) it.copy(isEvaluating = false) else it
            })
            val symbolic = mutableListOf<Pair<ExpressionRow, EvaluationKey>>()
            evaluations.forEach { (row, key, fast) ->
                val cached = completed[row.id]?.takeIf { it.first == key }?.second
                when {
                    fast != null || cached != null -> {
                        val result = fast ?: cached!!
                        completed[row.id] = key to result
                        applyResult(row.id, row.text, result)
                    }
                    pending[row.id] == key -> Unit
                    else -> symbolic += row to key
                }
            }
            // Only symbolic work is debounced. Ordinary arithmetic has already returned.
            if (symbolic.isNotEmpty()) delay(80)
            if (currentGeneration != generation) return@launch
            symbolic.forEach { (row, key) ->
                pending[row.id] = key
                engine.evaluate("row:${row.id}", row.text, key.definitions) { result ->
                    if (pending[row.id] == key) {
                        pending.remove(row.id)
                        completed[row.id] = key to result
                        if (requested[row.id] == key) applyResult(row.id, row.text, result)
                    }
                }
            }
            // Avoid flashing a spinner for calculations that finish within a frame or two.
            if (symbolic.isNotEmpty()) delay(150)
            if (currentGeneration == generation) _state.value = _state.value.copy(rows = _state.value.rows.map {
                it.copy(isEvaluating = pending[it.id] != null)
            })
        }
    }

    /** Do not load Giac for a half-typed operator or an unfinished template. */
    private fun incompleteResult(source: String): CasResult? {
        val text = source.trim()
        if (text.lastOrNull()?.let { it in "+-*/^=,×÷−" } == true ||
            Regex("[(,]\\s*,|,\\s*\\)").containsMatchIn(text) ||
            MathSyntax.unaryFunctions.any { Regex("\\b$it\\s*\\(\\s*\\)").containsMatchIn(text) }) return CasResult(source, "")
        var depth = 0
        for (c in text) {
            if (c == '(' || c == '[' || c == '{') depth++
            if (c == ')' || c == ']' || c == '}') depth--
        }
        return if (depth > 0) CasResult(source, "") else null
    }

    private fun applyResult(id: Long, source: String, result: CasResult) {
        val row = _state.value.rows.find { it.id == id && it.text == source } ?: return
        val exact = result.exact
        if (result.error == null && result.plotExpression != null) derivedPlots[id] = source to result.plotExpression
        else derivedPlots.remove(id)
        val unchanged = compact(exact) == compact(source) || compact(exact) == compact(source.replace("=", ":="))
        val displayed = if (unchanged && result.error == null && result.approximate.isBlank()) "" else exact
        if (row.result == displayed && row.approximate == result.approximate && row.error == result.error && !row.isEvaluating) return
        _state.value = _state.value.copy(rows = _state.value.rows.map {
            if (it.id == row.id) it.copy(result = displayed, approximate = result.approximate, error = result.error, isEvaluating = false) else it
        })
        updateSliders()
    }

    private fun definition(source: String): String? {
        val parsed = runCatching { MathSyntax.definition(source) }.getOrNull() ?: return null
        if (parsed.name in setOf("x", "y", "z", "r", "theta")) return null
        return "${parsed.target}:=${parsed.body}"
    }

    private fun rememberUndo(editedId: Long = -1, target: String? = null) {
        val now = System.currentTimeMillis()
        if (editedId < 0 || editedId != lastEditedId || target != lastEditTarget || now - lastEditTime > 750) {
            undo.addLast(document()); while (undo.size > 100) undo.removeFirst()
        }
        lastEditedId = editedId; lastEditTarget = target; lastEditTime = now; redo.clear()
    }

    private fun save(title: String) = viewModelScope.launch {
        val snapshot = document().copy(title = title.trim().ifBlank { "Untitled Graph" })
        runCatching {
            withContext(Dispatchers.IO) { repository.save(snapshot) }
            if (document().expressions == snapshot.expressions) _state.value = _state.value.copy(title = snapshot.title, dirty = false)
            refreshSavedGraphs(); showMessage("Saved on this device")
        }.onFailure { showMessage("Could not save graph: ${it.message}") }
    }

    private suspend fun saveBeforeSwitch() {
        if (_state.value.dirty && _state.value.rows.any { it.text.isNotBlank() }) {
            val snapshot = document()
            withContext(Dispatchers.IO) { repository.save(snapshot) }
            refreshSavedGraphs()
        }
    }

    private suspend fun refreshSavedGraphs() {
        val graphs = withContext(Dispatchers.IO) { repository.list() }
        _state.value = _state.value.copy(savedGraphs = graphs.map { SavedGraph(it.id, it.title, it.updatedAt) })
    }

    private fun document(): GraphDocument = GraphDocument(
        id = graphId, title = _state.value.title,
        activeId = _state.value.activeId,
        expressions = _state.value.rows.map { StoredExpression(it.id, it.text, it.color, it.hidden, it.folderId) },
        sliderSettings = _state.value.sliderSettings,
        is3D = _state.value.is3D, degreeMode = _state.value.degreeMode,
        showGrid = _state.value.showGrid, showAxes = _state.value.showAxes,
        parameters = mapOf("C" to integrationConstant),
        precisionDigits = _state.value.precisionDigits,
        complexMode = _state.value.complexMode,
        viewBounds = with(_state.value.bounds) { mapOf("xMin" to xMin, "xMax" to xMax, "yMin" to yMin, "yMax" to yMax, "zMin" to zMin, "zMax" to zMax, "tMin" to tMin, "tMax" to tMax, "uMin" to uMin, "uMax" to uMax, "vMin" to vMin, "vMax" to vMax).filterValues { it != null }.mapValues { it.value!! } },
    )

    private fun applyDocument(doc: GraphDocument, dirty: Boolean = false) {
        generation++
        engine.cancelPending()
        completed.clear(); pending.clear(); requested.clear()
        derivedPlots.clear()
        tableFitCache = null
        lastEditedId = -1L
        lastEditTarget = null
        lastEditTime = 0L
        graphId = doc.id
        integrationConstant = doc.parameters["C"] ?: 0.0
        val rows = doc.expressions.map { ExpressionRow(it.id, it.text, color = it.color, hidden = it.hidden, folderId = it.folderId) }.ifEmpty { listOf(newRow(1)) }
        nextId = maxOf(nextId, (rows.maxOfOrNull { it.id } ?: 0) + 1)
        _state.value = _state.value.copy(rows = rows, activeId = doc.activeId?.takeIf { id -> rows.any { it.id == id } } ?: rows.first().id, title = doc.title,
            is3D = doc.is3D, degreeMode = doc.degreeMode, showGrid = doc.showGrid, showAxes = doc.showAxes,
            precisionDigits = doc.precisionDigits, complexMode = doc.complexMode,
            bounds = with(doc.viewBounds) { GraphBounds(get("xMin"), get("xMax"), get("yMin"), get("yMax"), get("zMin"), get("zMax"), get("tMin") ?: -10.0, get("tMax") ?: 10.0, get("uMin") ?: -5.0, get("uMax") ?: 5.0, get("vMin") ?: -5.0, get("vMax") ?: 5.0) },
            dirty = dirty, canUndo = undo.isNotEmpty(), canRedo = redo.isNotEmpty(), statusMessage = null, sliderSettings = doc.sliderSettings, tableFits = emptyMap(), documentRevision = _state.value.documentRevision + 1, tableFitting = emptySet())
        updateSliders()
    }

    private fun updateSliders() {
        val previous = _state.value.sliders.associateBy { it.name }
        val values = mutableMapOf<String, Double>()
        val sliders = _state.value.rows.mapNotNull { row ->
            val assignment = runCatching { MathSyntax.definition(row.text) }.getOrNull() ?: return@mapNotNull null
            if (assignment.parameters != null) return@mapNotNull null
            val name = assignment.name
            if (name in setOf("x", "y", "z", "r", "theta", "e", "i", "pi")) return@mapNotNull null
            val value = runCatching { NumericCompiler.compile(assignment.body,
                symbols = MathSyntax.Symbols(variables = values.keys + name)).evaluate(values) }.getOrNull()
                ?.takeIf { it.isFinite() } ?: return@mapNotNull null
            values[name] = value
            val old = previous[name]
            val config = _state.value.sliderSettings[name]
            SliderUi(name, value, min = config?.min ?: old?.min ?: -max(10.0, abs(value) * 2), max = config?.max ?: old?.max ?: max(10.0, abs(value) * 2),
                step = config?.step ?: 0.0, playing = name in animations, animation = config?.animation ?: SliderAnimation.Oscillate, speed = config?.speed ?: 1.0)
        }.toMutableList()
        if (_state.value.rows.any { it.result.contains(integrationConstantRegex) } && sliders.none { it.name == "C" }) {
            sliders.add(SliderUi("C", integrationConstant, playing = "C" in animations))
        }
        _state.value = _state.value.copy(sliders = sliders.distinctBy { it.name })
    }

    private fun setSlider(name: String, value: Double) {
        if (name == "C" && _state.value.rows.none { Regex("^C\\s*=").containsMatchIn(it.text) }) {
            integrationConstant = value
            _state.value = _state.value.copy(dirty = true, sliders = _state.value.sliders.map { if (it.name == name) it.copy(value = value) else it })
            recoveryJob?.cancel()
            val snapshot = document()
            recoveryJob = viewModelScope.launch { delay(450); withContext(Dispatchers.IO) { repository.saveRecovery(snapshot) } }
            return
        }
        val row = _state.value.rows.find { runCatching { MathSyntax.definition(it.text)?.let { definition ->
            definition.name == name && definition.parameters == null
        } }.getOrNull() == true } ?: return
        onAction(CalculatorAction.EditRow(row.id, "$name=${number(value)}"))
    }

    private fun toggleAnimation(name: String) {
        animations.remove(name)?.let { it.cancel(); updateSliders(); return }
        animations[name] = viewModelScope.launch {
            var direction = if (_state.value.sliders.find { it.name == name }?.animation == SliderAnimation.Reverse) -1 else 1
            while (true) {
                val slider = _state.value.sliders.find { it.name == name } ?: break
                val increment = if (slider.step > 0) slider.step else (slider.max - slider.min) / 200
                var value = slider.value + increment * direction * (if (slider.step > 0) 1.0 else slider.speed)
                if (value > slider.max || value < slider.min) {
                    when (slider.animation) {
                        SliderAnimation.Oscillate -> { direction *= -1; value = value.coerceIn(slider.min, slider.max) }
                        SliderAnimation.Forward -> value = slider.min
                        SliderAnimation.Reverse -> value = slider.max
                        SliderAnimation.Once -> { setSlider(name, value.coerceIn(slider.min, slider.max)); animations.remove(name); updateSliders(); break }
                    }
                }
                if (slider.step > 0) value = (slider.min + kotlin.math.round((value - slider.min) / slider.step) * slider.step).coerceIn(slider.min, slider.max)
                setSlider(name, value)
                delay(if (slider.step > 0) (60 / slider.speed).toLong().coerceAtLeast(16) else 60)
            }
        }
        updateSliders()
    }

    private fun stopAnimations() { animations.values.forEach { it.cancel() }; animations.clear(); integrationConstant = 0.0 }

    override fun onCleared() {
        stopAnimations(); engine.close(); super.onCleared()
    }

    companion object {
        private val palette = intArrayOf(0xFFC74440.toInt(), 0xFF2D70B3.toInt(), 0xFF6042A6.toInt(), 0xFF388C46.toInt(), 0xFFFA7E19.toInt(), 0xFF000000.toInt())
        private val symbolicPrefixRegex = Regex("^(integrate|int|integral|diff|derive|simplify|factor|expand|normal|partfrac)\\s*\\(")
        private val integralTrailingCRegex = Regex("\\s*\\+\\s*C$")
        private val integrationConstantRegex = Regex("\\bC\\b")
        private val whitespaceRegex = Regex("\\s+")
        private val safeFileNameRegex = Regex("[^\\p{L}\\p{N} _.-]")
        private fun newRow(id: Long) = ExpressionRow(id, "", color = palette[((id - 1) % palette.size).toInt()])
        private fun compact(text: String) = text.replace(whitespaceRegex, "").replace("*", "").replace("(", "").replace(")", "")
        private fun number(value: Double) = BigDecimal.valueOf(value).setScale(5, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        private fun safeFileName(value: String) = value.replace(safeFileNameRegex, "_").take(80).ifBlank { "graph" }
    }
}
