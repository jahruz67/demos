@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.demos.graphing.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import dev.demos.graphing.plot.PlotExpression
import dev.demos.graphing.plot.PlotProgram
import dev.demos.graphing.plot.PlotSettings

private val Blue = Color(0xFF2D70DA)
private val Header = Color(0xFF292929)
private val Muted = Color(0xFF656565)
private val Border = Color(0xFFD3D3D3)
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
    var showMenu by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var tableExportId by remember { mutableStateOf<Long?>(null) }
    var showGraphExport by remember { mutableStateOf(false) }
    val graphState = rememberSaveableStateHolder()
    var pendingInsertion by remember { mutableStateOf<String?>(null) }
    var pendingFromId by remember { mutableLongStateOf(-1L) }
    val context = LocalContext.current
    var speech by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) { onDispose { speech?.shutdown() } }
    val focusRequester = remember { FocusRequester() }
    val messages = remember { SnackbarHostState() }
    val currentRow = state.rows.firstOrNull { it.id == state.activeId }
    val plotSources = state.rows.map { PlotExpression(it.id, it.text, it.color) }
    val plotParameters = state.sliders.associate { it.name to it.value }
    val graphableIds = remember(plotSources, plotParameters) {
        PlotProgram.compile(plotSources, PlotSettings(parameters = plotParameters)).relations.map { it.id }.toSet()
    }

    LaunchedEffect(state.rows.map { it.id }) {
        editorValues.keys.retainAll(state.rows.map { it.id }.toSet())
    }
    LaunchedEffect(state.statusMessage) { state.statusMessage?.let { messages.showSnackbar(it) } }
    BackHandler(enabled = !keyboardVisible && state.layout != CalculatorLayout.Split) { onAction(CalculatorAction.SetLayout(CalculatorLayout.Split)) }
    BackHandler(enabled = keyboardVisible && !showMenu && !showSave && !showLibrary && !showSettings && !showHelp) { setKeyboardVisible(false) }

    fun editorValue(row: ExpressionRow): TextFieldValue = editorValues[row.id]
        ?.takeIf { it.text == row.text } ?: TextFieldValue(row.text, TextRange(row.text.length))

    fun updateInput(row: ExpressionRow, value: TextFieldValue, complete: Boolean = true) {
        val next = if (complete) MathInputRules.apply(editorValue(row), value) else value
        editorValues[row.id] = next
        onAction(CalculatorAction.SelectRow(row.id))
        onAction(CalculatorAction.EditRow(row.id, next.text))
    }

    fun edit(insert: String, retreat: Int = 0) {
        val row = state.rows.firstOrNull { it.id == state.activeId } ?: return
        val value = editorValue(row)
        val start = value.selection.min.coerceIn(0, row.text.length)
        val end = value.selection.max.coerceIn(start, row.text.length)
        val next = row.text.replaceRange(start, end, insert)
        updateInput(row, TextFieldValue(next, TextRange((start + insert.length - retreat).coerceIn(0, next.length))),
            complete = retreat == 0 && insert.length == 1)
    }
    fun erase() {
        val row = state.rows.firstOrNull { it.id == state.activeId } ?: return
        val value = editorValue(row)
        updateInput(row, MathInputRules.backspace(value), complete = false)
    }
    fun moveCursor(delta: Int) {
        val row = state.rows.firstOrNull { it.id == state.activeId } ?: return
        val value = editorValue(row)
        editorValues[row.id] = MathInputRules.move(value, delta)
    }
    fun addRow() {
        state.rows.lastOrNull { parseFolderData(it.text) != null }?.let { row ->
            parseFolderData(row.text)?.takeIf { it.collapsed }?.let { folder -> onAction(CalculatorAction.EditRow(row.id, encodeFolderData(folder.copy(collapsed = false)))) }
        }
        onAction(CalculatorAction.AddRow)
    }
    fun key(label: String) {
        when (label) {
            "ABC", "123", "funcs" -> keyboardTab = label
            "←" -> moveCursor(-1)
            "→" -> moveCursor(1)
            "space" -> edit(" ")
            "⌫" -> erase()
            "↵" -> addRow()
            "a²" -> edit("^2")
            "aᵇ" -> edit("^{}", 1)
            "√" -> edit("sqrt()", 1)
            "()" -> edit("()", 1)
            "|a|" -> edit("abs()", 1)
            "π" -> edit("pi")
            "×" -> edit("*")
            "÷" -> edit("/")
            "−" -> edit("-")
            "≤" -> edit("<=")
            "≥" -> edit(">=")
            "paste" -> {
                val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { edit(it) }
            }
            "speak" -> currentRow?.let { row ->
                val utterance = row.text + if (row.result.isNotBlank()) " equals ${row.result}" else ""
                val currentSpeech = speech
                if (currentSpeech != null) {
                    currentSpeech.speak(utterance, TextToSpeech.QUEUE_FLUSH, null, "expression-${row.id}")
                } else {
                    var newSpeech: TextToSpeech? = null
                    newSpeech = TextToSpeech(context.applicationContext) { status ->
                        if (status == TextToSpeech.SUCCESS) {
                            newSpeech?.speak(utterance, TextToSpeech.QUEUE_FLUSH, null, "expression-${row.id}")
                        }
                    }
                    speech = newSpeech
                }
            }
            else -> edit(label)
        }
    }

    Column(
        Modifier.fillMaxSize().imePadding().background(Color.White)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (state.layout == CalculatorLayout.Graph || event.type != KeyEventType.KeyDown || showSave || showSettings || showLibrary || showHelp || parseNoteText(currentRow?.text.orEmpty()) != null || parseFolderData(currentRow?.text.orEmpty()) != null) false
                else when {
                    event.isCtrlPressed && event.key == Key.Z -> { onAction(if (event.isShiftPressed) CalculatorAction.Redo else CalculatorAction.Undo); true }
                    event.isCtrlPressed && event.key == Key.S -> { showSave = true; true }
                    event.isCtrlPressed && event.key == Key.V && !textInputFocused -> { key("paste"); true }
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
            val landscape = availableWidth >= 600.dp && (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE || availableWidth >= 840.dp)
            val graphPanel: @Composable (Modifier) -> Unit = { modifier ->
                Box(modifier) {
                    graphState.SaveableStateProvider("graph") { graph(state.is3D, Modifier.fillMaxSize()) }
                    if (showGraphControls) {
                        GraphTool(IconKind.Fullscreen, if (state.layout == CalculatorLayout.Graph) "Exit graph fullscreen" else "Graph fullscreen", Modifier.align(Alignment.TopStart).padding(5.dp)) {
                            setKeyboardVisible(false)
                            onAction(CalculatorAction.SetLayout(if (state.layout == CalculatorLayout.Graph) CalculatorLayout.Split else CalculatorLayout.Graph))
                        }
                        GraphTool(IconKind.Wrench, "Graph settings", Modifier.align(Alignment.TopEnd).padding(5.dp)) { showSettings = true }
                        if (state.layout == CalculatorLayout.Graph) GraphTool(IconKind.Export, "Export graph", Modifier.align(Alignment.BottomEnd).padding(6.dp)) { showGraphExport = true }
                    }
                }
            }
            val expressionPanel: @Composable ColumnScope.() -> Unit = {
                ExpressionToolbar(
                    canUndo = state.canUndo, canRedo = state.canRedo, keyboardVisible = keyboardVisible,
                    onAdd = { showAddMenu = true }, onUndo = { onAction(CalculatorAction.Undo) },
                    onRedo = { onAction(CalculatorAction.Redo) },
                    onCollapse = { setKeyboardVisible(!keyboardVisible) },
                    fullscreen = state.layout == CalculatorLayout.Calculator,
                    onFullscreen = {
                        onAction(CalculatorAction.SetLayout(if (state.layout == CalculatorLayout.Calculator) CalculatorLayout.Split else CalculatorLayout.Calculator))
                    },
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    CompositionLocalProvider(LocalBringIntoViewSpec provides ManualScrollSpec) {
                        LazyColumn(Modifier.fillMaxSize(), state = listState) {
                            itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                                val table = remember(row.text) { parseTableData(row.text) }
                                val note = remember(row.text) { parseNoteText(row.text) }
                                val folder = remember(row.text) { parseFolderData(row.text) }
                                val enclosingFolder = state.rows.take(index).lastOrNull { parseFolderData(it.text) != null }?.let { parseFolderData(it.text) }
                                if (folder == null && enclosingFolder?.collapsed == true) return@itemsIndexed
                                when {
                                    folder != null -> FolderExpressionItem(index + 1, row.id == state.activeId, folder,
                                        onSelect = { onAction(CalculatorAction.SelectRow(row.id)); setKeyboardVisible(false) },
                                        onDelete = { onAction(CalculatorAction.DeleteRow(row.id)) },
                                        onChange = { onAction(CalculatorAction.EditRow(row.id, encodeFolderData(it))) })
                                    table != null -> TableExpressionItem(row, index + 1, state.rows.take(index + 1).count { parseTableData(it.text) != null }, row.id == state.activeId, table,
                                        onSelect = { onAction(CalculatorAction.SelectRow(row.id)); setKeyboardVisible(false) },
                                        onDelete = { onAction(CalculatorAction.DeleteRow(row.id)) },
                                        onChange = { onAction(CalculatorAction.EditRow(row.id, encodeTableData(it))) },
                                        onVisibility = { onAction(CalculatorAction.ToggleVisibility(row.id)) }, onColor = { colorEditor = row }, onExport = { tableExportId = row.id })
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
                                        onSelect = { onAction(CalculatorAction.SelectRow(row.id)); setKeyboardVisible(true) },
                                        onDelete = { onAction(CalculatorAction.DeleteRow(row.id)) },
                                        onVisibility = { onAction(CalculatorAction.ToggleVisibility(row.id)) }, onColor = { colorEditor = row },
                                    )
                                }
                                state.sliders.filter {
                                    row.text.trim().startsWith(it.name + "=") || row.text.trim().startsWith(it.name + " =") ||
                                        (it.name == "C" && row.id == state.rows.firstOrNull { expression -> expression.result.contains(Regex("\\bC\\b")) }?.id)
                                }
                                    .forEach { slider -> SliderControl(slider, onAction) }
                            }
                            item {
                                Row(Modifier.fillMaxWidth().height(48.dp).clickable { addRow(); setKeyboardVisible(true) }) {
                                    Box(Modifier.width(35.dp).fillMaxHeight().background(Brush.verticalGradient(listOf(Color(0xFFEEEEEE), Color.White)))) {
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
                        graphPanel(Modifier.fillMaxWidth().height((availableHeight * if (keyboardVisible) .50f else .48f).coerceAtLeast(100.dp)))
                        expressionPanel()
                    }
                }
            }
        }

        SnackbarHost(messages)
        if (keyboardVisible && state.layout != CalculatorLayout.Graph) CalculatorKeypad(keyboardTab, state.layout, state.is3D, onKey = ::key, onTemplate = { template, retreat -> edit(template, retreat) })
    }

    if (showAddMenu) AlertDialog(
        onDismissRequest = { showAddMenu = false }, containerColor = Color.White,
        title = { Text("Add expression") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onAction(CalculatorAction.ImportTable); showAddMenu = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("Import table (CSV, TSV, JSON)", color = Blue, modifier = Modifier.fillMaxWidth())
                }
                listOf("Expression" to "", "Table" to "table([0,1,2],[0,1,4])", "Note" to "note(\"\")", "Folder" to "folder(\"Folder\",false)", "Indefinite integral" to "integrate(,x)", "Definite integral" to "integrate(,x,0,1)",
                    "Derivative" to "diff(,x)", "Solve equation" to "solve(,x)", "3D surface" to "z=x^2+y^2").forEach { (label, value) ->
                    TextButton(onClick = {
                        if (value.isNotEmpty()) {
                            pendingFromId = state.activeId
                            pendingInsertion = value
                        }
                        addRow()
                        setKeyboardVisible(true); showAddMenu = false
                    }, modifier = Modifier.fillMaxWidth()) { Text(label, color = Blue, modifier = Modifier.fillMaxWidth()) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showAddMenu = false }) { Text("Cancel") } },
    )

    tableExportId?.let { id ->
        AlertDialog(onDismissRequest = { tableExportId = null }, containerColor = Color.White,
            title = { Text("Export this table") },
            text = { Column {
                TableFileFormat.entries.forEach { format ->
                    TextButton(onClick = { onAction(CalculatorAction.ExportTable(id, format)); tableExportId = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("${format.name} (.${format.extension})", modifier = Modifier.fillMaxWidth())
                    }
                }
            } }, confirmButton = { TextButton(onClick = { tableExportId = null }) { Text("Cancel") } })
    }
    if (showGraphExport) AlertDialog(onDismissRequest = { showGraphExport = false }, containerColor = Color.White,
        title = { Text("Export graph") }, text = { Column {
            GraphExportFormat.entries.forEach { format ->
                TextButton(onClick = { onAction(CalculatorAction.ExportGraph(format)); showGraphExport = false }, modifier = Modifier.fillMaxWidth()) { Text(format.name) }
            }
        } }, confirmButton = { TextButton(onClick = { showGraphExport = false }) { Text("Cancel") } })

    val insertion = pendingInsertion
    LaunchedEffect(state.activeId, insertion) {
        if (insertion != null && state.activeId != pendingFromId && state.rows.firstOrNull { it.id == state.activeId }?.text?.isEmpty() == true) {
            onAction(CalculatorAction.EditRow(state.activeId, insertion))
            if (insertion.startsWith("table(") || insertion.startsWith("note(") || insertion.startsWith("folder(")) setKeyboardVisible(false)
            val at = insertion.indexOf('(').let { if (it >= 0) it + 1 else insertion.length }
            editorValues[state.activeId] = TextFieldValue(insertion, TextRange(at))
            pendingInsertion = null
        }
    }

    if (showSave) SaveDialog(state.title, onDismiss = { showSave = false }) { title ->
        onAction(CalculatorAction.Save(title)); showSave = false
    }
    if (showLibrary) LibrarySheet(state.savedGraphs, { showLibrary = false }, onAction)
    if (showSettings) SettingsSheet(state, { showSettings = false }, onAction)
    if (showHelp) HelpSheet { showHelp = false }
    colorEditor?.let { row -> ColorPaletteDialog(row.color, { colorEditor = null }) { color ->
        onAction(CalculatorAction.SetColor(row.id, color)); colorEditor = null
    } }
}

@Composable
private fun CalculatorHeader(state: CalculatorUiState, onOpen: () -> Unit, onSave: () -> Unit, on3D: () -> Unit, onMenu: () -> Unit,
                             menuExpanded: Boolean, onMenuDismiss: () -> Unit, menuContent: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp).background(Header).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onOpen, modifier = Modifier.size(31.dp)) { CalcIcon(IconKind.Folder, Color(0xFFDDDDDD), Modifier.size(23.dp)) }
        Box(Modifier.padding(start = 2.dp, end = 9.dp).width(1.dp).height(19.dp).background(Color(0xFF888888)))
        Text(state.title, Modifier.weight(1f).clickable(onClick = onSave), color = Color(0xFFDDDDDD), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.padding(start = 6.dp).width(55.dp).height(32.dp).clip(RoundedCornerShape(4.dp))
            .background(if (state.dirty) Blue else Color.Transparent).border(1.dp, if (state.dirty) Color(0xFF367EE8) else Color(0xFF606060), RoundedCornerShape(4.dp))
            .clickable(onClick = onSave), contentAlignment = Alignment.Center) {
            Text("Save", color = if (state.dirty) Color.White else Color(0xFF888888), fontSize = 14.sp)
        }
        Box(Modifier.padding(start = 8.dp).size(width = 34.dp, height = 30.dp).clip(RoundedCornerShape(4.dp))
            .border(1.dp, if (state.is3D) Blue else Color(0xFF707070), RoundedCornerShape(4.dp))
            .background(if (state.is3D) Blue else Color.Transparent).clickable(onClick = on3D)
            .semantics { contentDescription = "Switch to ${if (state.is3D) "2D" else "3D"} graph" }, contentAlignment = Alignment.Center) {
            Text(if (state.is3D) "2D" else "3D", color = Color(0xFFE6E6E6), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Box {
            IconButton(onClick = onMenu, modifier = Modifier.padding(start = 5.dp).size(30.dp)) { CalcIcon(IconKind.More, Color(0xFFDDDDDD), Modifier.size(23.dp)) }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = onMenuDismiss, modifier = Modifier.background(Color.White), content = menuContent)
        }
    }
}

