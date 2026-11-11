@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.demos.graphing.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import dev.demos.graphing.plot.PlotExpression
import dev.demos.graphing.plot.PlotProgram
import dev.demos.graphing.plot.PlotSettings

private val Blue = DemosColors.Blue
private val Header = DemosColors.Header
private val Muted = DemosColors.Muted
private val Border = DemosColors.Border
private val MathFont = FontFamily.Serif

// Focusing an equation must never reposition the user's worksheet.
private val ManualScrollSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
}

/** Native, offline calculator shell. The graph and document state remain owned by the app. */
@Composable
fun CalculatorScreen(
    state: CalculatorUiState,
    onAction: (CalculatorAction) -> Unit,
    showGraphControls: Boolean = true,
    onOpenLibrary: () -> Unit = {},
    graph: @Composable (is3D: Boolean, modifier: Modifier) -> Unit,
) {
    val keyboardVisible = state.keyboardVisible
    fun setKeyboardVisible(visible: Boolean) { onAction(CalculatorAction.SetKeyboardVisible(visible)) }
    var keyboardTab by rememberSaveable { mutableStateOf("123") }
    val editorValues = remember { mutableStateMapOf<Long, TextFieldValue>() }
    var textInputFocused by remember { mutableStateOf(false) }
    var colorEditor by remember { mutableStateOf<ExpressionRow?>(null) }
    var showSave by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var graphSettings by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var editingList by remember { mutableStateOf(false) }
    var appendRequested by remember { mutableStateOf(false) }
    var tableEditing by remember { mutableStateOf<TableCellSelection?>(null) }
    var tableValue by remember { mutableStateOf(TextFieldValue()) }
    val configuration = LocalAppConfiguration.current
    val density = LocalDensity.current
    var measuredKeypadHeight by remember(configuration.screenWidthDp, configuration.screenHeightDp) { mutableStateOf(0.dp) }
    var reservedKeypadHeight by remember(configuration.screenWidthDp, configuration.screenHeightDp) { mutableStateOf(0.dp) }
    var resizedGraphHeightDp by rememberSaveable { mutableStateOf<Float?>(null) }
    var tableExportId by remember { mutableStateOf<Long?>(null) }
    var showGraphExport by remember { mutableStateOf(false) }
    val graphState = rememberSaveableStateHolder()
    val platform = rememberUiActions()
    val focusRequester = remember { FocusRequester() }
    val messages = remember { SnackbarHostState() }
    val currentRow = state.rows.firstOrNull { it.id == state.activeId }
    val selectedTable = remember(currentRow?.text) { currentRow?.let { parseTableData(it.text) } }
    val latestState by rememberUpdatedState(state)
    val plotSources = state.rows.map { PlotExpression(it.id, it.text, it.color) }
    val plotParameters = state.sliders.associate { it.name to it.value }
    val graphableIds = remember(plotSources, plotParameters) {
        PlotProgram.compile(plotSources, PlotSettings(parameters = plotParameters)).relations.map { it.id }.toSet()
    }

    LaunchedEffect(state.rows.map { it.id }) {
        editorValues.keys.retainAll(state.rows.map { it.id }.toSet())
        if (state.rows.none { it.id == tableEditing?.id }) tableEditing = null
    }
    LaunchedEffect(state.activeId, currentRow?.text?.startsWith("table("), state.documentRevision) {
        val table = currentRow?.let { parseTableData(it.text) }
        if (table != null) {
            val previous = tableEditing?.takeIf { it.id == state.activeId } ?: TableCellSelection(state.activeId, 0, 0)
            val cell = previous.copy(row = previous.row.coerceIn(-1, table.size), column = previous.column.coerceIn(0, table.columnCount - 1))
            tableEditing = cell
            val number = state.rows.takeWhile { it.id != state.activeId }.count { parseTableData(it.text) != null } + 1
            val source = if (cell.row < 0) table.header(cell.column.coerceAtMost(table.columnCount - 1), number) else table.column(cell.column.coerceAtMost(table.columnCount - 1)).getOrElse(cell.row) { "" }
            tableValue = TextFieldValue(source, TextRange(source.length))
        } else tableEditing = null
    }
    LaunchedEffect(state.activeId, selectedTable?.size, selectedTable?.columnCount) {
        val table = selectedTable ?: return@LaunchedEffect
        val cell = tableEditing?.takeIf { it.id == state.activeId } ?: return@LaunchedEffect
        if (cell.row > table.size || cell.column >= table.columnCount) {
            val next = cell.copy(row = cell.row.coerceIn(-1, table.size), column = cell.column.coerceIn(0, table.columnCount - 1))
            val number = state.rows.takeWhile { it.id != state.activeId }.count { parseTableData(it.text) != null } + 1
            val source = if (next.row < 0) table.header(next.column, number) else table.column(next.column).getOrElse(next.row) { "" }
            tableEditing = next
            tableValue = TextFieldValue(source, TextRange(source.length))
        }
    }
    LaunchedEffect(state.statusMessage) { state.statusMessage?.let { messages.showSnackbar(it) } }
    PlatformBackHandler(enabled = !keyboardVisible && state.layout != CalculatorLayout.Split) { onAction(CalculatorAction.SetLayout(CalculatorLayout.Split)) }
    PlatformBackHandler(enabled = keyboardVisible && !showMenu && !showSave && !showLibrary && !showSettings && !showHelp) { setKeyboardVisible(false) }

    fun editorValue(row: ExpressionRow): TextFieldValue = editorValues[row.id]
        ?.takeIf { it.text == row.text } ?: TextFieldValue(row.text, TextRange(row.text.length))

    fun updateInput(row: ExpressionRow, value: TextFieldValue, complete: Boolean = true) {
        if (latestState.documentRevision != state.documentRevision) return
        val current = latestState.rows.firstOrNull { it.id == row.id } ?: return
        if (parseTableData(current.text) != null || parseNoteText(current.text) != null || parseFolderData(current.text) != null || parseImageData(current.text) != null || parseInferenceData(current.text) != null) return
        if (value.text == row.text && current.text != row.text) return
        val next = if (complete && value.text != current.text) {
            val symbols = dev.demos.graphing.math.MathSyntax.symbols(latestState.rows.map { it.text })
            MathInputRules.apply(editorValue(current), value, symbols.variables + symbols.functions)
        } else value
        editorValues[row.id] = next
        onAction(CalculatorAction.SelectRow(row.id))
        onAction(CalculatorAction.EditRow(row.id, next.text))
    }

    fun selectTableCell(cell: TableCellSelection) {
        val snapshot = latestState
        val table = snapshot.rows.firstOrNull { it.id == cell.id }?.let { parseTableData(it.text) } ?: return
        if (cell.column !in 0 until table.columnCount || cell.row !in -1..table.size) return
        if (tableEditing != cell) {
            val number = snapshot.rows.takeWhile { it.id != cell.id }.count { parseTableData(it.text) != null } + 1
            val source = if (cell.row < 0) table.header(cell.column, number) else table.column(cell.column).getOrElse(cell.row) { "" }
            tableValue = TextFieldValue(source, TextRange(source.length))
            tableEditing = cell
        }
        onAction(CalculatorAction.SelectRow(cell.id))
        if (platform.showKeypadOnInput) setKeyboardVisible(true)
    }

    fun updateTableInput(cell: TableCellSelection, value: TextFieldValue) {
        // A departing editor can deliver a final selection event after the next cell is focused.
        // Bind every change to its originating cell and never serialize a captured whole table.
        if (tableEditing != cell || latestState.documentRevision != state.documentRevision) return
        val previous = tableValue
        tableValue = value
        if (previous.text == value.text) return
        if (cell.row < 0) onAction(CalculatorAction.EditTableHeader(cell.id, cell.column, value.text))
        else onAction(CalculatorAction.EditTableCell(cell.id, cell.row, cell.column, value.text))
    }

    fun edit(insert: String, retreat: Int = 0) {
        val row = state.rows.firstOrNull { it.id == state.activeId } ?: return
        val cell = tableEditing?.takeIf { it.id == row.id } ?: parseTableData(row.text)?.let {
            TableCellSelection(row.id, 0, 0).also(::selectTableCell)
        }
        if (cell != null) {
            val start = tableValue.selection.min.coerceIn(0, tableValue.text.length)
            val end = tableValue.selection.max.coerceIn(start, tableValue.text.length)
            val next = tableValue.text.replaceRange(start, end, insert)
            updateTableInput(cell, TextFieldValue(next, TextRange((start + insert.length - retreat).coerceIn(0, next.length))))
            return
        }
        val value = editorValue(row)
        val start = value.selection.min.coerceIn(0, row.text.length)
        val end = value.selection.max.coerceIn(start, row.text.length)
        val next = row.text.replaceRange(start, end, insert)
        updateInput(row, TextFieldValue(next, TextRange((start + insert.length - retreat).coerceIn(0, next.length))),
            complete = retreat == 0 && insert.length == 1)
    }
    fun erase() {
        val row = state.rows.firstOrNull { it.id == state.activeId } ?: return
        val cell = tableEditing?.takeIf { it.id == row.id }
        if (cell == null && parseTableData(row.text) != null) { selectTableCell(TableCellSelection(row.id, 0, 0)); return }
        if (cell != null) {
            val next = MathInputRules.backspace(tableValue)
            updateTableInput(cell, next)
            return
        }
        val value = editorValue(row)
        updateInput(row, MathInputRules.backspace(value), complete = false)
    }
    fun moveCursor(delta: Int) {
        tableEditing?.takeIf { it.id == state.activeId }?.let { cell ->
            updateTableInput(cell, MathInputRules.move(tableValue, delta))
            return
        }
        val row = state.rows.firstOrNull { it.id == state.activeId } ?: return
        val value = editorValue(row)
        updateInput(row, MathInputRules.move(value, delta), complete = false)
    }
    fun moveTableCell(rowDelta: Int, columnDelta: Int = 0): Boolean {
        val cell = tableEditing?.takeIf { it.id == state.activeId } ?: return false
        val table = currentRow?.let { parseTableData(it.text) } ?: return false
        var column = cell.column + columnDelta
        var index = cell.row + rowDelta
        if (column >= table.columnCount) { column = 0; index++ }
        if (column < 0) { column = table.columnCount - 1; index-- }
        index = index.coerceIn(-1, table.size)
        val number = state.rows.takeWhile { it.id != state.activeId }.count { parseTableData(it.text) != null } + 1
        val source = if (index < 0) table.header(column, number) else table.column(column).getOrElse(index) { "" }
        tableEditing = cell.copy(row = index, column = column)
        tableValue = TextFieldValue(source, TextRange(source.length))
        return true
    }
    fun addRow() {
        val cell = tableEditing?.takeIf { it.id == state.activeId }
        val table = currentRow?.let { parseTableData(it.text) }
        if (cell != null && table != null) {
            val nextRow = if (cell.row < 0) 0 else (cell.row + 1).coerceAtMost(table.size)
            tableEditing = cell.copy(row = nextRow)
            val source = table.column(cell.column).getOrElse(nextRow) { "" }
            tableValue = TextFieldValue(source, TextRange(source.length))
            return
        }
        tableEditing = null
        onAction(CalculatorAction.AddRow)
    }
    fun key(label: String) {
        when (label) {
            "ABC", "123" -> keyboardTab = label
            "←" -> moveCursor(-1)
            "→" -> moveCursor(1)
            "space" -> edit(" ")
            "⌫" -> erase()
            "↵" -> addRow()
            "a²" -> edit("^{2}")
            "aᵇ" -> edit("^{}", 1)
            "√" -> edit("sqrt()", 1)
            "|a|" -> edit("abs()", 1)
            "π" -> {
                val row = state.rows.firstOrNull { it.id == state.activeId } ?: return
                val cell = tableEditing?.takeIf { it.id == row.id }
                if (cell != null) updateTableInput(cell, MathInputRules.insertConstant(tableValue, "π"))
                else updateInput(row, MathInputRules.insertConstant(editorValue(row), "π"), complete = false)
            }
            "×" -> edit("*")
            "÷" -> edit("/")
            "−" -> edit("-")
            "≤" -> edit("<=")
            "≥" -> edit(">=")
            "paste" -> {
                platform.readClipboard()?.let { edit(it) }
            }
            "speak" -> currentRow?.let { row ->
                val utterance = row.text + if (row.result.isNotBlank()) " equals ${row.result}" else ""
                platform.speak(utterance)
            }
            else -> edit(label)
        }
    }

    Column(
        Modifier.fillMaxSize().imePadding().background(DemosColors.Paper)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (state.layout == CalculatorLayout.Graph || event.type != KeyEventType.KeyDown || showSave || showSettings || showLibrary || showHelp || parseNoteText(currentRow?.text.orEmpty()) != null || parseFolderData(currentRow?.text.orEmpty()) != null || parseImageData(currentRow?.text.orEmpty()) != null || parseInferenceData(currentRow?.text.orEmpty()) != null) false
                else when {
                    event.isCtrlPressed && event.key == Key.Z -> { onAction(if (event.isShiftPressed) CalculatorAction.Redo else CalculatorAction.Undo); true }
                    event.isCtrlPressed && event.key == Key.S -> { showSave = true; true }
                    event.isCtrlPressed && event.key == Key.V && !textInputFocused -> { key("paste"); true }
                    tableEditing?.id == state.activeId && event.key == Key.DirectionUp -> moveTableCell(-1)
                    tableEditing?.id == state.activeId && event.key == Key.DirectionDown -> moveTableCell(1)
                    tableEditing?.id == state.activeId && event.key == Key.Tab -> moveTableCell(0, if (event.isShiftPressed) -1 else 1)
                    textInputFocused && event.key != Key.Enter && event.key != Key.NumPadEnter -> false
                    event.key == Key.Backspace -> { erase(); true }
                    event.key == Key.DirectionLeft -> { key("←"); true }
                    event.key == Key.DirectionRight -> { key("→"); true }
                    event.key == Key.Enter || event.key == Key.NumPadEnter -> { key("↵"); true }
                    !event.isCtrlPressed && !event.isAltPressed && event.utf16CodePoint >= 32 -> {
                        edit(String(Character.toChars(event.utf16CodePoint))); true
                    }
                    else -> false
                }
            }.focusable()
    ) {
        if (state.layout == CalculatorLayout.Split) CalculatorHeader(state, { onOpenLibrary(); showLibrary = true }, { showSave = true }, {
            onAction(CalculatorAction.Toggle3D)
        }, { showMenu = true }, showMenu, { showMenu = false }) {
            DropdownMenuItem(text = { Text("New graph") }, onClick = { onAction(CalculatorAction.New); showMenu = false })
            DropdownMenuItem(text = { Text("Open saved graph") }, onClick = { onOpenLibrary(); showLibrary = true; showMenu = false })
            DropdownMenuItem(text = { Text("Save graph") }, onClick = { showSave = true; showMenu = false })
            DropdownMenuItem(text = { Text("Calculator fullscreen") }, onClick = { onAction(CalculatorAction.SetLayout(CalculatorLayout.Calculator)); showMenu = false })
            DropdownMenuItem(text = { Text("Graph fullscreen") }, onClick = { onAction(CalculatorAction.SetLayout(CalculatorLayout.Graph)); showMenu = false })
            DropdownMenuItem(text = { Text("Settings") }, onClick = { graphSettings = false; showSettings = true; showMenu = false })
            DropdownMenuItem(text = { Text(if (editingList) "Finish editing expression list" else "Edit expression list") }, onClick = { editingList = !editingList; showMenu = false })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Export graph file") }, onClick = { onAction(CalculatorAction.ExportDocument); showMenu = false })
            DropdownMenuItem(text = { Text("Import graph file") }, onClick = { onAction(CalculatorAction.ImportDocument); showMenu = false })
            DropdownMenuItem(text = { Text("Export graph as PNG") }, onClick = { onAction(CalculatorAction.ExportGraph(GraphExportFormat.PNG)); showMenu = false })
            DropdownMenuItem(text = { Text("Export graph as PDF") }, onClick = { onAction(CalculatorAction.ExportGraph(GraphExportFormat.PDF)); showMenu = false })
            DropdownMenuItem(text = { Text("Share graph image") }, onClick = { onAction(CalculatorAction.ExportImage); showMenu = false })
            DropdownMenuItem(text = { Text("Import table (CSV, TSV, JSON)") }, onClick = { onAction(CalculatorAction.ImportTable); showMenu = false })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Calculator guide") }, onClick = { showHelp = true; showMenu = false })
        }

        BoxWithConstraints(Modifier.weight(1f)) {
            val availableWidth = maxWidth
            val availableHeight = maxHeight
            val fullPanelHeight = availableHeight + (if (keyboardVisible) measuredKeypadHeight else 0.dp)
            // Keep room for the toolbar and an expression, even with the keypad open.
            val maximumGraphHeight = (availableHeight - 104.dp).coerceAtLeast(0.dp)
            val minimumGraphHeight = 100.dp.coerceAtMost(maximumGraphHeight)
            val portraitGraphHeight = (resizedGraphHeightDp?.dp
                ?: ((fullPanelHeight - reservedKeypadHeight) * .5f))
                .coerceIn(minimumGraphHeight, maximumGraphHeight)
            val landscape = availableWidth >= 600.dp && (LocalAppConfiguration.current.orientation == AppConfiguration.ORIENTATION_LANDSCAPE || availableWidth >= 840.dp)
            val splitDragState = rememberDraggableState { delta ->
                // Start from the visible height so resizing remains responsive at either limit.
                // Store the requested height separately so opening the keypad only clamps it
                // temporarily; closing the keypad restores the user's split.
                val currentHeight = (resizedGraphHeightDp?.dp ?: portraitGraphHeight)
                    .coerceIn(minimumGraphHeight, maximumGraphHeight)
                resizedGraphHeightDp = (currentHeight + with(density) { delta.toDp() })
                    .coerceIn(minimumGraphHeight, maximumGraphHeight).value
            }
            val graphPanel: @Composable (Modifier) -> Unit = { modifier ->
                Box(modifier) {
                    graphState.SaveableStateProvider("graph") { graph(state.is3D, Modifier.fillMaxSize()) }
                    if (showGraphControls) {
                        GraphTool(IconKind.Wrench, "Graph settings", Modifier.align(Alignment.TopEnd).padding(5.dp)) { graphSettings = true; showSettings = true }
                        if (state.layout == CalculatorLayout.Graph) GraphTool(IconKind.Export, "Export graph", Modifier.align(Alignment.BottomEnd).padding(6.dp)) { showGraphExport = true }
                    }
                }
            }
            val expressionPanel: @Composable ColumnScope.() -> Unit = {
                ExpressionToolbar(
                    canUndo = state.canUndo, canRedo = state.canRedo, keyboardVisible = keyboardVisible,
                    onAdd = { showAddMenu = !showAddMenu }, onUndo = { onAction(CalculatorAction.Undo) },
                    onRedo = { onAction(CalculatorAction.Redo) },
                    onCollapse = { setKeyboardVisible(!keyboardVisible) },
                    onSettings = { graphSettings = false; showSettings = true },
                    fullscreen = state.layout == CalculatorLayout.Calculator,
                    onFullscreen = {
                        onAction(CalculatorAction.SetLayout(if (state.layout == CalculatorLayout.Calculator) CalculatorLayout.Split else CalculatorLayout.Calculator))
                    },
                    addMenuExpanded = showAddMenu, onAddMenuDismiss = { showAddMenu = false },
                    onInsert = { kind ->
                        tableEditing = null; showAddMenu = false
                        when (kind) {
                            "expression" -> { onAction(CalculatorAction.AddItem("")); if (platform.showKeypadOnInput) setKeyboardVisible(true) }
                            "note" -> { onAction(CalculatorAction.AddItem(encodeNoteText(""))); setKeyboardVisible(false) }
                            "table" -> { onAction(CalculatorAction.AddItem(encodeTableData(TableData(emptyList(), emptyList())))); if (platform.showKeypadOnInput) setKeyboardVisible(true) }
                            "folder" -> { onAction(CalculatorAction.AddItem(encodeFolderData(FolderData("")))); setKeyboardVisible(false) }
                            "image" -> onAction(CalculatorAction.ImportImage)
                            "inference" -> { onAction(CalculatorAction.AddItem(encodeInferenceData(InferenceData()))); setKeyboardVisible(false) }
                        }
                    },
                    modifier = if (state.layout == CalculatorLayout.Split && !landscape) {
                        Modifier.draggable(splitDragState, Orientation.Vertical)
                            .semantics { contentDescription = "Resize graph and expressions" }
                    } else Modifier,
                )
                if (editingList) Row(Modifier.fillMaxWidth().height(40.dp).background(DemosColors.BlueSoft).padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Edit expressions", fontSize = 13.sp, color = Muted, modifier = Modifier.weight(1f))
                    TextButton(onClick = { editingList = false }, shape = MaterialTheme.shapes.small) { Text("Done", style = MaterialTheme.typography.labelLarge) }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    var knownRows by remember { mutableStateOf(emptySet<Long>()) }
                    LaunchedEffect(state.rows.map { it.id }, state.activeId) {
                        val inserted = knownRows.isNotEmpty() && state.activeId !in knownRows
                        knownRows = state.rows.map { it.id }.toSet()
                        if (inserted && appendRequested) {
                            appendRequested = false
                            val index = state.rows.indexOfFirst { it.id == state.activeId }
                            val layout = listState.layoutInfo
                            val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
                            if (index >= 0 && (item == null || item.offset + item.size > layout.viewportEndOffset)) {
                                val height = item?.size ?: with(density) { 72.dp.roundToPx() }
                                listState.animateScrollToItem(index, -(layout.viewportEndOffset - height).coerceAtLeast(0))
                            }
                        }
                    }
                    CompositionLocalProvider(LocalBringIntoViewSpec provides ManualScrollSpec) {
                        LazyColumn(Modifier.fillMaxSize(), state = listState) {
                            itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                                val table = remember(row.text) { parseTableData(row.text) }
                                val note = remember(row.text) { parseNoteText(row.text) }
                                val folder = remember(row.text) { parseFolderData(row.text) }
                                val enclosingFolder = state.rows.firstOrNull { it.id == row.folderId }?.let { parseFolderData(it.text) }
                                if (folder == null && enclosingFolder?.collapsed == true) return@itemsIndexed
                                Column {
                                    if (editingList) {
                                        val siblings = state.rows.filter { it.folderId == row.folderId }
                                        val position = siblings.indexOfFirst { it.id == row.id }
                                        val canMoveUp = position > 0
                                        val canMoveDown = position < siblings.lastIndex
                                        Row(Modifier.fillMaxWidth().background(DemosColors.Surface).padding(start = 42.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("Move ↑", color = if (canMoveUp) Blue else DemosColors.Disabled, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable(enabled = canMoveUp) { onAction(CalculatorAction.MoveRow(row.id, -1)) }.padding(vertical = 10.dp))
                                            Text("Move ↓", color = if (canMoveDown) Blue else DemosColors.Disabled, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable(enabled = canMoveDown) { onAction(CalculatorAction.MoveRow(row.id, 1)) }.padding(vertical = 10.dp))
                                            Spacer(Modifier.weight(1f))
                                            if (table != null || folder != null || row.id in graphableIds || parseImageData(row.text) != null) {
                                                Text(if (row.hidden) "Show" else "Hide", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable { onAction(CalculatorAction.ToggleVisibility(row.id)) }.padding(vertical = 10.dp))
                                            }
                                        }
                                    }
                                    when {
                                        folder != null -> FolderExpressionItem(index + 1, row.id == state.activeId, folder,
                                            onSelect = { onAction(CalculatorAction.SelectRow(row.id)); setKeyboardVisible(false) },
                                            onDelete = { onAction(CalculatorAction.DeleteRow(row.id)) },
                                            onChange = { onAction(CalculatorAction.EditRow(row.id, encodeFolderData(it))) })
                                        table != null -> TableWorksheet(row, index + 1, state.rows.take(index + 1).count { parseTableData(it.text) != null }, row.id == state.activeId, table,
                                            editing = tableEditing, value = tableValue, fit = state.tableFits[row.id],
                                            fitting = row.id in state.tableFitting, parameters = plotParameters, radians = !state.degreeMode,
                                            onCell = { cellRow, column -> selectTableCell(TableCellSelection(row.id, cellRow, column)) },
                                            onInput = ::updateTableInput,
                                            onFocus = { cell, focused -> if (focused || tableEditing == cell) textInputFocused = focused },
                                            onDeleteRow = { index ->
                                                if (tableEditing?.id == row.id) tableEditing = null
                                                onAction(CalculatorAction.DeleteTableRow(row.id, index))
                                            },
                                            onSort = { tableEditing = null; onAction(CalculatorAction.SortTable(row.id)) },
                                            onSelect = { onAction(CalculatorAction.SelectRow(row.id)) },
                                            onDelete = { onAction(CalculatorAction.DeleteRow(row.id)) },
                                            onChange = { next -> onAction(CalculatorAction.SetTableOptions(row.id, next)) },
                                            onVisibility = { onAction(CalculatorAction.ToggleVisibility(row.id)) }, onColor = { colorEditor = row }, onExport = { tableExportId = row.id },
                                            onCopyEquation = { source ->
                                                if (source.startsWith("zoom-table:")) {
                                                    val points = dev.demos.graphing.math.TableEvaluation.pairs(table, state.rows.take(index + 1).count { parseTableData(it.text) != null }, plotParameters, !state.degreeMode)
                                                    if (points.isNotEmpty()) {
                                                        val xMin = points.minOf { it.first }; val xMax = points.maxOf { it.first }; val yMin = points.minOf { it.second }; val yMax = points.maxOf { it.second }
                                                        val px = maxOf(1.0, (xMax - xMin) * .2); val py = maxOf(1.0, (yMax - yMin) * .2)
                                                        onAction(CalculatorAction.SetBounds(state.bounds.copy(xMin = xMin - px, xMax = xMax + px, yMin = yMin - py, yMax = yMax + py)))
                                                    }
                                                } else { tableEditing = null; onAction(CalculatorAction.AddItem(source)); if (platform.showKeypadOnInput) setKeyboardVisible(true) }
                                            })
                                        parseImageData(row.text) != null -> ImageWorksheet(row, index + 1, row.id == state.activeId,
                                            onSelect = { tableEditing = null; onAction(CalculatorAction.SelectRow(row.id)); setKeyboardVisible(false) },
                                            onChange = { onAction(CalculatorAction.EditRow(row.id, encodeImageData(it))) }, onDelete = { onAction(CalculatorAction.DeleteRow(row.id)) },
                                            onVisibility = { onAction(CalculatorAction.ToggleVisibility(row.id)) })
                                        parseInferenceData(row.text) != null -> InferenceWorksheet(row, index + 1, state,
                                            onSelect = { tableEditing = null; onAction(CalculatorAction.SelectRow(row.id)); setKeyboardVisible(false) },
                                            onChange = { onAction(CalculatorAction.EditRow(row.id, encodeInferenceData(it))) }, onDelete = { onAction(CalculatorAction.DeleteRow(row.id)) })
                                        note != null -> NoteExpressionItem(row, index + 1, row.id == state.activeId, note,
                                            onSelect = { onAction(CalculatorAction.SelectRow(row.id)); setKeyboardVisible(false) },
                                            onDelete = { onAction(CalculatorAction.DeleteRow(row.id)) },
                                            onChange = { onAction(CalculatorAction.EditRow(row.id, encodeNoteText(it))) })
                                        else -> ExpressionItem(row, index + 1, row.id == state.activeId, editorValue(row), row.id in graphableIds,
                                            onInput = { value -> updateInput(row, value) },
                                            onInputFocus = { focused ->
                                                textInputFocused = focused
                                                if (focused) onAction(CalculatorAction.SelectRow(row.id))
                                            },
                                            onSelect = { tableEditing = null; onAction(CalculatorAction.SelectRow(row.id)); if (platform.showKeypadOnInput) setKeyboardVisible(true) },
                                            onDelete = { onAction(CalculatorAction.DeleteRow(row.id)) },
                                            onVisibility = { onAction(CalculatorAction.ToggleVisibility(row.id)) }, onColor = { colorEditor = row },
                                            slider = state.sliders.firstOrNull { slider -> runCatching { dev.demos.graphing.math.MathSyntax.definition(row.text)?.name == slider.name }.getOrDefault(false) },
                                            missingVariables = missingSliderVariables(row.text, state), onAction = onAction,
                                            defaultDecimal = state.defaultDecimal,
                                            onSettingsFocus = { focused -> textInputFocused = focused; if (focused) setKeyboardVisible(false) },
                                        )
                                    }
                                    state.sliders.filter { it.name == "C" && row.id == state.rows.firstOrNull { expression -> expression.result.contains(Regex("\\bC\\b")) }?.id }
                                        .forEach { slider -> SliderControl(slider, onAction) }
                                }
                            }
                            item {
                                Row(Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = "Add expression at end" }
                                    .clickable { tableEditing = null; appendRequested = true; onAction(CalculatorAction.AppendRow); if (platform.showKeypadOnInput) setKeyboardVisible(true) }) {
                                    Box(Modifier.width(34.dp).fillMaxHeight().background(DemosColors.Surface)) {
                                        Text("${state.rows.size + 1}", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(start = 3.dp, top = 3.dp))
                                    }
                                }
                            }
                        }
                    }

                }
            }
            when (state.layout) {
                CalculatorLayout.Graph -> graphPanel(Modifier.fillMaxSize())
                CalculatorLayout.Calculator -> Column(Modifier.fillMaxSize(), content = expressionPanel)
                CalculatorLayout.Split -> if (landscape) {
                    Row(Modifier.fillMaxSize()) {
                        Column(Modifier.width((availableWidth * .35f).coerceIn(260.dp, 420.dp)).fillMaxHeight(), content = expressionPanel)
                        graphPanel(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        graphPanel(Modifier.fillMaxWidth().height(portraitGraphHeight))
                        expressionPanel()
                    }
                }
            }
        }

        SnackbarHost(messages)
        if (keyboardVisible && state.layout != CalculatorLayout.Graph) Box(Modifier.onSizeChanged { size ->
            val height = with(density) { size.height.toDp() }
            measuredKeypadHeight = height
            if (reservedKeypadHeight == 0.dp && height > 0.dp) reservedKeypadHeight = height
        }) {
            CalculatorKeypad(keyboardTab, state.layout, state.is3D, state.keyboardHeightScale,
                onHeightChange = { onAction(CalculatorAction.SetKeyboardHeightScale(it)) },
                onKey = ::key, onTemplate = { template, retreat -> edit(template, retreat) })
        }
    }

    tableExportId?.let { id ->
        AlertDialog(onDismissRequest = { tableExportId = null }, containerColor = DemosColors.Paper,
            title = { Text("Export this table") },
            text = { Column {
                TableFileFormat.entries.forEach { format ->
                    TextButton(onClick = { onAction(CalculatorAction.ExportTable(id, format)); tableExportId = null }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Text("${format.name} (.${format.extension})", modifier = Modifier.fillMaxWidth())
                    }
                }
            } }, confirmButton = { TextButton(onClick = { tableExportId = null }, shape = MaterialTheme.shapes.small) { Text("Cancel") } })
    }
    if (showGraphExport) AlertDialog(onDismissRequest = { showGraphExport = false }, containerColor = DemosColors.Paper,
        title = { Text("Export graph") }, text = { Column {
            GraphExportFormat.entries.forEach { format ->
                TextButton(onClick = { onAction(CalculatorAction.ExportGraph(format)); showGraphExport = false }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) { Text(format.name) }
            }
        } }, confirmButton = { TextButton(onClick = { showGraphExport = false }, shape = MaterialTheme.shapes.small) { Text("Cancel") } })

    if (showSave) SaveDialog(state.title, onDismiss = { showSave = false }) { title ->
        onAction(CalculatorAction.Save(title)); showSave = false
    }
    if (showLibrary) LibrarySheet(state.savedGraphs, { showLibrary = false }, onAction)
    if (showSettings) SettingsSheet(state, graphSettings, { showSettings = false }, onAction)
    if (showHelp) HelpSheet { showHelp = false }
    colorEditor?.let { row -> ColorPaletteDialog(row.color, { colorEditor = null }) { color ->
        onAction(CalculatorAction.SetColor(row.id, color)); colorEditor = null
    } }
}

