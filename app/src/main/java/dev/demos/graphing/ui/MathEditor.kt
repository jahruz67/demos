package dev.demos.graphing.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.Text
import kotlinx.coroutines.awaitCancellation
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class MathSlot(val start: Int, val end: Int, val emptyArgument: Boolean = false)
internal fun EditableMath.slots(): List<MathSlot> = when (this) {
    is EditableMath.Text -> listOf(MathSlot(start, end, start == end))
    is EditableMath.Row -> children.flatMap { it.slots() }
    is EditableMath.Fraction -> listOf(MathSlot(start, start)) + top.slots() + bottom.slots() + MathSlot(end, end)
    is EditableMath.Script -> body.slots() + MathSlot(end, end)
    is EditableMath.Root -> listOf(MathSlot(start, start)) + index?.slots().orEmpty() + body.slots() + MathSlot(end, end)
}

internal fun EditableMath.caretOffsets(source: String): List<Int> = slots().flatMap { slot ->
    val display = LatexVisualTransformation.filter(AnnotatedString(source.substring(slot.start, slot.end)))
    (slot.start..slot.end).filter { offset ->
        val local = offset - slot.start
        display.offsetMapping.transformedToOriginal(display.offsetMapping.originalToTransformed(local)) == local
    }
}.distinct().sorted()

private data class MathHit(val offset: Int, val position: Offset, val height: Float, val bounds: Rect)