@Composable
private fun ExpressionToolbar(canUndo: Boolean, canRedo: Boolean, keyboardVisible: Boolean, onAdd: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onCollapse: () -> Unit, fullscreen: Boolean, onFullscreen: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp).background(Brush.verticalGradient(listOf(Color(0xFFFCFCFC), Color(0xFFECECEC))))
        .border(width = .5.dp, color = Border).padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        ToolIcon(IconKind.Plus, "Add expression", onClick = onAdd)
        Spacer(Modifier.weight(1f))
        ToolIcon(IconKind.Undo, "Undo", if (canUndo) Muted else Color(0xFFC8C8C8), onUndo)
        ToolIcon(IconKind.Redo, "Redo", if (canRedo) Muted else Color(0xFFC8C8C8), onRedo)
        Spacer(Modifier.weight(1f))
        ToolIcon(IconKind.Keyboard, if (keyboardVisible) "Hide keyboard" else "Show keyboard", if (keyboardVisible) Blue else Muted, onCollapse)
        ToolIcon(IconKind.Fullscreen, if (fullscreen) "Exit calculator fullscreen" else "Calculator fullscreen", if (fullscreen) Blue else Muted, onFullscreen)
    }
}

@Composable
private fun ExpressionItem(row: ExpressionRow, number: Int, selected: Boolean, value: TextFieldValue, graphable: Boolean, onInput: (TextFieldValue) -> Unit, onInputFocus: (Boolean) -> Unit, onSelect: () -> Unit, onDelete: () -> Unit, onVisibility: () -> Unit, onColor: () -> Unit) {
    var decimal by remember(row.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val hasResult = row.result.isNotBlank()
    val largeExpression = row.text.contains("integrate(") || row.text.contains("sum(") || row.text.contains("product(")
    val minimumHeight = if (largeExpression) 84.dp else 64.dp
    Column(Modifier.fillMaxWidth().border(if (selected) 1.dp else .5.dp, if (selected) Blue else Border)) {
        Row(Modifier.fillMaxWidth().heightIn(min = minimumHeight).height(IntrinsicSize.Min)) {
            Column(Modifier.width(34.dp).fillMaxHeight().background(if (selected) Blue else Color(0xFFEEEEEE)), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$number", color = if (selected) Color.White else Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.Start).padding(start = 2.dp, top = 2.dp))
                if (row.error != null) {
                    Canvas(Modifier.padding(top = 4.dp).size(23.dp)) {
                        val p = Path().apply { moveTo(size.width / 2, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                        drawPath(p, Color(0xFFE9783E))
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
                    MathEditor(value, selected, number, Modifier.weight(1f), onInput, onInputFocus, onSelect)
                    IconButton(onClick = onDelete, modifier = Modifier.size(44.dp).semantics { contentDescription = "Delete expression $number" }) {
                        CalcIcon(IconKind.Delete, Color(0xFFBBBBBB), Modifier.size(21.dp))
                    }
                }
                if (row.isEvaluating) {
                    Row(Modifier.padding(top = 8.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Muted)
                        Text("  Calculating…", color = Muted, fontSize = 11.sp)
                    }
                } else if (row.error != null) {
                    Text(row.error, color = Color(0xFFB8532D), fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(end = 12.dp, top = 4.dp, bottom = 5.dp))
                } else if (hasResult) {
                    val result = if (decimal && row.approximate.isNotBlank()) row.approximate else row.result
                    Row(Modifier.fillMaxWidth().padding(top = 5.dp, end = 5.dp, bottom = 1.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        if (row.approximate.isNotBlank() && row.approximate != row.result) {
                            Text(if (decimal) "≈" else "=", color = Color(0xFF999999), fontSize = 18.sp, modifier = Modifier.padding(end = 6.dp).clickable { decimal = !decimal })
                        } else Text("=", color = Color(0xFF999999), fontSize = 18.sp, modifier = Modifier.padding(end = 6.dp))
                        Box(Modifier.widthIn(max = 250.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFF7F7F7)).border(.7.dp, Border, RoundedCornerShape(4.dp))
                            .combinedClickable(onClick = { decimal = !decimal }, onLongClick = {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Result", result))
                            }).padding(horizontal = 7.dp, vertical = 3.dp)) {
                            MathExpression(result, 19)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderControl(slider: SliderUi, onAction: (CalculatorAction) -> Unit) {
    if (slider.name == "C") Text("C = ${formatNumber(slider.value)}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 50.dp, top = 3.dp))
    Row(Modifier.fillMaxWidth().padding(start = 41.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (slider.playing) "Ⅱ" else "▶", color = Muted, modifier = Modifier.width(25.dp).clickable { onAction(CalculatorAction.ToggleSlider(slider.name)) })
        Text(formatNumber(slider.min), fontSize = 10.sp, color = Muted)
        Slider(value = slider.value.toFloat().coerceIn(slider.min.toFloat(), slider.max.toFloat()),
            onValueChange = { value ->
                val snapped = if (slider.step > 0) (value / slider.step).roundToInt() * slider.step else value.toDouble()
                onAction(CalculatorAction.SetSlider(slider.name, snapped))
            }, valueRange = slider.min.toFloat()..slider.max.toFloat(),
            modifier = Modifier.weight(1f).height(29.dp), colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue))
        Text(formatNumber(slider.max), fontSize = 10.sp, color = Muted)
    }
}

@Composable
private fun TableExpressionItem(
    row: ExpressionRow, number: Int, tableNumber: Int, selected: Boolean, table: TableData,
    onSelect: () -> Unit, onDelete: () -> Unit, onChange: (TableData) -> Unit, onVisibility: () -> Unit, onColor: () -> Unit, onExport: () -> Unit,
) {
    var editing by remember(row.id) { mutableStateOf<Pair<Int, Boolean>?>(null) }
    fun changeCell(index: Int, isX: Boolean, value: String) {
        val x = MutableList(table.size) { table.x.getOrElse(it) { "" } }
        val y = MutableList(table.size) { table.y.getOrElse(it) { "" } }
        if (isX) x[index] = value else y[index] = value
        onChange(TableData(x, y))
    }
    Row(Modifier.fillMaxWidth().border(if (selected) 1.dp else .5.dp, if (selected) Blue else Border).height(IntrinsicSize.Min)) {
        Box(Modifier.width(34.dp).fillMaxHeight().background(if (selected) Blue else Color(0xFFEEEEEE)).clickable(onClick = onSelect)) {
            Text("$number", fontSize = 10.sp, color = if (selected) Color.White else Muted, modifier = Modifier.padding(start = 2.dp, top = 2.dp))
            Text("▦", fontSize = 27.sp, color = Color(row.color).copy(alpha = if (row.hidden) .3f else 1f),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 19.dp).combinedClickable(onClick = onVisibility, onLongClick = onColor))
        }
        Column(Modifier.weight(1f).padding(start = 9.dp, top = 7.dp, end = 5.dp, bottom = 6.dp)) {
            Row(Modifier.fillMaxWidth().height(29.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Table $tableNumber", fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f).clickable(onClick = onSelect))
                IconButton(onClick = onExport, modifier = Modifier.size(32.dp).semantics { contentDescription = "Export table $tableNumber" }) {
                    CalcIcon(IconKind.Export, Muted, Modifier.size(19.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(25.dp)) { CalcIcon(IconKind.Delete, Color(0xFFCCCCCC), Modifier.size(20.dp)) }
            }
            Row(Modifier.fillMaxWidth().height(35.dp)) {
                listOf("x${subscript(tableNumber)}", "y${subscript(tableNumber)}").forEach { header ->
                    Box(Modifier.weight(1f).fillMaxHeight().border(.5.dp, Border).background(Color(0xFFF7F7F7)), contentAlignment = Alignment.Center) {
                        Text(header, fontSize = 22.sp, fontFamily = MathFont, fontStyle = FontStyle.Italic)
                    }
                }
                Spacer(Modifier.width(25.dp))
            }
            LazyColumn(Modifier.height(minOf(220, maxOf(34, table.size * 34)).dp)) {
                items(table.size) { index ->
                    Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(true, false).forEach { isX ->
                            val cell = (if (isX) table.x else table.y).getOrElse(index) { "" }
                            Box(Modifier.weight(1f).fillMaxHeight().border(.5.dp, Border).clickable { onSelect(); editing = index to isX }
                                .semantics { contentDescription = "${if (isX) "x$tableNumber" else "y$tableNumber"} row ${index + 1}: ${cell.ifEmpty { "empty" }}" }.padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                                if (cell.isEmpty()) Text("—", color = Color(0xFFBBBBBB), fontSize = 16.sp) else MathExpression(cell, 18)
                            }
                        }
                        Box(Modifier.width(25.dp).fillMaxHeight().clickable {
                            val x = MutableList(table.size) { table.x.getOrElse(it) { "" } }; x.removeAt(index)
                            val y = MutableList(table.size) { table.y.getOrElse(it) { "" } }; y.removeAt(index)
                            onChange(TableData(x, y))
                        }.semantics { contentDescription = "Remove table row ${index + 1}" }, contentAlignment = Alignment.Center) {
                            Text("×", fontSize = 20.sp, color = Color(0xFFAAAAAA))
                        }
                    }
                }
            }
            TextButton(onClick = {
                onSelect()
                onChange(TableData(List(table.size) { table.x.getOrElse(it) { "" } } + "", List(table.size) { table.y.getOrElse(it) { "" } } + ""))
            }, modifier = Modifier.fillMaxWidth().height(32.dp), contentPadding = PaddingValues(0.dp)) { Text("+ Add row", color = Blue, fontSize = 12.sp) }
        }
    }
    editing?.let { (index, isX) ->
        val value = (if (isX) table.x else table.y).getOrElse(index) { "" }
        TableCellDialog("${if (isX) "x" else "y"}${subscript(tableNumber)}, row ${index + 1}", value, { editing = null }) { next ->
            changeCell(index, isX, next.trim()); editing = null
        }
    }
}

@Composable
private fun TableCellDialog(title: String, initial: String, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White, title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Number or expression") }, singleLine = true,
            textStyle = TextStyle(fontFamily = MathFont, fontSize = 21.sp), modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onApply(value) }) { Text("Apply", color = Blue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } })
}

@Composable
private fun NoteExpressionItem(row: ExpressionRow, number: Int, selected: Boolean, note: String, onSelect: () -> Unit, onDelete: () -> Unit, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).border(if (selected) 1.dp else .5.dp, if (selected) Blue else Border)) {
        Box(Modifier.width(34.dp).fillMaxHeight().background(if (selected) Blue else Color(0xFFEEEEEE)).clickable(onClick = onSelect)) {
            Text("$number", color = if (selected) Color.White else Muted, fontSize = 10.sp, modifier = Modifier.padding(start = 2.dp, top = 2.dp))
            Text("T", color = if (selected) Color.White else Muted, fontSize = 24.sp, fontFamily = MathFont,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 5.dp, top = 6.dp, bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Note", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(25.dp)) { CalcIcon(IconKind.Delete, Color(0xFFCCCCCC), Modifier.size(20.dp)) }
            }
            BasicTextField(value = note, onValueChange = onChange,
                textStyle = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = Color(0xFF333333)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 45.dp).padding(end = 12.dp).onFocusChanged { if (it.isFocused) onSelect() },
                decorationBox = { inner ->
                    Box { if (note.isEmpty()) Text("Add a note…", color = Color(0xFFAAAAAA), fontSize = 15.sp); inner() }
                })
        }
    }
}

@Composable
private fun FolderExpressionItem(number: Int, selected: Boolean, folder: FolderData, onSelect: () -> Unit, onDelete: () -> Unit, onChange: (FolderData) -> Unit) {
    Row(Modifier.fillMaxWidth().height(54.dp).background(Color(0xFFF7F7F7)).border(if (selected) 1.dp else .5.dp, if (selected) Blue else Border), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(34.dp).fillMaxHeight().background(if (selected) Blue else Color(0xFFE7E7E7)).clickable(onClick = onSelect)) {
            Text("$number", color = if (selected) Color.White else Muted, fontSize = 10.sp, modifier = Modifier.padding(start = 2.dp, top = 2.dp))
            CalcIcon(IconKind.Folder, if (selected) Color.White else Muted, Modifier.size(23.dp).align(Alignment.Center).padding(top = 6.dp))
        }
        Box(Modifier.width(30.dp).fillMaxHeight().clickable { onChange(folder.copy(collapsed = !folder.collapsed)) }
            .semantics { contentDescription = if (folder.collapsed) "Expand folder ${folder.title}" else "Collapse folder ${folder.title}" }, contentAlignment = Alignment.Center) {
            Text(if (folder.collapsed) "▸" else "▾", fontSize = 23.sp, color = Muted)
        }
        BasicTextField(value = folder.title, onValueChange = { onChange(folder.copy(title = it)) }, singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF444444)),
            modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) onSelect() },
            decorationBox = { inner -> Box { if (folder.title.isEmpty()) Text("Folder", color = Color(0xFFAAAAAA), fontSize = 16.sp); inner() } })
        IconButton(onClick = onDelete, modifier = Modifier.size(35.dp)) { CalcIcon(IconKind.Delete, Color(0xFFCCCCCC), Modifier.size(21.dp)) }
    }
}

@Composable
private fun ColorPaletteDialog(selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val colors = listOf(0xFFC74440, 0xFF2D70B3, 0xFF388C46, 0xFF6042A6, 0xFFFA7E19, 0xFF000000, 0xFFE8B735, 0xFFFA8EC2, 0xFF0099AA, 0xFF888888, 0xFF8C564B, 0xFF6B81C7)
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White, title = { Text("Expression color") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            colors.chunked(6).forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    line.forEach { packed ->
                        val color = packed.toInt()
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(Color(color))
                            .border(if (color == selected) 3.dp else 0.dp, Color(0xFFDDDDDD), RoundedCornerShape(16.dp))
                            .clickable { onPick(color) }.semantics { contentDescription = "Color ${Integer.toHexString(color)}" }, contentAlignment = Alignment.Center) {
                            if (color == selected) Text("✓", color = Color.White, fontSize = 18.sp)
                        }
                    }
                }
            }
            Text("Tap the expression icon to show or hide its graph.", color = Muted, fontSize = 12.sp)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Blue) } })
}