@Composable
private fun CalculatorHeader(state: CalculatorUiState, onOpen: () -> Unit, onSave: () -> Unit, on3D: () -> Unit, onMenu: () -> Unit,
                             menuExpanded: Boolean, onMenuDismiss: () -> Unit, menuContent: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).background(Header).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onOpen, modifier = Modifier.size(40.dp).semantics { contentDescription = "Open graph library" }) {
            CalcIcon(IconKind.Folder, DemosColors.OnHeader, Modifier.size(22.dp))
        }
        Box(Modifier.padding(start = 2.dp, end = 10.dp).width(1.dp).height(20.dp).background(DemosColors.HeaderBorder))
        Text(state.title, Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable(role = Role.Button, onClick = onSave).padding(vertical = 10.dp),
            color = DemosColors.OnHeader, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.padding(start = 6.dp).widthIn(min = 56.dp).heightIn(min = 40.dp).clip(MaterialTheme.shapes.small)
            .background(if (state.dirty) Blue else Color.Transparent).border(1.dp, if (state.dirty) Blue else DemosColors.HeaderBorder, MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onSave).padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text("Save", color = DemosColors.OnHeader, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
        Box(Modifier.padding(start = 8.dp).widthIn(min = 40.dp).heightIn(min = 40.dp).clip(MaterialTheme.shapes.small)
            .border(1.dp, if (state.is3D) Blue else DemosColors.HeaderBorder, MaterialTheme.shapes.small)
            .background(if (state.is3D) Blue else Color.Transparent).clickable(role = Role.Button, onClick = on3D)
            .semantics { contentDescription = "Switch to ${if (state.is3D) "2D" else "3D"} graph" }.padding(horizontal = 8.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(if (state.is3D) "2D" else "3D", color = DemosColors.OnHeader, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        Box {
            IconButton(onClick = onMenu, modifier = Modifier.padding(start = 4.dp).size(40.dp).semantics { contentDescription = "Graph menu" }) {
                CalcIcon(IconKind.More, DemosColors.OnHeader, Modifier.size(22.dp))
            }
            DemosDropdownMenu(expanded = menuExpanded, onDismissRequest = onMenuDismiss, content = menuContent)
        }
    }
}

@Composable
private fun ExpressionToolbar(canUndo: Boolean, canRedo: Boolean, keyboardVisible: Boolean, onAdd: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onCollapse: () -> Unit, fullscreen: Boolean, onFullscreen: () -> Unit,
    addMenuExpanded: Boolean, onAddMenuDismiss: () -> Unit, onInsert: (String) -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(52.dp).background(DemosColors.Surface)
        .border(width = .5.dp, color = Border).padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            ToolIcon(IconKind.Plus, "Add expression", onClick = onAdd)
            DemosDropdownMenu(addMenuExpanded, onAddMenuDismiss, offset = androidx.compose.ui.unit.DpOffset(40.dp, (-40).dp),
                modifier = Modifier.width(180.dp)) {
                listOf("expression", "note", "table", "inference", "folder", "image").forEach { name ->
                    DropdownMenuItem(text = { Text(name.replaceFirstChar { it.uppercaseChar() }, style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { WorksheetItemIcon(name, Modifier.size(24.dp)) },
                        onClick = { onInsert(name) })
                }
            }
        }
        Spacer(Modifier.weight(1f))
        ToolIcon(IconKind.Undo, "Undo", Muted, onUndo, enabled = canUndo)
        ToolIcon(IconKind.Redo, "Redo", Muted, onRedo, enabled = canRedo)
        Spacer(Modifier.weight(1f))
        ToolIcon(IconKind.Keyboard, if (keyboardVisible) "Hide keyboard" else "Show keyboard", if (keyboardVisible) Blue else Muted, onCollapse)
        ToolIcon(IconKind.Settings, "Settings", Muted, onSettings)
        ToolIcon(IconKind.Fullscreen, if (fullscreen) "Exit calculator fullscreen" else "Calculator fullscreen", if (fullscreen) Blue else Muted, onFullscreen)
    }
}

@Composable
private fun ExpressionItem(row: ExpressionRow, number: Int, selected: Boolean, value: TextFieldValue, graphable: Boolean, onInput: (TextFieldValue) -> Unit, onInputFocus: (Boolean) -> Unit, onSelect: () -> Unit, onDelete: () -> Unit, onVisibility: () -> Unit, onColor: () -> Unit,
    slider: SliderUi? = null, missingVariables: List<String> = emptyList(), onAction: (CalculatorAction) -> Unit, defaultDecimal: Boolean = true, onSettingsFocus: (Boolean) -> Unit = {}) {
    // Lazy rows can be restored after the global default changed while off screen.
    var decimal by rememberSaveable(row.id, defaultDecimal, stateSaver = listSaver<Boolean, Boolean>(
        save = { listOf(defaultDecimal, it) },
        restore = { if (it[0] == defaultDecimal) it[1] else defaultDecimal },
    )) { mutableStateOf(defaultDecimal) }
    val platform = rememberUiActions()
    val hasResult = row.result.isNotBlank() || row.approximate.isNotBlank()
    val largeExpression = row.text.contains("integrate(") || row.text.contains("sum(") || row.text.contains("product(")
    val minimumHeight = if (largeExpression) 84.dp else 52.dp
    Column(Modifier.fillMaxWidth().border(if (selected) 1.dp else .5.dp, if (selected) Blue else Border)) {
        Row(Modifier.fillMaxWidth().heightIn(min = minimumHeight).height(IntrinsicSize.Min)) {
            Column(Modifier.width(34.dp).fillMaxHeight().background(if (selected) Blue else DemosColors.Surface), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$number", color = if (selected) Color.White else Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.Start).padding(start = 6.dp, top = 4.dp))
                if (slider != null) {
                    Box(Modifier.padding(top = 4.dp).size(28.dp).clip(RoundedCornerShape(14.dp))
                        .border(1.dp, if (selected) Color.White else Muted, RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button) { onAction(CalculatorAction.ToggleSlider(slider.name)) }
                        .semantics { contentDescription = if (slider.playing) "Pause slider ${slider.name}" else "Play slider ${slider.name}" }, contentAlignment = Alignment.Center) {
                        PlaybackIcon(slider.playing, if (selected) Color.White else Muted)
                    }
                    SliderAnimationMenu(slider, onAction, selected)
                } else if (row.error != null && missingVariables.isEmpty()) {
                    Canvas(Modifier.padding(top = 4.dp).size(23.dp)) {
                        val p = Path().apply { moveTo(size.width / 2, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                        drawPath(p, DemosColors.Error)
                        drawLine(Color.White, Offset(size.width / 2, size.height * .30f), Offset(size.width / 2, size.height * .64f), 2.dp.toPx())
                        drawCircle(Color.White, 1.dp.toPx(), Offset(size.width / 2, size.height * .82f))
                    }
                } else if (graphable) {
                    Canvas(Modifier.padding(top = 5.dp).size(27.dp).combinedClickable(onClick = onVisibility, onLongClick = onColor)
                        .semantics { contentDescription = if (row.hidden) "Show expression $number on graph; hold to change color" else "Hide expression $number on graph; hold to change color" }) {
                        drawCircle(if (row.hidden) Color.White else Color(row.color))
                        if (row.hidden) drawCircle(Color(row.color), style = Stroke(1.5.dp.toPx()))
                        val wave = Path().apply {
                            moveTo(0f, size.height * .65f)
                            cubicTo(size.width * .28f, -size.height * .15f, size.width * .28f, size.height * 1.15f, size.width * .54f, size.height * .55f)
                            cubicTo(size.width * .76f, 0f, size.width * .76f, size.height * 1.0f, size.width, size.height * .3f)
                        }
                        drawPath(wave, if (row.hidden) Color(row.color).copy(alpha = .35f) else Color.White, style = Stroke(2.5.dp.toPx()))
                    }
                }
            }
            Column(Modifier.weight(1f).padding(start = 16.dp, top = 10.dp, bottom = if (hasResult || row.error != null) 4.dp else 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MathEditor(value, selected, number, Modifier.weight(1f), onInput, onInputFocus, onSelect, fontSize = 24, minimumHeight = 36.dp, placeholder = "")
                    DismissControl("Delete expression $number", onClick = onDelete)
                }
                if (row.isEvaluating) {
                    Row(Modifier.padding(top = 8.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Muted)
                        Text("  Calculating…", color = Muted, fontSize = 11.sp)
                    }
                } else if (missingVariables.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (missingVariables.size > 1) "add sliders:" else "add slider:", fontSize = 11.sp, color = Muted)
                        missingVariables.forEach { name ->
                            Text(name, color = Blue, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clip(MaterialTheme.shapes.extraSmall).background(DemosColors.BlueSoft)
                                .clickable { onAction(CalculatorAction.CreateSliders(listOf(name))) }.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        if (missingVariables.size > 1) Text("all", color = Blue, fontSize = 11.sp, modifier = Modifier.clickable { onAction(CalculatorAction.CreateSliders(missingVariables)) })
                    }
                } else if (row.error != null) {
                    Text(row.error, color = DemosColors.Error, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(end = 12.dp, top = 4.dp, bottom = 5.dp))
                } else if (hasResult) {
                    val showingDecimal = row.result.isBlank() || decimal && row.approximate.isNotBlank()
                    val result = if (showingDecimal) row.approximate else row.result
                    Row(Modifier.fillMaxWidth().padding(top = 5.dp, end = 5.dp, bottom = 1.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        if (row.result.isNotBlank() && row.approximate.isNotBlank() && row.approximate != row.result) {
                            Text(if (showingDecimal) "≈" else "=", color = Muted, style = mathTextStyle(18.sp), modifier = Modifier.padding(end = 6.dp).clickable { decimal = !decimal })
                        } else Text(if (showingDecimal) "≈" else "=", color = Muted, style = mathTextStyle(18.sp), modifier = Modifier.padding(end = 6.dp))
                        Box(Modifier.weight(1f, fill = false).widthIn(max = 250.dp).clip(MaterialTheme.shapes.small).background(DemosColors.Surface).border(1.dp, Border, MaterialTheme.shapes.small)
                            .semantics { contentDescription = "Result $number" }
                            .combinedClickable(onClick = { decimal = !decimal }, onLongClick = {
                                platform.copy(result)
                            }).padding(horizontal = 8.dp, vertical = 4.dp).horizontalScroll(rememberScrollState())) {
                            MathExpression(formatResultNumber(result), 19)
                        }
                    }
                }
                if (slider != null) InlineSlider(slider, selected, onAction, onSettingsFocus)
            }
        }
    }
}

@Composable
private fun SliderControl(slider: SliderUi, onAction: (CalculatorAction) -> Unit) {
    if (slider.name == "C") Text("C = ${formatNumber(slider.value)}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 50.dp, top = 3.dp))
    Row(Modifier.fillMaxWidth().padding(start = 41.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onAction(CalculatorAction.ToggleSlider(slider.name)) }, modifier = Modifier.size(32.dp)
            .semantics { contentDescription = if (slider.playing) "Pause slider ${slider.name}" else "Play slider ${slider.name}" }) {
            PlaybackIcon(slider.playing, Muted)
        }
        Text(formatNumber(slider.min), style = MaterialTheme.typography.labelSmall, color = Muted)
        Slider(value = slider.value.toFloat().coerceIn(slider.min.toFloat(), slider.max.toFloat()),
            onValueChange = { value ->
                val snapped = if (slider.step > 0) (value / slider.step).roundToInt() * slider.step else value.toDouble()
                onAction(CalculatorAction.SetSlider(slider.name, snapped))
            }, valueRange = slider.min.toFloat()..slider.max.toFloat(),
            modifier = Modifier.weight(1f).height(29.dp), colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue))
        Text(formatNumber(slider.max), style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

@Composable
private fun NoteExpressionItem(row: ExpressionRow, number: Int, selected: Boolean, note: String, onSelect: () -> Unit, onDelete: () -> Unit, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).border(if (selected) 1.dp else .5.dp, if (selected) Blue else Border)) {
        Box(Modifier.width(34.dp).fillMaxHeight().background(if (selected) Blue else DemosColors.Surface).clickable(onClick = onSelect)) {
            Text("$number", color = if (selected) Color.White else Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
            WorksheetItemIcon("note", Modifier.align(Alignment.TopCenter).padding(top = 22.dp).size(22.dp), if (selected) Color.White else Muted)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 5.dp, top = 6.dp, bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Note", color = Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                DismissControl("Delete note $number", onClick = onDelete)
            }
            BasicTextField(value = note, onValueChange = onChange,
                textStyle = interfaceTextStyle(15.sp).copy(color = DemosColors.Ink),
                modifier = Modifier.fillMaxWidth().heightIn(min = 45.dp).padding(end = 12.dp).onFocusChanged { if (it.isFocused) onSelect() },
                decorationBox = { inner ->
                    Box { if (note.isEmpty()) Text("Add a note…", color = DemosColors.Subtle, style = interfaceTextStyle(15.sp)); inner() }
                })
        }
    }
}

@Composable
private fun FolderExpressionItem(number: Int, selected: Boolean, folder: FolderData, onSelect: () -> Unit, onDelete: () -> Unit, onChange: (FolderData) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).height(IntrinsicSize.Min).background(DemosColors.Surface).border(if (selected) 1.dp else .5.dp, if (selected) Blue else Border), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(34.dp).fillMaxHeight().background(if (selected) Blue else DemosColors.KeySecondary).clickable(onClick = onSelect)) {
            Text("$number", color = if (selected) Color.White else Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
            CalcIcon(IconKind.Folder, if (selected) Color.White else Muted, Modifier.size(23.dp).align(Alignment.Center).padding(top = 6.dp))
        }
        Box(Modifier.width(30.dp).fillMaxHeight().clickable { onChange(folder.copy(collapsed = !folder.collapsed)) }
            .semantics { contentDescription = if (folder.collapsed) "Expand folder ${folder.title}" else "Collapse folder ${folder.title}" }, contentAlignment = Alignment.Center) {
            ChevronIcon(expanded = !folder.collapsed, color = Muted)
        }
        BasicTextField(value = folder.title, onValueChange = { onChange(folder.copy(title = it)) }, singleLine = true,
            textStyle = interfaceTextStyle(16.sp, FontWeight.Medium).copy(color = DemosColors.Ink),
            modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) onSelect() },
            decorationBox = { inner -> Box { if (folder.title.isEmpty()) Text("Folder", color = DemosColors.Subtle, style = interfaceTextStyle(16.sp, FontWeight.Medium)); inner() } })
        DismissControl("Delete folder $number", onClick = onDelete)
    }
}

@Composable
internal fun ColorPaletteDialog(selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val colors = listOf(0xFFC74440, 0xFF2D70B3, 0xFF388C46, 0xFF6042A6, 0xFFFA7E19, 0xFF000000, 0xFFE8B735, 0xFFFA8EC2, 0xFF0099AA, 0xFF888888, 0xFF8C564B, 0xFF6B81C7)
    val names = listOf("Red", "Blue", "Green", "Purple", "Orange", "Black", "Yellow", "Pink", "Teal", "Gray", "Brown", "Periwinkle")
    AlertDialog(onDismissRequest = onDismiss, containerColor = DemosColors.Paper, title = { Text("Expression color") }, text = {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            colors.withIndex().chunked(6).forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    line.forEach { (index, packed) ->
                        val color = packed.toInt()
                        val circle = androidx.compose.foundation.shape.CircleShape
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(circle)
                            .selectable(selected = color == selected, role = Role.RadioButton, onClick = { onPick(color) })
                            .semantics { contentDescription = names[index] }
                            .border(if (color == selected) 2.dp else 0.dp, Blue, circle).padding(4.dp)) {
                            Box(Modifier.fillMaxSize().clip(circle).background(Color(color)), contentAlignment = Alignment.Center) {
                                if (color == selected) CheckmarkIcon(color = if (Color(color).luminance() > .45f) DemosColors.Ink else Color.White)
                            }
                        }
                    }
                }
            }
            Text("Tap the expression icon to show or hide its graph.", color = Muted, fontSize = 12.sp)
        }
    }, confirmButton = { TextButton(onClick = onDismiss, shape = MaterialTheme.shapes.small) { Text("Done") } })
}

