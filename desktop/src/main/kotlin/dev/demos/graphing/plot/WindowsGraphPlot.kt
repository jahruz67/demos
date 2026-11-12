package dev.demos.graphing.plot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import dev.demos.graphing.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.*
import java.util.Locale

@Composable
fun GraphPlot(expressions: List<PlotExpression>, is3D: Boolean, settings: PlotSettings = PlotSettings(),
    modifier: Modifier = Modifier, showControls: Boolean = true) {
    val imageSources = expressions.mapNotNull { parseImageData(it.text) }
    val imageUris = imageSources.map { it.uri }.distinct()
    val loadImage = rememberImageLoader()
    val images by produceState<Map<String, ImageBitmap>>(emptyMap(), imageUris) {
        value = withContext(Dispatchers.IO) { imageUris.mapNotNull { uri -> loadImage(uri)?.let { uri to it } }.toMap() }
    }
    var center by rememberSaveable(stateSaver = Saver<Offset, List<Float>>(save = { listOf(it.x, it.y) }, restore = { Offset(it[0], it[1]) })) { mutableStateOf(Offset.Zero) }
    var viewWidth by rememberSaveable { mutableDoubleStateOf(20.0) }
    var viewHeight by rememberSaveable { mutableDoubleStateOf(16.0) }
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    var geometry by remember { mutableStateOf(Geometry2D()) }
    var meshes by remember { mutableStateOf(emptyList<Mesh3D>()) }
    var trace by remember { mutableStateOf<Offset?>(null) }
    var zoom3D by remember { mutableFloatStateOf(1f) }
    val camera = remember { GraphCameraState() }
    val view = { Viewport(center.x.toDouble(), center.y.toDouble(), viewWidth, viewHeight.takeIf { it > 0 } ?: viewWidth * size.height / size.width) }
    val program by produceState<PlotProgram?>(null, expressions, settings) {
        value = withContext(Dispatchers.Default) { PlotProgram.compile(expressions.filter { parseImageData(it.text) == null }, settings) }
    }
    LaunchedEffect(program, is3D, viewWidth, size.width) {
        val compiled = program ?: return@LaunchedEffect
        val sampling = compiled.copy(context = dev.demos.graphing.math.EvaluationContext(compiled.context.constants, compiled.context.functions, compiled.context.radians))
        val context = currentCoroutineContext()
        if (is3D) meshes = withContext(Dispatchers.Default) { PlotGeometry.sample3D(sampling) { !context.isActive } }
        else geometry = withContext(Dispatchers.Default) { PlotGeometry.sample2D(sampling, view(), size.width.coerceAtLeast(400)) { !context.isActive } }
    }
    fun transform(zoom: Float, pan: Offset) {
        if (zoom.isFinite() && zoom > 0f) {
            viewWidth = (viewWidth / zoom).coerceIn(1e-7, 1e10)
            viewHeight = (viewHeight / zoom).coerceIn(1e-7, 1e10)
        }
        center = Offset(center.x - pan.x * viewWidth.toFloat() / size.width, center.y + pan.y * viewHeight.toFloat() / size.height)
        trace = null
    }
    var reset by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current.density
    val measurer = rememberTextMeasurer()
    val labels = remember(measurer, density) { PlotLabels(measurer, density) }
    Box(modifier.background(Color.White).clipToBounds().onSizeChanged {
        if (it.width > 0 && it.height > 0) { size = it; viewHeight = viewWidth * it.height / it.width }
    }) {
        if (is3D) PlotSurface3D(meshes, settings, reset, camera, zoom3D) { trace = Offset.Zero }
        else Canvas(Modifier.fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Scroll) {
                            val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                            if (delta != 0f) {
                                transform(if (delta < 0f) 1.1f else 1f / 1.1f, Offset.Zero)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(false)
                var pan = Offset.Zero
                var zoom = 1f
                var pressed = true
                while (pressed) {
                    val event = awaitPointerEvent()
                    pressed = event.changes.any { it.pressed }
                    pan += event.calculatePan()
                    zoom *= event.calculateZoom()
                    if (pan != Offset.Zero || zoom != 1f) { transform(zoom, pan); pan = Offset.Zero; zoom = 1f; event.changes.forEach { if (it.positionChanged()) it.consume() } }
                }
                val current = view()
                fun screenPoint(point: Offset) = Offset(
                    ((point.x - current.left) / current.width * size.width).toFloat(),
                    ((current.top - point.y) / current.height * size.height).toFloat(),
                )
                val intersection = geometry.interests
                    .filter { it.kind == InterestKind.INTERSECTION }
                    .map { Offset(it.x.toFloat(), it.y.toFloat()) }
                    .minByOrNull { screenPoint(it).minus(down.position).getDistance() }
                    ?.takeIf { screenPoint(it).minus(down.position).getDistance() <= 48f * density }
                if (intersection != null) {
                    trace = intersection
                } else {
                    var nearest: Offset? = null
                    var best = 42f * density
                    geometry.interests
                        .filter { it.kind != InterestKind.INTERSECTION }
                        .map { Offset(it.x.toFloat(), it.y.toFloat()) }
                        .forEach { point ->
                            val distance = screenPoint(point).minus(down.position).getDistance()
                            if (distance < best) { best = distance; nearest = point }
                        }
                    geometry.strokes.forEach { stroke ->
                        val coordinates = stroke.coordinates
                        for (i in 0 until coordinates.size - 2 step 2) {
                            val a = screenPoint(Offset(coordinates[i].toDouble().toFloat(), coordinates[i + 1].toDouble().toFloat()))
                            val b = screenPoint(Offset(coordinates[i + 2].toDouble().toFloat(), coordinates[i + 3].toDouble().toFloat()))
                            val delta = b - a
                            val lengthSquared = delta.x * delta.x + delta.y * delta.y
                            val amount = if (lengthSquared > 0f) ((down.position - a).x * delta.x + (down.position - a).y * delta.y) / lengthSquared else 0f
                            val clamped = amount.coerceIn(0f, 1f)
                            val candidate = a + delta * clamped
                            val distance = candidate.minus(down.position).getDistance()
                            if (distance < best) {
                                best = distance
                                nearest = Offset(
                                    (current.left + candidate.x / size.width * current.width).toFloat(),
                                    (current.top - candidate.y / size.height * current.height).toFloat(),
                                )
                            }
                        }
                    }
                    trace = nearest
                }
            }
            }
        ) {
            val current = view(); val width = size.width; val height = size.height
            fun sx(x: Double) = ((axisCoordinate(x, settings.appearance.logX) - current.left) / current.width * width).toFloat()
            fun sy(y: Double) = ((current.top - axisCoordinate(y, settings.appearance.logY)) / current.height * height).toFloat()
            val step = niceStep(90 * density / (width / current.width))
            if (settings.showGrid) {
                var x = ceil(current.left / step) * step; var count = 0
                while (x <= current.right && count++ < 300) { drawLine(Color(0xFFDDDDDD), Offset(sx(x), 0f), Offset(sx(x), height.toFloat())); x += step }
                var y = ceil(current.bottom / step) * step; count = 0
                while (y <= current.top && count++ < 300) { drawLine(Color(0xFFDDDDDD), Offset(0f, sy(y)), Offset(width.toFloat(), sy(y))); y += step }
            }
            geometry.fills.forEach { fill ->
                val path = Path().apply { fill.coordinates.indices.step(2).forEach { i -> if (i == 0) moveTo(sx(fill.coordinates[i].toDouble()), sy(fill.coordinates[i + 1].toDouble())) else lineTo(sx(fill.coordinates[i].toDouble()), sy(fill.coordinates[i + 1].toDouble())) }; close() }
                drawPath(path, Color(fill.color).copy(alpha = .14f))
            }
            if (settings.showAxes) {
                if (current.left <= 0 && current.right >= 0) drawLine(Color.DarkGray, Offset(sx(0.0), 0f), Offset(sx(0.0), height.toFloat()), 1.5f * density)
                if (current.bottom <= 0 && current.top >= 0) drawLine(Color.DarkGray, Offset(0f, sy(0.0)), Offset(width.toFloat(), sy(0.0)), 1.5f * density)
            }
            geometry.strokes.forEach { line ->
                val path = Path().apply { line.coordinates.indices.step(2).forEach { i -> val point = Offset(sx(line.coordinates[i].toDouble()), sy(line.coordinates[i + 1].toDouble())); if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) } }
                if (line.points) line.coordinates.indices.step(2).forEach { drawCircle(Color(line.color), 4f * density, Offset(sx(line.coordinates[it].toDouble()), sy(line.coordinates[it + 1].toDouble()))) }
                else drawPath(path, Color(line.color), style = Stroke(2.6f * density))
            }
            for (image in imageSources) images[image.uri]?.let {
                rotate((-image.angle).toFloat(), Offset(sx(image.x), sy(image.y))) {
                    drawImage(it, dstOffset = IntOffset(sx(image.x - image.width / 2).roundToInt(), sy(image.y + image.height / 2).roundToInt()),
                        dstSize = IntSize((image.width / current.width * width).roundToInt().coerceAtLeast(1), (image.height / current.height * height).roundToInt().coerceAtLeast(1)), alpha = image.opacity.toFloat())
                }
            }
            trace?.takeIf { it != Offset.Zero }?.let { drawCircle(Color.Black, 5f * density, Offset(sx(it.x.toDouble()), sy(it.y.toDouble()))) }
            labels.textSize = 13f * density
            val textStep = if (settings.appearance.numbers) step else Double.POSITIVE_INFINITY
            if (settings.showAxes && textStep.isFinite()) {
                val xAxisY = sy(0.0).coerceIn(14f * density, height.toFloat() - 4f * density)
                for (x in generateSequence(ceil(current.left / textStep) * textStep) { it + textStep }.take(60)
                    .filter { it in current.left..current.right && abs(it) > 1e-8 }) {
                    drawTextWithHalo(String.format(Locale.US, "%.3g", x), sx(x), xAxisY + 16 * density, labels, density)
                }
                val yStep = niceStep(90 * density / (height / current.height))
                val yAxisX = sx(0.0).coerceIn(4f * density, width.toFloat() - 4f * density)
                for (y in generateSequence(ceil(current.bottom / yStep) * yStep) { it + yStep }.take(60)
                    .filter { it in current.bottom..current.top && abs(it) > 1e-8 }) {
                    val text = String.format(Locale.US, "%.3g", y)
                    val labelX = (yAxisX - labels.measureText(text) - 6f * density)
                        .coerceIn(2f * density, width.toFloat() - labels.measureText(text) - 2f * density)
                    drawTextWithHalo(text, labelX, sy(y) + 4f * density, labels, density)
                }
            }
        }
        if (showControls) PlotDesktopControls(is3D,
            onZoom = { zoom -> if (is3D) zoom3D = (zoom3D * zoom).coerceIn(.01f, 100f) else transform(zoom, Offset.Zero) },
            onReset = { center = Offset.Zero; viewWidth = 20.0; viewHeight = 16.0; zoom3D = 1f; reset++ })
    }
}

@Composable private fun PlotDesktopControls(is3D: Boolean, onZoom: (Float) -> Unit, onReset: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.End) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf<Pair<String, () -> Unit>>("⌂" to onReset, "+" to { onZoom(1.3f) }, "−" to { onZoom(1f / 1.3f) }).forEach { (label, action) ->
                androidx.compose.material3.TextButton(onClick = action, modifier = Modifier.size(42.dp).background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFD6DDE5), RoundedCornerShape(8.dp))) { BasicText(label, style = interfaceTextStyle(20.sp)) }
            }
        }
        if (is3D) BasicText("Left-drag rotate · Right/middle-drag pan · Scroll zoom",
            Modifier.padding(6.dp), style = interfaceTextStyle(11.sp).copy(color = DemosColors.Muted))
    }
}

private fun niceStep(target: Double): Double {
    if (!target.isFinite() || target <= 0.0) return 1.0
    val magnitude = 10.0.pow(floor(log10(target)))
    val normalized = target / magnitude
    return magnitude * when { normalized < 1.4 -> 1.0; normalized < 3.2 -> 2.0; normalized < 7 -> 5.0; else -> 10.0 }
}