private fun formatNumber(n: Double) = if (n == n.toLong().toDouble()) n.toLong().toString() else "%.3g".format(n)
private fun subscript(number: Int) = number.toString().map { "₀₁₂₃₄₅₆₇₈₉"[it.digitToInt()] }.joinToString("")

@Composable
private fun CalculatorKeypad(tab: String, layout: CalculatorLayout, is3D: Boolean, onKey: (String) -> Unit, onTemplate: (String, Int) -> Unit) {
    if (tab == "funcs") { FunctionKeypad(layout, is3D, onKey, onTemplate); return }
    val calculator = layout == CalculatorLayout.Calculator
    val first = if (calculator) "aᵇ" else "x"
    val second = if (calculator) "√" else "y"
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(Modifier.fillMaxWidth().background(Color(0xFFF0F0F0)).border(.5.dp, Border).padding(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val shortcuts = if (calculator) listOf("a²", "π", "!", "←", "→") else listOf("aᵇ", "√", "π", "←", "→")
            shortcuts.forEach { label -> KeyTile(label, Modifier.weight(1f)) { onKey(label) } }
        }
        if (tab == "ABC") {
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEach { letters ->
                Row(Modifier.fillMaxWidth().height(if (landscape) 40.dp else 48.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (letters.length < 10) Spacer(Modifier.weight((10 - letters.length) / 2f))
                    letters.forEach { letter -> KeyTile(letter.toString(), Modifier.weight(1f), alphabet = true) { onKey(letter.toString()) } }
                    if (letters.length < 10) Spacer(Modifier.weight((10 - letters.length) / 2f))
                }
            }
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                KeyTile("123", Modifier.weight(1.5f), gray = true) { onKey("123") }
                KeyTile("space", Modifier.weight(4f)) { onKey("space") }
                KeyTile("⌫", Modifier.weight(1.5f), gray = true) { onKey("⌫") }
                KeyTile("↵", Modifier.weight(1.5f), blue = true) { onKey("↵") }
            }
        } else if (landscape) {
            val digits = listOf(listOf("7", "8", "9", "÷"), listOf("4", "5", "6", "×"), listOf("1", "2", "3", "−"), listOf("0", ".", "=", "+"))
            val extras = listOf(listOf(first, second, if (!calculator && is3D) "z" else "("), listOf("ABC", "funcs", if (!calculator && is3D) "()" else ")"), listOf("space", ",", "⌫"), listOf("a²", "√", "↵"))
            repeat(4) { index ->
                Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    digits[index].forEach { label -> KeyTile(label, Modifier.weight(1f), gray = label.first().isDigit()) { onKey(label) } }
                    Spacer(Modifier.width(4.dp))
                    extras[index].forEach { label -> KeyTile(label, Modifier.weight(1f), gray = label in listOf("ABC", "funcs", "⌫"), blue = label == "↵") { onKey(label) } }
                }
            }
        } else {
            val rows = listOf(
                listOf(first, "7", "8", "9", "÷"),
                listOf(second, "4", "5", "6", "×"),
                listOf(if (!calculator && is3D) "z" else "(", "1", "2", "3", "−"),
                listOf(if (!calculator && is3D) "()" else ")", "0", ".", "=", "+"),
                listOf("ABC", "funcs", "space", "⌫", "↵"),
            )
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { label -> KeyTile(label, Modifier.weight(if (label == "space") 1.5f else 1f),
                        gray = label.first().isDigit() || label in listOf("ABC", "funcs", "⌫"), blue = label == "↵") { onKey(label) } }
                }
            }
        }
    }
}