private fun formatNumber(n: Double) = if (n == n.toLong().toDouble()) n.toLong().toString() else "%.3g".format(n)
private fun subscript(number: Int) = number.toString().map { "₀₁₂₃₄₅₆₇₈₉"[it.digitToInt()] }.joinToString("")

@Composable
private fun KeyboardHeightControl(scale: Float, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Text("Keyboard height", style = MaterialTheme.typography.bodySmall, color = Muted)
        Slider(value = scale.coerceIn(.6f, 1.4f), onValueChange = onChange, valueRange = .6f..1.4f, steps = 15,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                .semantics { contentDescription = "Keyboard height" },
            colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue))
        Text("${(scale * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = Muted, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 40.dp))
    }
}

@Composable
private fun CalculatorKeypad(tab: String, layout: CalculatorLayout, is3D: Boolean, heightScale: Float,
    onHeightChange: (Float) -> Unit, onKey: (String) -> Unit, onTemplate: (String, Int) -> Unit) {
    val calculator = layout == CalculatorLayout.Calculator
    val first = if (calculator) "aᵇ" else "x"
    val second = if (calculator) "√" else "y"
    val landscape = LocalAppConfiguration.current.orientation == AppConfiguration.ORIENTATION_LANDSCAPE
    val tablet = LocalAppConfiguration.current.smallestScreenWidthDp >= 600
    // Reserve space for the worksheet on short screens, including landscape phones.
    val keyRowsHeight = if (tablet) 176f else if (tab == "ABC") (if (landscape) 186f else 210f) else if (landscape) 210f else 264f
    val spacingHeight = if (tablet) 22f else if (tab == "ABC" || landscape) 30f else 34f
    val maximumScale = ((LocalAppConfiguration.current.screenHeightDp - 160f - 20f - spacingHeight) / keyRowsHeight).coerceIn(.6f, 1.4f)
    val keyScale = heightScale.coerceIn(.6f, 1.4f).coerceAtMost(maximumScale)
    val density = LocalDensity.current
    var dragScale by remember { mutableFloatStateOf(keyScale) }
    val resizeState = rememberDraggableState { delta ->
        dragScale = (dragScale - with(density) { delta.toDp().value } / keyRowsHeight).coerceIn(.6f, maximumScale)
        onHeightChange(dragScale)
    }
    var keypadTop by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth().onGloballyPositioned { keypadTop = it.positionInWindow().y.roundToInt() }
        .background(DemosColors.Keypad).border(.5.dp, Border).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(20.dp)
            .draggable(resizeState, Orientation.Vertical, onDragStarted = { dragScale = keyScale })
            .semantics {
                contentDescription = "Resize keyboard"
                stateDescription = "${(keyScale * 100).roundToInt()}%"
                progressBarRangeInfo = ProgressBarRangeInfo(keyScale, .6f..maximumScale)
                setProgress { value ->
                    if (value.isFinite()) {
                        onHeightChange(value.coerceIn(.6f, maximumScale))
                        true
                    } else false
                }
            }, contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(DemosColors.Disabled))
        }
        if (!tablet) {
            Row(Modifier.fillMaxWidth().height(34.dp * keyScale), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val shortcuts = if (calculator) listOf("a²", "π", "!", "←", "→") else listOf("aᵇ", if (is3D) "z" else "√", "π", "←", "→")
                shortcuts.forEach { label -> KeyTile(label, Modifier.weight(1f)) { onKey(label) } }
            }
        }
        if (tablet) {
            val math = listOf(
                listOf(first, second, "a²", "aᵇ"),
                listOf("(", ")", "<", ">"),
                listOf(if (!calculator && is3D) "z" else "|a|", ",", "≤", "≥"),
                listOf("ABC", "speak", "√", "π"),
            )
            val digits = listOf(listOf("7", "8", "9", "÷"), listOf("4", "5", "6", "×"),
                listOf("1", "2", "3", "−"), listOf("0", ".", "=", "+"))
            val letters = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
            repeat(4) { index ->
                Row(Modifier.fillMaxWidth().height(44.dp * keyScale), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tab == "ABC" && index < 3) {
                        Row(Modifier.weight(8f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            letters[index].forEach { letter ->
                                KeyTile(letter.toString(), Modifier.weight(1f), alphabet = true) { onKey(letter.toString()) }
                            }
                        }
                    } else {
                        Row(Modifier.weight(4f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val labels = if (tab == "ABC") listOf("123", "space") else math[index]
                            labels.forEach { label ->
                                KeyTile(label, Modifier.weight(if (label == "space") 3f else 1f), gray = label in listOf("ABC", "123", "speak")) { onKey(label) }
                            }
                        }
                        Row(Modifier.weight(4f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val labels = if (tab == "ABC") listOf("(", ")", ",", ".") else digits[index]
                            labels.forEach { label ->
                                KeyTile(label, Modifier.weight(1f), gray = label.first().isDigit() || label == ".") { onKey(label) }
                            }
                        }
                    }
                    Row(Modifier.weight(2f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        when (index) {
                            0 -> FunctionMenuKey(Modifier.weight(1f), onTemplate, keypadTop)
                            1 -> listOf("←", "→").forEach { label -> KeyTile(label, Modifier.weight(1f), gray = true) { onKey(label) } }
                            2 -> KeyTile("⌫", Modifier.weight(1f), gray = true) { onKey("⌫") }
                            else -> KeyTile("↵", Modifier.weight(1f), blue = true) { onKey("↵") }
                        }
                    }
                }
            }
        } else if (tab == "ABC") {
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEach { letters ->
                Row(Modifier.fillMaxWidth().height((if (landscape) 36.dp else 44.dp) * keyScale), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (letters.length < 10) Spacer(Modifier.weight((10 - letters.length) / 2f))
                    letters.forEach { letter -> KeyTile(letter.toString(), Modifier.weight(1f), alphabet = true) { onKey(letter.toString()) } }
                    if (letters.length < 10) Spacer(Modifier.weight((10 - letters.length) / 2f))
                }
            }
            Row(Modifier.fillMaxWidth().height(44.dp * keyScale), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                KeyTile("123", Modifier.weight(1.5f), gray = true) { onKey("123") }
                KeyTile("space", Modifier.weight(4f)) { onKey("space") }
                KeyTile("⌫", Modifier.weight(1.5f), gray = true) { onKey("⌫") }
                KeyTile("↵", Modifier.weight(1.5f), blue = true) { onKey("↵") }
            }
        } else if (landscape) {
            val digits = listOf(listOf("7", "8", "9", "÷"), listOf("4", "5", "6", "×"), listOf("1", "2", "3", "−"), listOf("0", ".", "=", "+"))
            val extras = listOf(listOf(first, second, "("), listOf("ABC", "funcs", ")"), listOf("space", ",", "⌫"), listOf("a²", "√", "↵"))
            repeat(4) { index ->
                Row(Modifier.fillMaxWidth().height(44.dp * keyScale), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    digits[index].forEach { label -> KeyTile(label, Modifier.weight(1f), gray = label.first().isDigit()) { onKey(label) } }
                    Spacer(Modifier.width(4.dp))
                    extras[index].forEach { label ->
                        if (label == "funcs") FunctionMenuKey(Modifier.weight(1f), onTemplate, keypadTop)
                        else KeyTile(label, Modifier.weight(1f), gray = label in listOf("ABC", "⌫"), blue = label == "↵") { onKey(label) }
                    }
                }
            }
        } else {
            val rows = listOf(
                listOf(first, "7", "8", "9", "÷"),
                listOf(second, "4", "5", "6", "×"),
                listOf("(", "1", "2", "3", "−"),
                listOf(")", "0", ".", "=", "+"),
                listOf("ABC", "funcs", "space", "⌫", "↵"),
            )
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().height(46.dp * keyScale), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { label ->
                        if (label == "funcs") FunctionMenuKey(Modifier.weight(1f), onTemplate, keypadTop)
                        else KeyTile(label, Modifier.weight(if (label == "space") 1.5f else 1f),
                            gray = label.first().isDigit() || label in listOf("ABC", "⌫"), blue = label == "↵") { onKey(label) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun KeyTile(label: String, modifier: Modifier = Modifier, gray: Boolean = false, blue: Boolean = false, alphabet: Boolean = false, active: Boolean = false, onClick: () -> Unit) {
    val fill = when { blue -> Blue; active -> DemosColors.BlueSoft; gray -> DemosColors.KeySecondary; else -> DemosColors.Paper }
    val ink = when { blue -> Color.White; active -> Blue; else -> DemosColors.Ink }
    BoxWithConstraints(modifier.fillMaxHeight().clip(MaterialTheme.shapes.small).background(fill)
        .border(1.dp, if (blue || active) Blue else Border, MaterialTheme.shapes.small)
        .clickable(role = Role.Button, onClick = onClick).semantics {
            contentDescription = when (label) { "⌫" -> "Backspace"; "↵" -> "New expression"; "edit" -> "Edit expression"; "speak" -> "Read expression aloud"; "space" -> "Space"; "funcs" -> "Functions"; else -> label }
            if (label == "←" || label == "→") stateDescription = if (label == "←") "Move cursor left" else "Move cursor right"
        }, contentAlignment = Alignment.Center) {
        val iconSize = minOf(22.dp, (maxHeight - 8.dp).coerceAtLeast(0.dp), (maxWidth - 8.dp).coerceAtLeast(0.dp))
        when (label) {
            "⌫" -> CalcIcon(IconKind.Backspace, ink, Modifier.size(iconSize))
            "↵" -> CalcIcon(IconKind.Return, ink, Modifier.size(iconSize))
            "speak" -> CalcIcon(IconKind.Speaker, ink, Modifier.size(iconSize))
            "←" -> CalcIcon(IconKind.ArrowLeft, ink, Modifier.size(iconSize))
            "→" -> CalcIcon(IconKind.ArrowRight, ink, Modifier.size(iconSize))
            else -> {
                val variable = !alphabet && (label in listOf("a²", "aᵇ", "aₙ", "eˣ") || (label.length == 1 && label[0].isLetter() && label != "π"))
                val size = when {
                    alphabet -> 20.sp
                    label.length == 1 && label[0].isDigit() -> 26.sp
                    label in listOf("ABC", "123", "funcs", "space") -> 13.sp
                    label.length > 2 -> 18.sp
                    else -> 24.sp
                }
                // Use one typeface for scripts instead of mixing Unicode fallback glyphs.
                val caption = remember(label) { buildAnnotatedString {
                    val script = when (label) { "a²" -> "2"; "aᵇ" -> "b"; "aₙ" -> "n"; "eˣ" -> "x"; else -> null }
                    append(if (script != null) label.first().toString() + script else if (label == "funcs") "functions" else label)
                    if (script != null) addStyle(SpanStyle(fontSize = .65.em,
                        baselineShift = if (label == "aₙ") BaselineShift.Subscript else BaselineShift.Superscript,
                        fontStyle = if (script[0].isLetter()) FontStyle.Italic else FontStyle.Normal), 1, 2)
                } }
                val style = interfaceTextStyle(size).copy(
                    color = ink, lineHeight = size * 1.3f,
                    fontFamily = if (variable || label in listOf("π", "√", "|a|")) MathFont else FontFamily.SansSerif,
                    fontStyle = if (variable) FontStyle.Italic else FontStyle.Normal,
                )
                val measurer = rememberTextMeasurer(cacheSize = 2)
                val measured = measurer.measure(caption, style, softWrap = false, maxLines = 1)
                val density = LocalDensity.current
                val scale = with(density) {
                    minOf(1f, (maxWidth - 8.dp).toPx() / measured.size.width.coerceAtLeast(1),
                        (maxHeight - 4.dp).toPx() / measured.size.height.coerceAtLeast(1)).coerceIn(.1f, 1f)
                }
                Text(caption, style = style.copy(fontSize = size * scale, lineHeight = size * (1.3f * scale)),
                    maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Mathematical display is distinct from source so CAS syntax stays editable and portable. */
@Composable
internal fun MathExpression(source: String, fontSize: Int = 24, showCursor: Boolean = false, cursor: Int = source.length) {
    val integral = parseIntegral(source)
    if (integral != null) {
        val args = integral.second
        val variable = args.getOrElse(1) { "x" }.ifBlank { "x" }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("∫", style = mathTextStyle((fontSize * 1.9).sp))
            if (args.size >= 4) {
                Column(Modifier.offset(x = (-3).dp).height(with(LocalDensity.current) { (fontSize * 2.1).sp.toDp() }), verticalArrangement = Arrangement.SpaceBetween) {
                    PrettyText(args[3], (fontSize * .64).toInt())
                    PrettyText(args[2], (fontSize * .64).toInt())
                }
            }
            val body = args.firstOrNull().orEmpty()
            val opening = source.indexOf('(') + 1
            Row(Modifier.padding(start = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                val bodyCursor = cursor - opening
                if (showCursor && bodyCursor in 0..body.length) {
                    PrettyText(body.take(bodyCursor), fontSize)
                    MathCaret(fontSize)
                    PrettyText(body.drop(bodyCursor), fontSize)
                } else {
                    if (body.isEmpty()) Text("□", style = mathTextStyle(fontSize.sp), color = DemosColors.Subtle)
                    else PrettyText(body, fontSize)
                }
                PrettyText(" d$variable", fontSize)
                if (showCursor && cursor >= source.length) MathCaret(fontSize)
            }
        }
    } else if (!showCursor) {
        StructuredMath(source, fontSize)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val at = cursor.coerceIn(0, source.length)
            if (showCursor) {
                PrettyText(source.take(at), fontSize)
                MathCaret(fontSize)
                PrettyText(source.drop(at), fontSize)
            } else PrettyText(source, fontSize)
        }
    }
}

@Composable
internal fun RenderMathDisplay(display: MathDisplay, fontSize: Int) {
    when (display) {
        is MathDisplay.Text -> PrettyText(display.value, fontSize)
        is MathDisplay.Row -> Row(verticalAlignment = Alignment.CenterVertically) {
            display.children.forEach { RenderMathDisplay(it, fontSize) }
        }
        is MathDisplay.Fraction -> Column(
            Modifier.width(IntrinsicSize.Max).padding(horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val childSize = (fontSize * .85).toInt().coerceAtLeast(12)
            RenderMathDisplay(display.numerator, childSize)
            Box(Modifier.fillMaxWidth().height(1.dp).background(DemosColors.Ink))
            RenderMathDisplay(display.denominator, childSize)
        }
    }
}

@Composable
private fun MathCaret(fontSize: Int) {
    Box(Modifier.padding(horizontal = .5.dp).width(1.dp).height(with(LocalDensity.current) { (fontSize * 1.1).sp.toDp() }).background(Blue))
}

@Composable
private fun PrettyText(source: String, fontSize: Int) {
    val pretty = remember(source, fontSize) { prettyMath(source, fontSize) }
    Text(pretty, style = mathTextStyle(fontSize.sp), softWrap = false)
}

private fun prettyMath(source: String, fontSize: Int) = buildAnnotatedString {
    val text = source.replace("**", "^").replace("<=", "≤").replace(">=", "≥").replace("!=", "≠")
        .replace("infinity", "∞").replace("sqrt", "√").replace(Regex("\\bpi\\b"), "π")
        .replace(Regex("(?<=[0-9.)])\\*(?=[a-zA-Zπ(])"), "").replace('*', '·')
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '_' && i + 1 < text.length && text[i + 1].isDigit()) {
            var end = i + 1
            while (end < text.length && text[end].isDigit()) end++
            pushStyle(SpanStyle(fontSize = (fontSize * .68).sp, baselineShift = BaselineShift.Subscript))
            append(text.substring(i + 1, end)); pop(); i = end
        } else if (c == '^' && i + 1 < text.length) {
            val start = i + 1
            var end = start
            var exponent: String
            if (text[start] == '(') {
                var depth = 1
                end = start + 1
                while (end < text.length && depth > 0) { if (text[end] == '(') depth++; if (text[end] == ')') depth--; end++ }
                exponent = text.substring(start + 1, if (depth == 0) end - 1 else end)
            } else {
                if (text[end] == '-') end++
                while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '.')) end++
                exponent = text.substring(start, end)
            }
            if (end == start) { append(c); i++; continue }
            pushStyle(SpanStyle(fontSize = (fontSize * .68).sp, baselineShift = BaselineShift.Superscript))
            append(exponent)
            pop()
            i = end
        } else {
            if (c.isLetter() && c !in "Cπ") pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            append(if (c == '-') '−' else c)
            if (c.isLetter() && c !in "Cπ") pop()
            i++
        }
    }
}

private fun parseIntegral(source: String): Pair<String, List<String>>? {
    val trimmed = source.trim()
    val prefix = listOf("integrate(", "int(").firstOrNull { trimmed.startsWith(it) } ?: return null
    if (!trimmed.endsWith(')')) return null
    val body = trimmed.substring(prefix.length, trimmed.length - 1)
    val args = mutableListOf<String>()
    var depth = 0
    var start = 0
    body.forEachIndexed { i, c ->
        if (c in "([{") depth++
        if (c in ")]}") depth--
        if (c == ',' && depth == 0) { args += body.substring(start, i).trim(); start = i + 1 }
    }
    args += body.substring(start).trim()
    return if (args.size == 2 || args.size == 4) prefix to args else null
}

@Composable
private fun SaveDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = DemosColors.Paper,
        title = { Text("Save graph") },
        text = { OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Graph title") }, singleLine = true, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = { onSave(title.trim().ifEmpty { "Untitled Graph" }) }, shape = MaterialTheme.shapes.small) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss, shape = MaterialTheme.shapes.small) { Text("Cancel") } },
    )
}

@Composable
private fun LibrarySheet(graphs: List<SavedGraph>, onDismiss: () -> Unit, onAction: (CalculatorAction) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DemosColors.Paper) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("My graphs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAction(CalculatorAction.New); onDismiss() }, shape = MaterialTheme.shapes.small) { Text("+ New") }
            }
            Text("Saved on this device", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(bottom = 12.dp))
            if (graphs.isEmpty()) {
                Text("Save your first graph using the Save button.", color = Muted, modifier = Modifier.padding(vertical = 30.dp))
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    itemsIndexed(graphs, key = { _, item -> item.id }) { _, item ->
                        Row(Modifier.fillMaxWidth().clickable { onAction(CalculatorAction.Open(item.id)); onDismiss() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CalcIcon(IconKind.Folder, Blue, Modifier.size(27.dp))
                            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.updatedAt)), style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                            DismissControl("Delete graph ${item.title}", onClick = { onAction(CalculatorAction.DeleteGraph(item.id)) })
                        }
                        HorizontalDivider(color = Border)
                    }
                }
            }
            TextButton(onClick = { onAction(CalculatorAction.ImportDocument); onDismiss() }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Import graph file") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSheet(state: CalculatorUiState, graphSettings: Boolean, onDismiss: () -> Unit, onAction: (CalculatorAction) -> Unit) {
    val activeBounds = if (state.is3D) state.bounds3D else state.bounds
    val appearance = if (state.is3D) state.appearance3D else state.appearance2D
    val ranges = remember(state.is3D, activeBounds) {
        with(activeBounds) { mutableStateListOf(xMin?.toString().orEmpty(), xMax?.toString().orEmpty(), yMin?.toString().orEmpty(), yMax?.toString().orEmpty(),
            zMin?.toString().orEmpty(), zMax?.toString().orEmpty(), tMin.toString(), tMax.toString(), uMin.toString(), uMax.toString(), vMin.toString(), vMax.toString()) }
    }
    var showMore by remember { mutableStateOf(false) }
    var showBounds by remember(state.is3D) { mutableStateOf(!state.is3D) }
    var boundsError by remember { mutableStateOf<String?>(null) }
    var precision by remember(state.precisionDigits) { mutableStateOf(state.precisionDigits.toString()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.matchParentSize().clickable(onClick = onDismiss))
            Surface(Modifier.align(Alignment.TopEnd).padding(12.dp).widthIn(max = 400.dp).fillMaxWidth()
                .heightIn(max = (maxHeight - 24.dp).coerceAtLeast(0.dp)),
                shape = MaterialTheme.shapes.large, color = DemosColors.Paper, shadowElevation = 8.dp, border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (!graphSettings) "App settings" else if (state.is3D) "3D graph settings" else "2D graph settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                DismissControl("Close settings", onClick = onDismiss)
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            if (graphSettings) {
            if (state.is3D) {
                Text("Perspective", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Orthographic", fontSize = 11.sp, color = Muted)
                    Slider(appearance.perspective, { onAction(CalculatorAction.SetAppearance(appearance.copy(perspective = it))) },
                        modifier = Modifier.weight(1f), valueRange = 0f..1f)
                    Text("Perspective", fontSize = 11.sp, color = Muted)
                }
            }
            SettingsCheck(if (state.is3D) "XY plane grid" else "Grid", if (state.is3D) state.showGrid3D else state.showGrid) { onAction(CalculatorAction.SetGrid(it)) }
            SettingsCheck(if (state.is3D) "3D axes and box" else "Axes", if (state.is3D) state.showAxes3D else state.showAxes) { onAction(CalculatorAction.SetAxes(it)) }
            SettingsCheck("Axis numbers", appearance.numbers) { onAction(CalculatorAction.SetAppearance(appearance.copy(numbers = it))) }
            SettingsCheck("Axis labels", appearance.labels) { onAction(CalculatorAction.SetAppearance(appearance.copy(labels = it))) }
            if (appearance.labels) repeat(if (state.is3D) 3 else 2) { axis ->
                val label = listOf(appearance.xLabel, appearance.yLabel, appearance.zLabel)[axis]
                OutlinedTextField(label, { text ->
                    onAction(CalculatorAction.SetAppearance(when (axis) {
                        0 -> appearance.copy(xLabel = text)
                        1 -> appearance.copy(yLabel = text)
                        else -> appearance.copy(zLabel = text)
                    }))
                }, label = { Text("${"XYZ"[axis]}-axis label") }, singleLine = true, shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp))
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            if (state.is3D) {
                DisclosureHeader("All bounds", showBounds) { showBounds = !showBounds }
                if (showBounds) BoundFieldPair("All", ranges[0], ranges[1],
                    { value -> for (i in listOf(0, 2, 4)) ranges[i] = value },
                    { value -> for (i in listOf(1, 3, 5)) ranges[i] = value })
            } else Text("Viewing window", style = MaterialTheme.typography.titleSmall)
            if (showBounds) {
            Text("Leave both bounds blank for the default view.", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 7.dp))
            repeat(if (state.is3D) 3 else 2) { axis ->
                BoundFieldPair("${"xyz"[axis]}", ranges[axis * 2], ranges[axis * 2 + 1],
                    { ranges[axis * 2] = it }, { ranges[axis * 2 + 1] = it })
            }
            boundsError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DemosColors.Error, modifier = Modifier.padding(top = 5.dp)) }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onAction(CalculatorAction.SetBounds(GraphBounds())); boundsError = null }, shape = MaterialTheme.shapes.small) { Text("Reset bounds") }
                Button(onClick = {
                    val values = ranges.map { it.trim().takeIf(String::isNotEmpty)?.toDoubleOrNull() }
                    val malformed = ranges.indices.any { ranges[it].isNotBlank() && (values[it] == null || values[it]?.isFinite() != true) }
                    val missing = (0..2).any { (values[it * 2] == null) != (values[it * 2 + 1] == null) } || (6..11).any { values[it] == null }
                    val unordered = (0..5).any { pair -> val a = values[pair * 2]; val b = values[pair * 2 + 1]; a != null && b != null && a >= b }
                    val nonpositive = !state.is3D && ((appearance.logX && values[0] != null && values[0]!! <= 0.0) || (appearance.logY && values[2] != null && values[2]!! <= 0.0))
                    if (nonpositive) boundsError = "Logarithmic axes require positive bounds."
                    else if (malformed || missing || unordered) boundsError = "Enter finite minimum and maximum values, with minimum less than maximum."
                    else {
                        onAction(CalculatorAction.SetBounds(GraphBounds(values[0], values[1], values[2], values[3], values[4], values[5],
                            values[6]!!, values[7]!!, values[8]!!, values[9]!!, values[10]!!, values[11]!!)))
                        boundsError = null
                    }
                }, shape = MaterialTheme.shapes.small) { Text("Apply") }
            }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            DisclosureHeader("More options", showMore) { showMore = !showMore; if (showMore) showBounds = true }
            if (showMore) {
                if (!state.is3D) {
                    Text("X-axis scale", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp))
                    SettingsSegments("Linear", "Logarithmic", !appearance.logX) { onAction(CalculatorAction.SetAppearance(appearance.copy(logX = !it))) }
                    Text("Y-axis scale", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp))
                    SettingsSegments("Linear", "Logarithmic", !appearance.logY) { onAction(CalculatorAction.SetAppearance(appearance.copy(logY = !it))) }
                    Text("Logarithmic axes display positive values in powers of 10.", fontSize = 11.sp, color = Muted)
                }
                Text("Parametric ranges", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp))
                repeat(3) { parameter ->
                    val index = 6 + parameter * 2
                    BoundFieldPair("${"tuv"[parameter]}", ranges[index], ranges[index + 1], { ranges[index] = it }, { ranges[index + 1] = it })
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Angle measure", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
            SettingsSegments("Radians", "Degrees", !state.degreeMode) { onAction(CalculatorAction.SetDegreeMode(!it)) }
            } else {
                Text("Default result format", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp))
                SettingsSegments("Decimal", "Exact", state.defaultDecimal) { onAction(CalculatorAction.SetDefaultDecimal(it)) }
                Text("Tap a result to switch formats. Symbolic answers stay exact.", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 5.dp))
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                KeyboardHeightControl(state.keyboardHeightScale) { onAction(CalculatorAction.SetKeyboardHeightScale(it)) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingRow("Complex mode", "Allow complex solutions", state.complexMode) { onAction(CalculatorAction.SetComplexMode(it)) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Angle measure", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
                SettingsSegments("Radians", "Degrees", !state.degreeMode) { onAction(CalculatorAction.SetDegreeMode(!it)) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = precision, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) precision = it },
                        label = { Text("Precision digits") }, supportingText = { Text("10–1000 digits") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = MaterialTheme.shapes.small, textStyle = interfaceTextStyle(14.sp), modifier = Modifier.weight(1f))
                    TextButton(onClick = { precision.toIntOrNull()?.takeIf { it in 10..1000 }?.let { onAction(CalculatorAction.SetPrecisionDigits(it)) } },
                        enabled = (precision.toIntOrNull() ?: 0) in 10..1000, shape = MaterialTheme.shapes.small) { Text("Apply") }
                }
            }
                }
            }
        }
    }
}