/** Native editing and clipboard with a custom keypad and two-dimensional decoration. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun MathEditor(value: TextFieldValue, selected: Boolean, number: Int, modifier: Modifier = Modifier,
    onInput: (TextFieldValue) -> Unit, onFocus: (Boolean) -> Unit, onSelect: () -> Unit,
    fontSize: Int = 26, minimumHeight: androidx.compose.ui.unit.Dp = 48.dp, placeholder: String = "Type an expression") {
    val tree = remember(value.text) { editableMath(value.text) }
    val slots = remember(tree) { tree.slots() }
    val owner = slots.firstOrNull { value.selection.end in it.start..it.end }
        ?: slots.minByOrNull { minOf(abs(value.selection.end - it.start), abs(value.selection.end - it.end)) }
    val focus = remember { FocusRequester() }
    val hits = remember { mutableStateMapOf<MathSlot, List<MathHit>>() }
    val latestHits by rememberUpdatedState(hits)
    val latestValue by rememberUpdatedState(value)
    val latestInput by rememberUpdatedState(onInput)
    val latestSelect by rememberUpdatedState(onSelect)
    val scroll = rememberScrollState()
    val clipboard = LocalClipboardManager.current
    val toolbar = LocalTextToolbar.current
    var viewportOrigin by remember { mutableStateOf(Offset.Zero) }
    var caretVisible by remember { mutableStateOf(true) }
    var showHandles by remember { mutableStateOf(false) }
    var showToolbar by remember { mutableStateOf(false) }
    var ownsToolbar by remember { mutableStateOf(false) }
    var handleDragPoint by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(selected) { if (selected) focus.requestFocus() }
    LaunchedEffect(selected, value.selection, value.text) {
        caretVisible = true
        while (selected) { kotlinx.coroutines.delay(530); caretVisible = !caretVisible }
    }
    fun choose(offset: Int, extend: Boolean = false) {
        latestSelect()
        focus.requestFocus()
        latestInput(latestValue.copy(selection = if (extend) TextRange(latestValue.selection.start, offset) else TextRange(offset)))
    }
    fun nearest(point: Offset): Int? {
        val all = latestHits.values.flatten()
        if (all.isEmpty()) return 0
        // A raised exponent or fraction slot owns touches inside its text bounds;
        // a nearby structural exit must not steal them based on distance alone.
        val inside = all.filter { it.bounds.contains(point) }
        if (inside.isEmpty()) {
            if (point.x >= all.maxOf { it.bounds.right }) return latestValue.text.length
            if (point.x <= all.minOf { it.bounds.left }) return 0
        }
        return (inside.ifEmpty { all }).minByOrNull {
            val dy = point.y - it.position.y - it.height / 2
            val dx = point.x - it.position.x
            dx * dx + dy * dy * 4
        }?.offset
    }
    fun hit(offset: Int): MathHit? = hits.values.flatten().minByOrNull { abs(it.offset - offset) }
    fun wordAt(offset: Int): TextRange {
        val source = latestValue.text
        if (source.isEmpty()) return TextRange.Zero
        val nonempty = editableMath(source).slots().filter { it.start < it.end }
        if (nonempty.isEmpty()) return TextRange(offset.coerceIn(0, source.length))
        val slot = nonempty.firstOrNull { offset in it.start..it.end }
            ?: nonempty.minByOrNull { minOf(abs(offset - it.start), abs(offset - it.end)) }
        val a = slot?.start ?: offset.coerceAtMost(source.lastIndex)
        val b = slot?.end ?: (a + 1)
        val at = offset.coerceIn(a, b - 1)
        var start = at
        var end = at
        fun word(c: Char) = c.isLetterOrDigit() || c == '_'
        while (start > a && word(source[start - 1])) start--
        while (end < b && word(source[end])) end++
        return if (start == end) TextRange(at, at + 1) else TextRange(start, end)
    }
    val startHit = hit(value.selection.start)
    val endHit = hit(value.selection.end)
    LaunchedEffect(selected, showToolbar, value.selection, value.text, startHit, endHit) {
        if (!selected || !showToolbar) {
            if (ownsToolbar) { toolbar.hide(); ownsToolbar = false }
            return@LaunchedEffect
        }
        val a = startHit ?: return@LaunchedEffect
        val b = endHit ?: return@LaunchedEffect
        val selection = value.selection
        ownsToolbar = true
        toolbar.showMenu(
            Rect(minOf(a.position.x, b.position.x), minOf(a.position.y, b.position.y),
                maxOf(a.position.x, b.position.x) + 1f, maxOf(a.position.y + a.height, b.position.y + b.height)),
            onCopyRequested = if (selection.collapsed) null else ({
                clipboard.setText(AnnotatedString(value.text.substring(selection.min, selection.max)))
                showToolbar = false
            }),
            onPasteRequested = clipboard.getText()?.let { pasted -> ({
                val next = value.text.replaceRange(selection.min, selection.max, pasted.text)
                latestInput(TextFieldValue(next, TextRange(selection.min + pasted.length)))
                showToolbar = false
            }) },
            onCutRequested = if (selection.collapsed) null else ({
                clipboard.setText(AnnotatedString(value.text.substring(selection.min, selection.max)))
                latestInput(MathInputRules.backspace(value))
                showToolbar = false
            }),
            onSelectAllRequested = if (value.text.isEmpty()) null else ({
                latestInput(value.copy(selection = TextRange(0, value.text.length)))
                showHandles = true
            }),
        )
    }
    DisposableEffect(toolbar) { onDispose { if (ownsToolbar) toolbar.hide() } }
    // showKeyboardOnFocus only prevents the initial request. Block the input
    // session itself so taps, selection and refocusing cannot open the tablet IME.
    InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
        BasicTextField(value = value, onValueChange = onInput, singleLine = true,
            textStyle = TextStyle(fontSize = 26.sp, color = Color.Transparent), cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
            modifier = modifier.heightIn(min = minimumHeight).focusRequester(focus).onFocusChanged { onFocus(it.isFocused) }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                        Key.Backspace -> { onInput(MathInputRules.backspace(value)); true }
                        Key.DirectionLeft, Key.DirectionRight -> {
                            val offsets = tree.caretOffsets(value.text)
                            val next = if (event.key == Key.DirectionLeft) offsets.lastOrNull { it < value.selection.end }
                                else offsets.firstOrNull { it > value.selection.end }
                            if (next != null) choose(next, event.isShiftPressed)
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            val current = hits[owner]?.minByOrNull { abs(it.offset - value.selection.end) }
                            val next = current?.let { here -> hits.values.flatten().filter {
                                if (event.key == Key.DirectionUp) it.position.y + it.height / 2 < here.position.y
                                else it.position.y > here.position.y + here.height / 2
                            }.minByOrNull { abs(it.position.x - here.position.x) + abs(it.position.y - here.position.y) * 2 } }
                            if (next != null) choose(next.offset, event.isShiftPressed)
                            true
                        }
                        else -> false
                    }
                }.semantics { contentDescription = "Equation $number" },
            decorationBox = { field ->
                Box(Modifier.fillMaxWidth().onGloballyPositioned { viewportOrigin = it.positionInRoot() }) {
                    // Keep one native editing and semantics surface, with no second visible line.
                    Box(Modifier.size(1.dp).alpha(0f)) { field() }
                    // Gesture coordinates stay in the viewport. Hit positions already include
                    // horizontal scrolling, so never subtract the scroll offset a second time.
                    Box(Modifier.fillMaxWidth().pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            var finished = false
                            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                waitForUpOrCancellation().also { finished = true }
                            }
                            if (finished) {
                                if (up != null) {
                                    up.consume()
                                    showToolbar = false
                                    nearest(viewportOrigin + up.position)?.let { choose(it) }
                                    showHandles = true
                                }
                            } else {
                                val offset = nearest(viewportOrigin + down.position) ?: 0
                                val word = wordAt(offset)
                                latestSelect()
                                focus.requestFocus()
                                latestInput(latestValue.copy(selection = word))
                                showHandles = true
                                showToolbar = false
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) { change.consume(); break }
                                    if (change.position != change.previousPosition) {
                                        nearest(viewportOrigin + change.position)?.let { end ->
                                            val range = if (end < word.start) TextRange(word.end, end)
                                                else TextRange(word.start, maxOf(word.end, end))
                                            latestInput(latestValue.copy(selection = range))
                                        }
                                    }
                                    change.consume()
                                }
                                showToolbar = true
                            }
                        }
                    }) {
                      Box(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(vertical = 6.dp, horizontal = 2.dp)) {
                        if (value.text.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, color = Color(0xFFAAAAAA), fontSize = 18.sp)
                        MathEditorNode(tree, value, fontSize, owner, selected && caretVisible, hits)
                      }
                    }
                    if (selected && showHandles) {
                        fun dragStart(point: MathHit) {
                            showToolbar = false
                            handleDragPoint = point.position + Offset(0f, point.height / 2)
                        }
                        if (!value.selection.collapsed) startHit?.let { point ->
                            MathSelectionHandle(point, viewportOrigin, "Selection start",
                                onStart = { dragStart(point) }, onEnd = { showToolbar = true }, onDrag = { delta ->
                                    handleDragPoint += delta
                                    nearest(handleDragPoint)?.let { latestInput(latestValue.copy(selection = TextRange(it, latestValue.selection.end))) }
                                })
                        }
                        endHit?.let { point ->
                            MathSelectionHandle(point, viewportOrigin, if (value.selection.collapsed) "Move cursor" else "Selection end",
                                onStart = { dragStart(point) }, onEnd = { showToolbar = true }, onDrag = { delta ->
                                    handleDragPoint += delta
                                    nearest(handleDragPoint)?.let { offset ->
                                        val selection = latestValue.selection
                                        latestInput(latestValue.copy(selection = if (selection.collapsed) TextRange(offset) else TextRange(selection.start, offset)))
                                    }
                                })
                        }
                    }
                }
            })
    }
}

/** Results share the editor's fraction, root and script layout, without editing chrome. */
@Composable
internal fun StructuredMath(source: String, fontSize: Int) {
    val tree = remember(source) { editableMath(source) }
    val hits = remember(source) { mutableMapOf<MathSlot, List<MathHit>>() }
    MathEditorNode(tree, TextFieldValue(source), fontSize, null, false, hits)
}