@Composable
private fun KeyTile(label: String, modifier: Modifier = Modifier, gray: Boolean = false, blue: Boolean = false, alphabet: Boolean = false, onClick: () -> Unit) {
    val top = when { blue -> Color(0xFF337BE5); gray -> Color(0xFFD1D1D1); else -> Color.White }
    val bottom = when { blue -> Blue; gray -> Color(0xFFC7C7C7); else -> Color(0xFFFAFAFA) }
    Box(modifier.fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Brush.verticalGradient(listOf(top, bottom)))
        .border(.7.dp, if (blue) Color(0xFF377FE9) else Color(0xFFD5D5D5), RoundedCornerShape(2.dp))
        .clickable(onClick = onClick).semantics { contentDescription = when (label) { "⌫" -> "Backspace"; "↵" -> "New expression"; "edit" -> "Edit expression"; "speak" -> "Read expression aloud"; "space" -> "Space"; "funcs" -> "Functions"; else -> label } }, contentAlignment = Alignment.Center) {
        when (label) {
            "⌫" -> CalcIcon(IconKind.Backspace, Color.Black, Modifier.size(20.dp))
            "↵" -> CalcIcon(IconKind.Return, Color.White, Modifier.size(27.dp))
            "edit" -> Text("✎", color = Color(0xFF828282), fontSize = 18.sp)
            "speak" -> CalcIcon(IconKind.Speaker, Color(0xFF858585), Modifier.size(18.dp))
            else -> Text(label, color = if (blue) Color.White else Color.Black,
                fontSize = when { alphabet -> 20.sp; label.length == 1 && label[0].isDigit() -> 30.sp; label in listOf("ABC", "123", "funcs", "space") -> 15.sp; label.length > 2 -> 18.sp; else -> 24.sp },
                fontFamily = if (alphabet || label in listOf("ABC", "123", "funcs", "space", "←", "→")) FontFamily.SansSerif else MathFont,
                fontStyle = if (!alphabet && (label in listOf("x", "y", "a²", "aᵇ") || (label.length == 1 && label[0].isLetter()))) FontStyle.Italic else FontStyle.Normal)
        }
    }
}