@Composable
private fun SettingsCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp), colors = CheckboxDefaults.colors(checkedColor = Blue))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SettingsSegments(first: String, second: String, firstSelected: Boolean, onSelect: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(DemosColors.Surface)
        .border(1.dp, Border, MaterialTheme.shapes.small).padding(4.dp).selectableGroup()) {
        listOf(first to true, second to false).forEach { (label, selected) ->
            val active = firstSelected == selected
            Box(Modifier.weight(1f).heightIn(min = 40.dp).clip(MaterialTheme.shapes.extraSmall)
                .background(if (active) DemosColors.BlueSoft else Color.Transparent)
                .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(selected) }).padding(horizontal = 8.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(label, color = if (active) Blue else Muted, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun BoundFieldPair(axis: String, minimum: String, maximum: String, onMin: (String) -> Unit, onMax: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 5.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        OutlinedTextField(value = minimum, onValueChange = onMin, label = { Text("$axis min") }, placeholder = { Text("Auto") },
            singleLine = true, shape = MaterialTheme.shapes.small, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), textStyle = interfaceTextStyle(14.sp), modifier = Modifier.weight(1f))
        OutlinedTextField(value = maximum, onValueChange = onMax, label = { Text("$axis max") }, placeholder = { Text("Auto") },
            singleLine = true, shape = MaterialTheme.shapes.small, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), textStyle = interfaceTextStyle(14.sp), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SettingRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontSize = 16.sp); Text(detail, fontSize = 12.sp, color = Muted) }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Blue))
    }
}