@Composable
private fun MathSelectionHandle(point: MathHit, origin: Offset, label: String,
    onStart: () -> Unit, onEnd: () -> Unit, onDrag: (Offset) -> Unit) {
    val density = LocalDensity.current
    val halfWidth = with(density) { 22.dp.toPx() }
    val currentStart by rememberUpdatedState(onStart)
    val currentEnd by rememberUpdatedState(onEnd)
    val currentDrag by rememberUpdatedState(onDrag)
    Popup(alignment = Alignment.TopStart,
        offset = IntOffset((point.position.x - origin.x - halfWidth).roundToInt(), (point.position.y + point.height - origin.y).roundToInt()),
        properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        Canvas(Modifier.size(44.dp).semantics { contentDescription = label }.pointerInput(Unit) {
            detectDragGestures(onDragStart = { currentStart() }, onDragEnd = { currentEnd() },
                onDragCancel = { currentEnd() }, onDrag = { change, delta -> change.consume(); currentDrag(delta) })
        }) {
            val blue = Color(0xFF2D70DA)
            drawLine(blue, Offset(size.width / 2, 0f), Offset(size.width / 2, 8.dp.toPx()), 2.dp.toPx())
            drawCircle(blue, 7.dp.toPx(), Offset(size.width / 2, 8.dp.toPx()))
        }
    }
}