private data class FunctionKey(val title: String, val source: String, val retreat: Int = 1)

@Composable
private fun FunctionKeypad(layout: CalculatorLayout, is3D: Boolean, onKey: (String) -> Unit, onTemplate: (String, Int) -> Unit) {
    var category by remember { mutableStateOf("Math") }
    val categories = listOf("Math", "LaTeX", "Calculus", "CAS", "Trig", "Matrix", "Stats", "More")
    val functions = when (category) {
        "Math" -> listOf(FunctionKey("a²", "^2", 0), FunctionKey("aᵇ", "^{}"), FunctionKey("√", "sqrt()"), FunctionKey("π", "pi", 0),
            FunctionKey("|x|", "abs()"), FunctionKey("ln", "ln()"), FunctionKey("log", "log10()"), FunctionKey(",", ",", 0))
        "LaTeX" -> listOf(FunctionKey("a/b", "\\frac{}{}", 3), FunctionKey("√", "\\sqrt{}"),
            FunctionKey("ⁿ√", "\\sqrt[3]{}"), FunctionKey("aᵇ", "^{}"), FunctionKey("aₙ", "_{}"),
            FunctionKey("sin", "\\sin()"), FunctionKey("θ", "\\theta", 0), FunctionKey("\\", "\\", 0))
        "Calculus" -> listOf(FunctionKey("∫ f dx", "integrate(,x)", 3), FunctionKey("∫ₐᵇ f dx", "integrate(,x,0,1)", 7), FunctionKey("d/dx", "diff(,x)", 3), FunctionKey("lim", "limit(,x,0)", 5),
            FunctionKey("Σ", "sum(,n,1,10)", 8), FunctionKey("Π", "product(,n,1,10)", 8), FunctionKey("series", "series(,x,0,6)", 7), FunctionKey("∂²/∂x²", "diff(,x,2)", 5))
        "CAS" -> listOf(FunctionKey("solve", "solve(,x)", 3), FunctionKey("factor", "factor()"), FunctionKey("expand", "expand()"), FunctionKey("simplify", "simplify()"),
            FunctionKey("fsolve", "fsolve(,x)", 3), FunctionKey("desolve", "desolve(,y)", 3), FunctionKey("apart", "partfrac()"), FunctionKey("assume", "assume()"))
        "Trig" -> listOf("sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh").map { FunctionKey(it, "$it()") }
        "Matrix" -> listOf(FunctionKey("[ ]", "[]"), FunctionKey("matrix", "[[,],[,]]", 7), FunctionKey("det", "det()"), FunctionKey("inverse", "inv()"),
            FunctionKey("transpose", "tran()"), FunctionKey("eigenvals", "eigenvals()"), FunctionKey("rref", "rref()"), FunctionKey("dot", "dot(,)", 2))
        "Stats" -> listOf(FunctionKey("linear fit", "linear_regression(x_1,y_1)", 0), FunctionKey("poly fit", "polynomial_regression(x_1,y_1,2)", 0),
            FunctionKey("exp fit", "exponential_regression(x_1,y_1)", 0), FunctionKey("log fit", "logarithmic_regression(x_1,y_1)", 0),
            FunctionKey("power fit", "power_regression(x_1,y_1)", 0), FunctionKey("mean", "mean()"), FunctionKey("median", "median()"), FunctionKey("stddev", "stddev()"))
        else -> listOf(FunctionKey("ln", "ln()"), FunctionKey("log", "log10()"), FunctionKey("eˣ", "exp()"), FunctionKey("|x|", "abs()"),
            FunctionKey("√", "sqrt()"), FunctionKey("n!", "!", 0), FunctionKey("∞", "infinity", 0), FunctionKey("i", "i", 0))
    }
    Column(Modifier.fillMaxWidth().height(244.dp).background(Color(0xFFF0F0F0)).border(.5.dp, Border).padding(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("123", color = Blue, fontSize = 15.sp, modifier = Modifier.size(48.dp).clickable { onKey("123") }.wrapContentSize())
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                categories.forEach { title -> Text(title, color = if (title == category) Color.White else Muted, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(if (title == category) Blue else Color.Transparent).clickable { category = title }.padding(horizontal = 12.dp, vertical = 12.dp)) }
            }
        }
        functions.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { item -> KeyTile(item.title, Modifier.weight(1f)) { onTemplate(item.source, item.retreat) } }
            }
        }
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            val contextual = if (layout == CalculatorLayout.Calculator) listOf("aᵇ", "√") else if (is3D) listOf("x", "y", "z") else listOf("x", "y")
            (contextual + listOf("←", "→", "space", "⌫", "↵")).forEach { label -> KeyTile(label, Modifier.weight(1f), gray = true, blue = label == "↵") { onKey(label) } }
        }
    }
}