@Composable
private fun HelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DemosColors.Paper) {
        Column(Modifier.fillMaxWidth().heightIn(max = 610.dp).verticalScroll(rememberScrollState()).padding(horizontal = 23.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text("Calculator guide", style = MaterialTheme.typography.titleLarge)
            Text("Edit mathematics directly: tap a numerator, denominator, or exponent to place the cursor there. Division creates a fraction; use the arrow keys to move between slots or out of a fraction. Hold and drag to select. Type opening and closing parentheses yourself; typing a function name does not add parentheses. The keypad adapts to calculator, 2D, and 3D modes. Decimal results appear by default; choose Decimal or Exact in Settings. Tap a result to switch formats; long-press to copy it. Drag the small blue handle above the keypad up or down to adjust its height.", fontSize = 13.sp, color = Muted, lineHeight = 19.sp)
            Text("Symbolic mathematics is powered by Giac/Xcas and runs on your device. Use exact fractions and assumptions when you need symbolic results.", fontSize = 12.sp, color = Muted, lineHeight = 18.sp)
            listOf(
                "2D graphs" to "y=sin(x)\nx^2+y^2=9\ny<x^2\na=2\ny=a*x^2",
                "3D graphs" to "z=x^2+y^2\nx^2+y^2+z^2=9\nz=sin(x)*cos(y)",
                "Symbolic calculus" to "integrate(x^2,x)\nintegrate(87*x,x,0,100)\ndiff(sin(x),x)\nlimit(sin(x)/x,x,0)",
                "Algebra" to "solve(x^2-4=0,x)\nfactor(x^3-1)\nexpand((x+1)^5)\nsimplify((x^2-1)/(x-1))",
                "Assumptions" to "assume(x>0)\nsimplify(sqrt(x^2))\npurge(x)",
                "Matrices and complex numbers" to "det([[1,2],[3,4]])\ninv([[1,2],[3,4]])\n(2+3*i)^2\nsqrt(-1)",
                "Tables and regressions" to "Use + → table, then tap a cell and type with the math keypad. Enter moves down; Tab moves across. Hold a column's point icon to change its color. Use the regression button in the table margin to fit a curve, choose a model, and plot residuals.\nlinear_regression(x_1,y_1)\npolynomial_regression(x_1,y_1,2)\ny_1~m*x_1+b",
                "Math LaTeX" to "Type or paste \\frac{1}{2}, \\sqrt{x}, x^{2}, x_{1}, or \\sin(x). Use Functions → LaTeX for templates. Fractions, roots, powers, subscripts, and common math commands are rendered directly in the editable equation.",
                "Import and export" to "Use the top menu to save a graph as PNG or PDF or import a two-column CSV, TSV, or JSON table.\nCSV/TSV may include an x,y header. JSON accepts [[x,y],…], [{\"x\":1,\"y\":2},…], or x and y arrays.\nUse a table's options menu to export its data.\nImports support up to 2,000 rows and 1 MB.",
                "Fullscreen" to "Use the top menu to open calculator or graph fullscreen. Your choice is remembered when you reopen the app.\nCalculator fullscreen opens with the keypad ready for typing. Keyboard visibility is remembered when you reopen the app. Tap anywhere in an equation to place the cursor. Back hides the keypad, then returns to the split view.",
            ).forEach { (heading, examples) ->
                Text(heading, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(examples, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().background(DemosColors.Surface, MaterialTheme.shapes.small).padding(12.dp))
            }
            Spacer(Modifier.height(15.dp))
        }
    }
}

private enum class IconKind { Folder, More, Wrench, Plus, Undo, Redo, Settings, Collapse, Expand, Delete, Backspace, Return, Speaker, Keyboard, Fullscreen, Export, ArrowLeft, ArrowRight }

@Composable
private fun ToolIcon(kind: IconKind, description: String, color: Color = Muted, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp).semantics { contentDescription = description }) {
        Box(Modifier.size(36.dp).clip(MaterialTheme.shapes.small).background(if (color == Blue) DemosColors.BlueSoft else Color.Transparent), contentAlignment = Alignment.Center) {
            CalcIcon(kind, if (enabled) color else DemosColors.Disabled, Modifier.size(22.dp))
        }
    }
}