@Composable
private fun MathEditorNode(node: EditableMath, value: TextFieldValue, size: Int, owner: MathSlot?, caret: Boolean,
    hits: MutableMap<MathSlot, List<MathHit>>) {
    fun childSize() = (size * .8).toInt().coerceAtLeast(13)
    @Composable fun child(part: EditableMath, font: Int = size) { MathEditorNode(part, value, font, owner, caret, hits) }
    @Composable fun entry() { MathEditorLeaf(MathSlot(node.start, node.start), value, size, owner, caret, hits) }
    @Composable fun exit() { MathEditorLeaf(MathSlot(node.end, node.end), value, size, owner, caret, hits) }
    when (node) {
        is EditableMath.Text -> MathEditorLeaf(MathSlot(node.start, node.end, node.start == node.end), value, size, owner, caret, hits)
        is EditableMath.Row -> Row(verticalAlignment = Alignment.CenterVertically) { node.children.forEach { child(it) } }
        is EditableMath.Fraction -> Row(verticalAlignment = Alignment.CenterVertically) {
            entry()
            Column(Modifier.width(IntrinsicSize.Max).padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                child(node.top, childSize())
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF202020)))
                child(node.bottom, childSize())
            }
            exit()
        }
        is EditableMath.Script -> Row(Modifier.padding(bottom = if (node.raised) (size * .65).dp else 0.dp,
            top = if (!node.raised) (size * .5).dp else 0.dp), verticalAlignment = Alignment.CenterVertically) { child(node.body, childSize()); exit() }
        is EditableMath.Root -> Row(verticalAlignment = Alignment.CenterVertically) {
            entry()
            node.index?.let { index -> Box(Modifier.padding(bottom = (size * .7).dp)) { child(index, childSize()) } }
            Text("√", fontFamily = FontFamily.Serif, fontSize = (size * 1.3).sp)
            Column(Modifier.width(IntrinsicSize.Max)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF202020)))
                child(node.body)
            }
            exit()
        }
    }
}

@Composable
private fun MathEditorLeaf(slot: MathSlot, value: TextFieldValue, size: Int, owner: MathSlot?, caret: Boolean,
    hits: MutableMap<MathSlot, List<MathHit>>) {
    val raw = value.text.substring(slot.start, slot.end)
    val transformed = remember(raw) { LatexVisualTransformation.filter(AnnotatedString(raw)) }
    val shown = if (raw.isEmpty()) AnnotatedString(if (slot.emptyArgument && value.text.isNotEmpty()) "□" else "\u200b") else transformed.text
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val active = owner == slot
    val currentValue by rememberUpdatedState(value)
    fun displayOffset(offset: Int) = transformed.offsetMapping.originalToTransformed((offset - slot.start).coerceIn(0, raw.length))
    LaunchedEffect(layout, origin, slot, hits) {
        layout?.let { result ->
            val points = (slot.start..slot.end).filter { offset ->
                transformed.offsetMapping.transformedToOriginal(displayOffset(offset)) == offset - slot.start
            }.map { offset ->
                val rect = result.getCursorRect(displayOffset(offset))
                MathHit(offset, origin + rect.topLeft, rect.height,
                    Rect(origin, androidx.compose.ui.geometry.Size(result.size.width.coerceAtLeast(2).toFloat(), result.size.height.toFloat())))
            }
            hits[slot] = points
        }
    }
    DisposableEffect(slot, hits) { onDispose { hits.remove(slot) } }
    Box(Modifier.widthIn(min = 2.dp).onGloballyPositioned { origin = it.positionInRoot() }) {
        Text(shown, fontSize = size.sp, fontFamily = FontFamily.Serif, color = if (raw.isEmpty() && slot.emptyArgument) Color(0xFFAAAAAA) else Color(0xFF202020),
            softWrap = false, onTextLayout = { layout = it })
        Canvas(Modifier.matchParentSize()) {
            val result = layout ?: return@Canvas
            val selection = currentValue.selection
            if (!selection.collapsed && selection.max > slot.start && selection.min < slot.end) {
                val path = result.getPathForRange(displayOffset(selection.min), displayOffset(selection.max))
                drawPath(path, Color(0xFF2D70DA).copy(alpha = .25f))
            }
            if (active && caret && selection.collapsed) {
                val rect = result.getCursorRect(displayOffset(selection.end))
                drawLine(Color(0xFF2D70DA), rect.topLeft, rect.bottomLeft, 1.5.dp.toPx())
            }
        }
    }
}