/** Mathematical display is distinct from source so CAS syntax stays editable and portable. */
@Composable
private fun MathExpression(source: String, fontSize: Int = 24, showCursor: Boolean = false, cursor: Int = source.length) {
    val integral = parseIntegral(source)
    val display = remember(source) { mathDisplay(source) }
    if (integral != null) {
        val args = integral.second
        val variable = args.getOrElse(1) { "x" }.ifBlank { "x" }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("∫", fontSize = (fontSize * 1.9).sp, fontFamily = MathFont, lineHeight = (fontSize * 2.05).sp)
            if (args.size >= 4) {
                Column(Modifier.offset(x = (-3).dp).height((fontSize * 2.1).dp), verticalArrangement = Arrangement.SpaceBetween) {
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
                    if (body.isEmpty()) Text("□", fontSize = fontSize.sp, color = Color(0xFFBBBBBB), fontFamily = MathFont)
                    else PrettyText(body, fontSize)
                }
                PrettyText(" d$variable", fontSize)
                if (showCursor && cursor >= source.length) MathCaret(fontSize)
            }
        }
    } else if (!showCursor && display.hasFraction()) {
        RenderMathDisplay(display, fontSize)
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
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF111111)))
            RenderMathDisplay(display.denominator, childSize)
        }
    }
}

@Composable
private fun MathCaret(fontSize: Int) {
    Box(Modifier.padding(horizontal = .5.dp).width(1.dp).height((fontSize * 1.1).dp).background(Blue))
}

@Composable
private fun PrettyText(source: String, fontSize: Int) {
    val pretty = remember(source, fontSize) { prettyMath(source, fontSize) }
    Text(pretty, fontSize = fontSize.sp, fontFamily = MathFont, color = Color(0xFF111111), lineHeight = (fontSize * 1.4).sp, softWrap = false)
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
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text("Save graph") },
        text = { OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Graph title") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onSave(title.trim().ifEmpty { "Untitled Graph" }) }) { Text("Save", color = Blue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } },
    )
}

