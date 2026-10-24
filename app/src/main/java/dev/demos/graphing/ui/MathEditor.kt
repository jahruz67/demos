package dev.demos.graphing.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.InterceptPlatformTextInput
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
import androidx.compose.material3.Text
import kotlinx.coroutines.awaitCancellation
import kotlin.math.abs

internal data class MathSlot(val start: Int, val end: Int, val emptyArgument: Boolean = false)
internal fun EditableMath.slots(): List<MathSlot> = when (this) {
    is EditableMath.Text -> listOf(MathSlot(start, end, start == end))
    is EditableMath.Row -> children.flatMap { it.slots() }
    is EditableMath.Fraction -> top.slots() + bottom.slots() + MathSlot(end, end)
    is EditableMath.Script -> body.slots() + MathSlot(end, end)
    is EditableMath.Root -> index?.slots().orEmpty() + body.slots() + MathSlot(end, end)
}

private data class MathHit(val offset: Int, val position: Offset, val height: Float)

/** Native editing and clipboard with a custom keypad and two-dimensional decoration. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun MathEditor(value: TextFieldValue, selected: Boolean, number: Int, modifier: Modifier = Modifier,
    onInput: (TextFieldValue) -> Unit, onFocus: (Boolean) -> Unit, onSelect: () -> Unit) {
    val tree = remember(value.text) { editableMath(value.text) }
    val slots = remember(tree) { tree.slots() }
    val owner = slots.firstOrNull { value.selection.end in it.start..it.end }
        ?: slots.minByOrNull { minOf(abs(value.selection.end - it.start), abs(value.selection.end - it.end)) }
    val focus = remember { FocusRequester() }
    val hits = remember(value.text) { mutableMapOf<MathSlot, List<MathHit>>() }
    val latestValue by rememberUpdatedState(value)
    val latestInput by rememberUpdatedState(onInput)
    val latestSelect by rememberUpdatedState(onSelect)
    val scroll = rememberScrollState()
    var viewportOrigin by remember { mutableStateOf(Offset.Zero) }
    var caretVisible by remember { mutableStateOf(true) }
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
    fun nearest(point: Offset): Int? = hits.values.flatten().minByOrNull {
        val dy = point.y - it.position.y - it.height / 2
        val dx = point.x - it.position.x
        dx * dx + dy * dy * 4
    }?.offset
    // showKeyboardOnFocus only prevents the initial request. Block the input
    // session itself so taps, selection and refocusing cannot open the tablet IME.
    InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
        BasicTextField(value = value, onValueChange = onInput, singleLine = true,
            textStyle = TextStyle(fontSize = 26.sp, color = Color.Transparent), cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
            modifier = modifier.heightIn(min = 48.dp).focusRequester(focus).onFocusChanged { onFocus(it.isFocused) }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                        Key.Backspace -> { onInput(MathInputRules.backspace(value)); true }
                        Key.DirectionLeft, Key.DirectionRight -> {
                            val offsets = slots.flatMap { (it.start..it.end).toList() }.distinct().sorted()
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
                    Box(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(vertical = 6.dp, horizontal = 2.dp)
                        .pointerInput(tree) {
                            detectDragGesturesAfterLongPress(onDragStart = { point ->
                                nearest(viewportOrigin + point - Offset(scroll.value.toFloat(), 0f))?.let { choose(it) }
                            }, onDrag = { change, _ ->
                                change.consume()
                                nearest(viewportOrigin + change.position - Offset(scroll.value.toFloat(), 0f))?.let { choose(it, true) }
                            })
                        }) {
                        if (value.text.isEmpty()) Text("Type an expression", color = Color(0xFFAAAAAA), fontSize = 18.sp)
                        MathEditorNode(tree, value, 26, owner, selected && caretVisible, hits,
                            onChoose = { choose(it) })
                    }
                }
            })
    }
}

@Composable
private fun MathEditorNode(node: EditableMath, value: TextFieldValue, size: Int, owner: MathSlot?, caret: Boolean,
    hits: MutableMap<MathSlot, List<MathHit>>, onChoose: (Int) -> Unit) {
    fun childSize() = (size * .8).toInt().coerceAtLeast(13)
    @Composable fun child(part: EditableMath, font: Int = size) { MathEditorNode(part, value, font, owner, caret, hits, onChoose) }
    @Composable fun exit() { MathEditorLeaf(MathSlot(node.end, node.end), value, size, owner, caret, hits, onChoose) }
    when (node) {
        is EditableMath.Text -> MathEditorLeaf(MathSlot(node.start, node.end, node.start == node.end), value, size, owner, caret, hits, onChoose)
        is EditableMath.Row -> Row(verticalAlignment = Alignment.CenterVertically) { node.children.forEach { child(it) } }
        is EditableMath.Fraction -> Row(verticalAlignment = Alignment.CenterVertically) {
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
    hits: MutableMap<MathSlot, List<MathHit>>, onChoose: (Int) -> Unit) {
    val raw = value.text.substring(slot.start, slot.end)
    val transformed = remember(raw) { LatexVisualTransformation.filter(AnnotatedString(raw)) }
    val shown = if (raw.isEmpty()) AnnotatedString(if (slot.emptyArgument && value.text.isNotEmpty()) "□" else "\u200b") else transformed.text
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val active = owner == slot
    val currentChoose by rememberUpdatedState(onChoose)
    val currentValue by rememberUpdatedState(value)
    fun displayOffset(offset: Int) = transformed.offsetMapping.originalToTransformed((offset - slot.start).coerceIn(0, raw.length))
    LaunchedEffect(layout, origin, slot, value.selection, active) {
        layout?.let { result ->
            val points = (slot.start..slot.end).map { offset ->
                val rect = result.getCursorRect(displayOffset(offset))
                MathHit(offset, origin + rect.topLeft, rect.height)
            }
            hits[slot] = points
        }
    }
    DisposableEffect(slot) { onDispose { hits.remove(slot) } }
    Box(Modifier.widthIn(min = 2.dp).onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(slot, shown) { detectTapGestures(onTap = { point ->
            val offset = layout?.getOffsetForPosition(point) ?: 0
            currentChoose(slot.start + if (raw.isEmpty()) 0 else transformed.offsetMapping.transformedToOriginal(offset.coerceIn(0, transformed.text.length)))
        }) }) {
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