@Composable
private fun GraphTool(kind: IconKind, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GraphControl(description, modifier, onClick) {
        CalcIcon(kind, Muted, Modifier.size(22.dp))
    }
}

@Composable
private fun CalcIcon(kind: IconKind, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * .08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = .08f) = drawLine(color, Offset(x1 * w, y1 * h), Offset(x2 * w, y2 * h), w * width, StrokeCap.Round)
        fun path(points: List<Pair<Float, Float>>, close: Boolean = false) {
            val p = Path().apply {
                points.forEachIndexed { i, point -> if (i == 0) moveTo(point.first * w, point.second * h) else lineTo(point.first * w, point.second * h) }
                if (close) close()
            }
            drawPath(p, color, style = stroke)
        }
        when (kind) {
            IconKind.Fullscreen -> {
                path(listOf(.12f to .36f, .12f to .12f, .36f to .12f))
                path(listOf(.64f to .12f, .88f to .12f, .88f to .36f))
                path(listOf(.12f to .64f, .12f to .88f, .36f to .88f))
                path(listOf(.64f to .88f, .88f to .88f, .88f to .64f))
            }
            IconKind.Keyboard -> {
                path(listOf(.07f to .23f, .93f to .23f, .93f to .79f, .07f to .79f), true)
                listOf(.36f, .5f).forEach { y -> listOf(.24f, .41f, .58f, .75f).forEach { x -> drawCircle(color, w * .025f, Offset(w*x,h*y)) } }
                line(.3f, .66f, .7f, .66f, .06f)
            }
            IconKind.Export -> {
                path(listOf(.18f to .58f, .18f to .86f, .82f to .86f, .82f to .58f))
                line(.5f, .12f, .5f, .64f)
                path(listOf(.28f to .34f, .5f to .12f, .72f to .34f))
            }
            IconKind.Folder -> {
                path(listOf(.08f to .8f, .08f to .2f, .36f to .2f, .47f to .30f, .86f to .30f))
                path(listOf(.12f to .8f, .28f to .43f, .95f to .43f, .79f to .8f), true)
            }
            IconKind.More -> {
                listOf(.2f, .5f, .8f).forEach { x -> drawCircle(color, w * .065f, Offset(w * x, h * .5f)) }
            }
            IconKind.Plus -> { line(.2f, .5f, .8f, .5f); line(.5f, .2f, .5f, .8f) }
            IconKind.Delete -> { line(.2f, .2f, .8f, .8f); line(.8f, .2f, .2f, .8f) }
            IconKind.ArrowLeft, IconKind.ArrowRight -> {
                val right = kind == IconKind.ArrowRight
                val tip = if (right) .8f else .2f
                line(.2f, .5f, .8f, .5f)
                path(listOf(.5f to .2f, tip to .5f, .5f to .8f))
            }
            IconKind.Collapse, IconKind.Expand -> {
                val invert = kind == IconKind.Expand
                val y: (Float) -> Float = { if (invert) 1f - it else it }
                path(listOf(.2f to y(.25f), .5f to y(.53f), .8f to y(.25f)))
                path(listOf(.2f to y(.48f), .5f to y(.76f), .8f to y(.48f)))
            }
            IconKind.Undo, IconKind.Redo -> {
                val flip = kind == IconKind.Redo
                fun x(v: Float) = if (flip) 1f - v else v
                val p = Path().apply {
                    moveTo(x(.84f) * w, h * .73f)
                    cubicTo(x(.82f) * w, h * .28f, x(.45f) * w, h * .18f, x(.17f) * w, h * .53f)
                }
                drawPath(p, color, style = stroke)
                path(listOf(x(.18f) to .22f, x(.17f) to .53f, x(.48f) to .52f))
            }
            IconKind.Settings -> {
                val p = Path()
                val teeth = 24
                repeat(teeth) { i ->
                    val angle = (i.toDouble() / teeth * Math.PI * 2)
                    val radius = w * if (i % 3 == 0 || i % 3 == 1) .43f else .34f
                    val point = Offset(w / 2 + kotlin.math.cos(angle).toFloat() * radius, h / 2 + kotlin.math.sin(angle).toFloat() * radius)
                    if (i == 0) p.moveTo(point.x, point.y) else p.lineTo(point.x, point.y)
                }
                p.close(); drawPath(p, color, style = stroke)
                drawCircle(color, w * .15f, style = stroke)
            }
            IconKind.Wrench -> {
                path(listOf(.14f to .74f, .5f to .38f, .48f to .22f, .61f to .09f, .76f to .08f,
                    .66f to .28f, .75f to .38f, .94f to .28f, .93f to .45f, .8f to .58f, .64f to .56f, .28f to .92f), close = true)
            }
            IconKind.Backspace -> {
                path(listOf(.08f to .50f, .34f to .20f, .91f to .20f, .91f to .80f, .34f to .80f), true)
                line(.49f, .35f, .74f, .65f, .075f)
                line(.74f, .35f, .49f, .65f, .075f)
            }
            IconKind.Return -> {
                val p = Path().apply { moveTo(w * .84f, h * .22f); lineTo(w * .84f, h * .42f); quadraticTo(w * .84f, h * .62f, w * .64f, h * .62f); lineTo(w * .18f, h * .62f) }
                drawPath(p, color, style = stroke)
                path(listOf(.34f to .46f, .18f to .62f, .34f to .78f))
            }
            IconKind.Speaker -> {
                path(listOf(.08f to .35f, .28f to .35f, .5f to .15f, .5f to .85f, .28f to .65f, .08f to .65f), close = true)
                val p = Path().apply { moveTo(w * .65f, h * .32f); quadraticTo(w * .84f, h * .5f, w * .65f, h * .68f) }
                drawPath(p, color, style = stroke)
                val outer = Path().apply { moveTo(w * .78f, h * .2f); quadraticTo(w * 1.08f, h * .5f, w * .78f, h * .8f) }
                drawPath(outer, color, style = stroke)
            }
        }
    }
}