@Composable
private fun LibrarySheet(graphs: List<SavedGraph>, onDismiss: () -> Unit, onAction: (CalculatorAction) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("My graphs", fontSize = 23.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAction(CalculatorAction.New); onDismiss() }) { Text("+ New", color = Blue) }
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
                                Text(item.title, fontSize = 16.sp)
                                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.updatedAt)), fontSize = 11.sp, color = Muted)
                            }
                            IconButton(onClick = { onAction(CalculatorAction.DeleteGraph(item.id)) }) { CalcIcon(IconKind.Delete, Color(0xFFAAAAAA), Modifier.size(17.dp)) }
                        }
                        HorizontalDivider(color = Border)
                    }
                }
            }
            TextButton(onClick = { onAction(CalculatorAction.ImportDocument); onDismiss() }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Import graph file", color = Blue) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSheet(state: CalculatorUiState, onDismiss: () -> Unit, onAction: (CalculatorAction) -> Unit) {
    val ranges = remember(state.bounds) {
        with(state.bounds) { mutableStateListOf(xMin?.toString().orEmpty(), xMax?.toString().orEmpty(), yMin?.toString().orEmpty(), yMax?.toString().orEmpty(),
            zMin?.toString().orEmpty(), zMax?.toString().orEmpty(), tMin.toString(), tMax.toString(), uMin.toString(), uMax.toString(), vMin.toString(), vMax.toString()) }
    }
    var showParameters by remember { mutableStateOf(false) }
    var boundsError by remember { mutableStateOf<String?>(null) }
    var precision by remember(state.precisionDigits) { mutableStateOf(state.precisionDigits.toString()) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 23.dp).navigationBarsPadding()) {
            Text("Graph settings", fontSize = 23.sp, modifier = Modifier.padding(bottom = 15.dp))
            SettingRow("Grid", "Show coordinate grid", state.showGrid) { onAction(CalculatorAction.SetGrid(it)) }
            SettingRow("Axes", "Show axes and labels", state.showAxes) { onAction(CalculatorAction.SetAxes(it)) }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text("Angle measure", fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Radians" to false, "Degrees" to true).forEach { (label, degrees) ->
                    FilterChip(selected = state.degreeMode == degrees, onClick = { onAction(CalculatorAction.SetDegreeMode(degrees)) }, label = { Text(label) })
                }
            }
            HorizontalDivider(Modifier.padding(bottom = 12.dp))
            Text("Computer algebra", fontSize = 15.sp)
            SettingRow("Complex results", "Allow complex solutions", state.complexMode) { onAction(CalculatorAction.SetComplexMode(it)) }
            Row(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = precision, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) precision = it },
                    label = { Text("Precision digits", fontSize = 12.sp) }, supportingText = { Text("10–1000 digits", fontSize = 10.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, textStyle = TextStyle(fontSize = 14.sp), modifier = Modifier.weight(1f))
                TextButton(onClick = { precision.toIntOrNull()?.takeIf { it in 10..1000 }?.let { onAction(CalculatorAction.SetPrecisionDigits(it)) } },
                    enabled = (precision.toIntOrNull() ?: 0) in 10..1000) { Text("Apply", color = Blue) }
            }
            HorizontalDivider(Modifier.padding(bottom = 14.dp))
            Text("Viewing window", fontSize = 15.sp)
            Text("Leave both bounds blank for the default view.", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 7.dp))
            repeat(if (state.is3D) 3 else 2) { axis ->
                BoundFieldPair("${"xyz"[axis]}", ranges[axis * 2], ranges[axis * 2 + 1],
                    { ranges[axis * 2] = it }, { ranges[axis * 2 + 1] = it })
            }
            TextButton(onClick = { showParameters = !showParameters }, contentPadding = PaddingValues(0.dp)) {
                Text("${if (showParameters) "▾" else "▸"} Parametric ranges", color = Blue, fontSize = 12.sp)
            }
            if (showParameters) repeat(3) { parameter ->
                val index = 6 + parameter * 2
                BoundFieldPair("${"tuv"[parameter]}", ranges[index], ranges[index + 1], { ranges[index] = it }, { ranges[index + 1] = it })
            }
            boundsError?.let { Text(it, fontSize = 11.sp, color = Color(0xFFB8532F), modifier = Modifier.padding(top = 5.dp)) }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onAction(CalculatorAction.SetBounds(GraphBounds())); boundsError = null }) { Text("Reset bounds", fontSize = 12.sp, color = Muted) }
                Button(onClick = {
                    val values = ranges.map { it.trim().takeIf(String::isNotEmpty)?.toDoubleOrNull() }
                    val malformed = ranges.indices.any { ranges[it].isNotBlank() && (values[it] == null || values[it]?.isFinite() != true) }
                    val missing = (0..2).any { (values[it * 2] == null) != (values[it * 2 + 1] == null) } || (6..11).any { values[it] == null }
                    val unordered = (0..5).any { pair -> val a = values[pair * 2]; val b = values[pair * 2 + 1]; a != null && b != null && a >= b }
                    if (malformed || missing || unordered) boundsError = "Enter finite minimum and maximum values, with minimum less than maximum."
                    else {
                        onAction(CalculatorAction.SetBounds(GraphBounds(values[0], values[1], values[2], values[3], values[4], values[5],
                            values[6]!!, values[7]!!, values[8]!!, values[9]!!, values[10]!!, values[11]!!)))
                        boundsError = null
                    }
                }) { Text("Apply", fontSize = 12.sp) }
            }
            Text("View", fontSize = 15.sp, modifier = Modifier.padding(bottom = 4.dp))
            Text(if (state.is3D) "3D: drag to orbit; pinch to zoom. Use two fingers to pan." else "2D: drag to pan; pinch to zoom. Tap a curve to inspect a point.", fontSize = 12.sp, lineHeight = 18.sp, color = Muted)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun BoundFieldPair(axis: String, minimum: String, maximum: String, onMin: (String) -> Unit, onMax: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 5.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        OutlinedTextField(value = minimum, onValueChange = onMin, label = { Text("$axis min", fontSize = 12.sp) }, placeholder = { Text("Auto", fontSize = 12.sp) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), textStyle = TextStyle(fontSize = 14.sp), modifier = Modifier.weight(1f))
        OutlinedTextField(value = maximum, onValueChange = onMax, label = { Text("$axis max", fontSize = 12.sp) }, placeholder = { Text("Auto", fontSize = 12.sp) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), textStyle = TextStyle(fontSize = 14.sp), modifier = Modifier.weight(1f))
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().heightIn(max = 610.dp).verticalScroll(rememberScrollState()).padding(horizontal = 23.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text("Calculator guide", fontSize = 23.sp)
            Text("Edit mathematics directly: tap a numerator, denominator, or exponent to place the cursor there. Division creates a fraction; use the arrow keys to move between slots or out of a fraction. Hold and drag to select. Typing sin, cos, sqrt, or other function names opens an argument pair automatically. The keypad adapts to calculator, 2D, and 3D modes. Tap an exact result to switch to its decimal value; long-press to copy it.", fontSize = 13.sp, color = Muted, lineHeight = 19.sp)
            Text("Symbolic mathematics is powered by Giac/Xcas and runs on your device. Use exact fractions and assumptions when you need symbolic results.", fontSize = 12.sp, color = Muted, lineHeight = 18.sp)
            listOf(
                "2D graphs" to "y=sin(x)\nx^2+y^2=9\ny<x^2\na=2\ny=a*x^2",
                "3D graphs" to "z=x^2+y^2\nx^2+y^2+z^2=9\nz=sin(x)*cos(y)",
                "Symbolic calculus" to "integrate(x^2,x)\nintegrate(87*x,x,0,100)\ndiff(sin(x),x)\nlimit(sin(x)/x,x,0)",
                "Algebra" to "solve(x^2-4=0,x)\nfactor(x^3-1)\nexpand((x+1)^5)\nsimplify((x^2-1)/(x-1))",
                "Assumptions" to "assume(x>0)\nsimplify(sqrt(x^2))\npurge(x)",
                "Matrices and complex numbers" to "det([[1,2],[3,4]])\ninv([[1,2],[3,4]])\n(2+3*i)^2\nsqrt(-1)",
                "Tables and regressions" to "Use + → Table to enter points.\nlinear_regression(x_1,y_1)\npolynomial_regression(x_1,y_1,2)\ny_1~m*x_1+b",
                "Math LaTeX" to "Type or paste \\frac{1}{2}, \\sqrt{x}, x^{2}, x_{1}, or \\sin(x). Use Functions → LaTeX for templates. Fractions, roots, powers, subscripts, and common math commands are rendered directly in the editable equation.",
                "Import and export" to "Use the top menu to save a graph as PNG or PDF.\nUse + → Import table for two-column CSV, TSV, or JSON.\nCSV/TSV may include an x,y header. JSON accepts [[x,y],…], [{\"x\":1,\"y\":2},…], or x and y arrays.\nUse the export icon beside a table to save just that table.\nImports support up to 2,000 rows and 1 MB.",
                "Fullscreen" to "Use the corner icon on the graph or calculator toolbar to expand that view. Your choice is remembered when you reopen the app.\nCalculator fullscreen opens with the keypad ready for typing. Keyboard visibility is remembered when you reopen the app. Tap anywhere in an equation to place the cursor. Back hides the keypad, then returns to the split view.",
            ).forEach { (heading, examples) ->
                Text(heading, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(examples, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp)).padding(12.dp))
            }
            Spacer(Modifier.height(15.dp))
        }
    }
}

private enum class IconKind { Folder, More, Wrench, Plus, Undo, Redo, Settings, Collapse, Expand, Delete, Backspace, Return, Speaker, Keyboard, Fullscreen, Export }

@Composable
private fun ToolIcon(kind: IconKind, description: String, color: Color = Muted, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics { contentDescription = description }) {
        CalcIcon(kind, color, Modifier.size(23.dp))
    }
}

@Composable
private fun GraphTool(kind: IconKind, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(48.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFEEEEEE)).border(.6.dp, Border, RoundedCornerShape(5.dp))
        .clickable(onClick = onClick).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        CalcIcon(kind, Muted, Modifier.size(20.dp))
    }
}

@Composable
private fun CalcIcon(kind: IconKind, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * .08f, cap = StrokeCap.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = .08f) = drawLine(color, Offset(x1 * w, y1 * h), Offset(x2 * w, y2 * h), w * width, StrokeCap.Round)
        fun path(points: List<Pair<Float, Float>>, close: Boolean = false, fill: Boolean = false) {
            val p = Path().apply {
                points.forEachIndexed { i, point -> if (i == 0) moveTo(point.first * w, point.second * h) else lineTo(point.first * w, point.second * h) }
                if (close) close()
            }
            if (fill) drawPath(p, color) else drawPath(p, color, style = stroke)
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
                drawCircle(color, w * .44f, style = Stroke(w * .065f))
                listOf(.3f, .5f, .7f).forEach { x -> drawCircle(color, w * .045f, Offset(w * x, h * .5f)) }
            }
            IconKind.Plus -> { line(.2f, .5f, .8f, .5f, .13f); line(.5f, .2f, .5f, .8f, .13f) }
            IconKind.Delete -> { line(.2f, .2f, .8f, .8f, .12f); line(.8f, .2f, .2f, .8f, .12f) }
            IconKind.Collapse, IconKind.Expand -> {
                val invert = kind == IconKind.Expand
                val y: (Float) -> Float = { if (invert) 1f - it else it }
                path(listOf(.2f to y(.25f), .5f to y(.53f), .8f to y(.25f)))
                path(listOf(.2f to y(.48f), .5f to y(.76f), .8f to y(.48f)))
            }
            IconKind.Undo, IconKind.Redo -> {
                val flip = kind == IconKind.Redo
                fun x(v: Float) = (if (flip) 1f - v else v) * w
                val p = Path().apply { moveTo(x(.15f), h * .54f); cubicTo(x(.35f), h * .17f, x(.66f), h * .15f, x(.9f), h * .49f); cubicTo(x(.60f), h * .30f, x(.39f), h * .37f, x(.28f), h * .60f) }
                drawPath(p, color)
                path(listOf((if (flip) .85f else .15f) to .27f, (if (flip) .85f else .15f) to .68f, (if (flip) .48f else .52f) to .68f), true, true)
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
                p.close(); drawPath(p, color)
                drawCircle(Color(0xFFF3F3F3), w * .17f)
                drawCircle(color, w * .07f)
            }
            IconKind.Wrench -> {
                line(.24f, .78f, .64f, .39f, .20f)
                drawCircle(color, w * .25f, Offset(w * .67f, h * .31f))
                path(listOf(.62f to .06f, .57f to .32f, .78f to .43f, .95f to .25f, 1f to .01f), true, true)
                // The open jaw is cut out in the tool tile's background color.
                val jaw = Path().apply { moveTo(w * .67f, 0f); lineTo(w, 0f); lineTo(w * .86f, h * .27f); lineTo(w * .64f, h * .31f); close() }
                drawPath(jaw, Color(0xFFEEEEEE))
                drawCircle(Color(0xFFEEEEEE), w * .04f, Offset(w * .24f, h * .78f))
            }
            IconKind.Backspace -> {
                path(listOf(.08f to .50f, .34f to .20f, .91f to .20f, .91f to .80f, .34f to .80f), true, true)
                drawLine(Color(0xFFD0D0D0), Offset(w * .49f, h * .35f), Offset(w * .74f, h * .65f), w * .075f)
                drawLine(Color(0xFFD0D0D0), Offset(w * .74f, h * .35f), Offset(w * .49f, h * .65f), w * .075f)
            }
            IconKind.Return -> {
                val p = Path().apply { moveTo(w * .88f, h * .27f); lineTo(w * .88f, h * .46f); quadraticTo(w * .88f, h * .68f, w * .65f, h * .68f); lineTo(w * .20f, h * .68f) }
                drawPath(p, color, style = Stroke(w * .09f))
                path(listOf(.32f to .53f, .15f to .68f, .32f to .83f), true, true)
            }
            IconKind.Speaker -> {
                path(listOf(.08f to .35f, .28f to .35f, .5f to .15f, .5f to .85f, .28f to .65f, .08f to .65f), true, true)
                val p = Path().apply { moveTo(w * .65f, h * .32f); quadraticTo(w * .84f, h * .5f, w * .65f, h * .68f) }
                drawPath(p, color, style = Stroke(w * .065f))
                val outer = Path().apply { moveTo(w * .78f, h * .2f); quadraticTo(w * 1.08f, h * .5f, w * .78f, h * .8f) }
                drawPath(outer, color, style = Stroke(w * .065f))
            }
        }
    }
}
