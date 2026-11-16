package dev.demos.graphing.plot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import dev.demos.graphing.ui.createPlotImage
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import java.util.Locale
import kotlin.math.*

/** Shared Compose renderer and camera interactions for Android and Windows. */
@Composable
internal fun PlotSurface3D(meshes: List<Mesh3D>, settings: PlotSettings, resetCounter: Int,
    cameraState: GraphCameraState, zoom: Int, interactive: Boolean,
    onReady: (Boolean) -> Unit, onInspect: (String) -> Unit) {
    var rotation by remember { mutableStateOf(if (cameraState.configuredBounds == null) Quaternion.fromEuler(-30f, 20f)
        else Quaternion(cameraState.rotationW, cameraState.rotationX, cameraState.rotationY, cameraState.rotationZ)) }
    var target by remember { mutableStateOf(Vec3(cameraState.targetX, cameraState.targetY, cameraState.targetZ)) }
    var distance by remember { mutableFloatStateOf(cameraState.distance) }
    var destination by remember { mutableStateOf<CameraDestination3D?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var spinSpeed by remember { mutableFloatStateOf(0f) }
    var previousZoom by remember { mutableIntStateOf(zoom) }
    val currentMeshes by rememberUpdatedState(meshes)
    val currentSettings by rememberUpdatedState(settings)
    val configuredBounds = listOf(settings.xMin, settings.xMax, settings.yMin, settings.yMax, settings.zMin, settings.zMax)
    val requestedBounds = remember(configuredBounds) { cubicBounds(settings) }
    var bounds by remember { mutableStateOf(requestedBounds) }
    var frame by remember { mutableStateOf<Rect?>(null) }
    var renderedScene by remember { mutableStateOf<RenderedScene3D?>(null) }
    val currentBounds by rememberUpdatedState(bounds)
    val currentOnInspect by rememberUpdatedState(onInspect)
    val measurer = rememberTextMeasurer()
    val plotDensity = LocalDensity.current
    val density = plotDensity.density
    val labels = remember(measurer, plotDensity) { PlotLabels(measurer, plotDensity) }
    LaunchedEffect(resetCounter, configuredBounds) {
        if (cameraState.resetCounter != resetCounter || cameraState.configuredBounds != configuredBounds ||
            cameraState.sceneBounds != requestedBounds.asList()) {
            bounds = requestedBounds
            spinSpeed = 0f
            val home = CameraDestination3D(Quaternion.fromEuler(-30f, 20f), bounds.center,
                fitDistance(bounds, settings.appearance.perspective))
            if (cameraState.configuredBounds == null || cameraState.configuredBounds != configuredBounds ||
                cameraState.sceneBounds != requestedBounds.asList()) {
                destination = null
                rotation = home.rotation; target = home.target; distance = home.distance
            } else destination = home
            cameraState.configuredBounds = configuredBounds
            cameraState.sceneBounds = bounds.asList()
            cameraState.resetCounter = resetCounter
        }
    }
    LaunchedEffect(zoom) {
        val steps = zoom - previousZoom
        if (steps != 0) {
            spinSpeed = 0f
            val end = ((destination?.distance ?: distance) / 1.5.pow(steps.toDouble())).toFloat().coerceIn(.01f, 1e7f)
            destination = CameraDestination3D(rotation, target, end)
        }
        previousZoom = zoom
    }
    LaunchedEffect(cameraState.viewRequestCounter) {
        if (cameraState.viewRequestCounter == 0) return@LaunchedEffect
        spinSpeed = 0f
        destination = when (cameraState.requestedView) {
            Graph3DView.Stop -> null
            Graph3DView.Standard -> CameraDestination3D(Quaternion.fromEuler(-30f, 20f), bounds.center,
                fitDistance(bounds, currentSettings.appearance.perspective))
            Graph3DView.XY -> CameraDestination3D(Quaternion.fromEuler(0f, 90f), target, distance)
            Graph3DView.XZ -> CameraDestination3D(Quaternion.fromEuler(0f, 0f), target, distance)
            Graph3DView.YZ -> CameraDestination3D(Quaternion.fromEuler(-90f, 0f), target, distance)
        }
    }
    LaunchedEffect(destination) {
        val end = destination ?: return@LaunchedEffect
        val start = CameraDestination3D(rotation, target, distance)
        val started = withFrameNanos { it }
        var fraction = .2f
        while (true) {
            rotation = start.rotation.interpolate(end.rotation, fraction.toDouble())
            target = start.target * (1.0 - fraction) + end.target * fraction.toDouble()
            distance = if (fraction == 1f) end.distance else
                (1.0 / ((1.0 - fraction) / start.distance + fraction / end.distance)).toFloat()
            if (fraction == 1f) break
            fraction = withFrameNanos { (.2f + (it - started) / 200_000_000f).coerceAtMost(1f) }
        }
        destination = null
    }
    LaunchedEffect(spinSpeed) {
        if (spinSpeed == 0f) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            rotation = rotateCamera(rotation, Offset(spinSpeed * ((now - previous) / 1_000_000f), 0f))
            previous = now
        }
    }
    // Observe camera changes in composition too, so saved state stays current
    // when a gesture would otherwise invalidate only the Canvas draw phase.
    val savedRotation = rotation
    val savedTarget = target
    val savedDistance = distance
    SideEffect {
        cameraState.rotationW = savedRotation.w
        cameraState.rotationX = savedRotation.x
        cameraState.rotationY = savedRotation.y
        cameraState.rotationZ = savedRotation.z
        cameraState.targetX = savedTarget.x
        cameraState.targetY = savedTarget.y
        cameraState.targetZ = savedTarget.z
        cameraState.distance = savedDistance
        cameraState.spinning = spinSpeed != 0f
    }

    val currentDensity by rememberUpdatedState(density)
    val currentFrame = frame
    val moving = dragging || destination != null || spinSpeed != 0f
    val desiredScene = if (currentFrame == null) null
        else PlotScene3D(meshes, settings, bounds, rotation, target, distance, currentFrame, density, moving)
    val rasterReady = desiredScene != null && renderedScene?.scene == desiredScene &&
        !moving && previousZoom == zoom && cameraState.resetCounter == resetCounter &&
        cameraState.configuredBounds == configuredBounds
    SideEffect { onReady(rasterReady) }
    LaunchedEffect(Unit) {
        snapshotFlow {
            val currentFrame = frame
            if (currentFrame == null) null
            else PlotScene3D(currentMeshes, currentSettings, bounds, rotation, target, distance, currentFrame, currentDensity,
                dragging || destination != null || spinSpeed != 0f)
        }.filterNotNull().conflate().collect { scene ->
            renderedScene = withContext(Dispatchers.Default) { renderScene3D(scene) }
        }
    }

    Canvas(Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
        if (coordinates.size.width > 0 && coordinates.size.height > 0) {
            val origin = coordinates.positionInRoot()
            val next = Rect(origin.x, origin.y, origin.x + coordinates.size.width, origin.y + coordinates.size.height)
            frame = next
        }
    }
        .semantics { contentDescription = "Three-dimensional graph. Drag or right-drag to rotate. Pinch or scroll to zoom. Shift-drag, middle-drag, or select Pan to move the view. Click to stop rotation. Tap a surface to inspect coordinates." }
        .pointerInput(interactive) {
            if (!interactive) return@pointerInput
            awaitEachGesture {
                // Accept every mouse button as well as touch contact.
                var event = awaitPointerEvent()
                while (event.changes.none { it.pressed && !it.previousPressed }) event = awaitPointerEvent()
                val down = event.changes.first { it.pressed && !it.previousPressed }
                val wasAnimating = spinSpeed != 0f || destination != null
                spinSpeed = 0f
                destination = null
                val panGesture = !event.buttons.isSecondaryPressed && (cameraState.tool == Graph3DTool.Pan ||
                    (down.type == PointerType.Mouse && (event.buttons.isTertiaryPressed || event.keyboardModifiers.isShiftPressed)))
                val inspectGesture = down.type != PointerType.Mouse || event.buttons.isPrimaryPressed
                var moved = false
                var pastSlop = false
                var cancelled = false
                var multiplePointers = false
                var accumulatedPan = Offset.Zero
                var accumulatedZoom = 1f
                var previousCount = 1
                var lastOrbitTime = down.uptimeMillis
                var releaseTime = down.uptimeMillis
                val speeds = ArrayDeque<Float>()
                try { while (true) {
                    event = awaitPointerEvent()
                    if (event.changes.any { it.isConsumed }) { cancelled = true; break }
                    val now = event.changes.maxOfOrNull { it.uptimeMillis } ?: lastOrbitTime
                    releaseTime = now
                    val count = event.changes.count { it.pressed }
                    if (count == 0) break
                    multiplePointers = multiplePointers || count > 1
                    if (count != previousCount) {
                        accumulatedPan = Offset.Zero; accumulatedZoom = 1f; previousCount = count
                        pastSlop = false; speeds.clear(); lastOrbitTime = now
                        continue
                    }
                    val pan = event.calculatePan()
                    val zoomChange = event.calculateZoom()
                    accumulatedPan += pan
                    accumulatedZoom *= zoomChange
                    val wasMoved = pastSlop
                    pastSlop = pastSlop || accumulatedPan.getDistance() > viewConfiguration.touchSlop ||
                        abs(1f - accumulatedZoom) * event.calculateCentroidSize(useCurrent = false) > viewConfiguration.touchSlop
                    if (!pastSlop) continue
                    moved = true
                    dragging = true
                    val delta = if (wasMoved) pan else accumulatedPan
                    val factor = if (wasMoved) zoomChange else accumulatedZoom
                    val pinchMotion = abs(1f - factor) * event.calculateCentroidSize(useCurrent = false)
                    val pinchDominates = count > 1 && pinchMotion >= 2f * delta.getDistance()
                    if (panGesture || pinchDominates) {
                        target = panCamera(target, rotation, delta, currentBounds, distance,
                            size.width.toFloat(), size.height.toFloat())
                    }
                    if (pinchDominates || (panGesture && count > 1)) {
                        if (factor.isFinite() && factor > 0f) {
                            distance = (distance / factor).coerceIn(.01f, 1e7f)
                        }
                    }
                    if (!panGesture && !pinchDominates) {
                        rotation = rotateCamera(rotation, delta / density)
                        val elapsed = now - lastOrbitTime
                        if (elapsed > 100) speeds.clear()
                        else if (elapsed > 0) {
                            speeds.addLast(delta.x / density / elapsed)
                            if (speeds.size > 5) speeds.removeFirst()
                        }
                    } else {
                        speeds.clear()
                    }
                    lastOrbitTime = now
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                } } finally { dragging = false }
                if (!cancelled && moved && speeds.size == 5 && releaseTime - lastOrbitTime <= 100) {
                    val speed = speeds.average().toFloat().coerceIn(-.1f, .1f)
                    if (abs(speed) >= .01f) spinSpeed = speed
                }
                if (!moved && !multiplePointers && !cancelled && !panGesture && inspectGesture && !wasAnimating) {
                    renderedScene?.let { shown ->
                        val offset = shown.scene.frame.topLeft - (frame?.topLeft ?: shown.scene.frame.topLeft)
                        inspectPoint(shown.scene.meshes, shown.scene.bounds, shown.projection, down.position - offset, 28f * density)?.let(currentOnInspect)
                    }
                }
            }
        }
        .pointerInput(interactive) {
            if (!interactive) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Scroll) {
                        val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                        if (delta != 0f) {
                            val factor = plotScrollZoom(delta)
                            spinSpeed = 0f
                            val end = ((destination?.distance ?: distance) / factor).coerceIn(.01f, 1e7f)
                            destination = CameraDestination3D(rotation, target, end)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
        }) {
        val shown = renderedScene ?: return@Canvas
        val projection = shown.projection
        val settings = shown.scene.settings
        val offset = shown.scene.frame.topLeft - (frame?.topLeft ?: shown.scene.frame.topLeft)
        translate(offset.x, offset.y) {
            drawImage(shown.image, dstSize = IntSize(shown.scene.frame.width.roundToInt(), shown.scene.frame.height.roundToInt()))
            if (settings.showAxes) {
                val names = listOf(settings.appearance.xLabel, settings.appearance.yLabel, settings.appearance.zLabel)
                for (axis in 0..2) {
                    fun label(value: Float, text: String) {
                        val point = projection.point(if (axis == 0) value else 0f, if (axis == 1) value else 0f, if (axis == 2) value else 0f)
                        if (point.visible) drawTextWithHalo(text, point.position.x + 4 * density, point.position.y - 4 * density, labels, density, Color(graphAxisColors[axis]))
                    }
                    val low = listOf(projection.xMin, projection.yMin, projection.zMin)[axis]
                    val high = listOf(projection.xMax, projection.yMax, projection.zMax)[axis]
                    if (settings.appearance.labels) label(high, names[axis])
                    if (settings.appearance.numbers) {
                        val step = coordinateStep(((high - low) / 8f).toDouble()).toFloat()
                        var value = ceil((low / step).toDouble()).toFloat() * step
                        repeat(40) {
                            if (value in low..high && abs(value) > 1e-6f)
                                label(value, formatCoordinate(value.toDouble()))
                            value += step
                        }
                    }
                }
            }
        }
    }
}


private data class PlotScene3D(val meshes: List<Mesh3D>, val settings: PlotSettings, val bounds: Bounds,
    val rotation: Quaternion, val target: Vec3, val distance: Float, val frame: Rect, val density: Float, val moving: Boolean)

private data class RenderedScene3D(val image: ImageBitmap, val scene: PlotScene3D, val projection: Projection)

private suspend fun renderScene3D(scene: PlotScene3D): RenderedScene3D {
    val context = currentCoroutineContext()
    val budget = if (scene.moving) 180_000.0 else 2_000_000.0
    val scale = minOf(1.0, sqrt(budget / (scene.frame.width.toDouble() * scene.frame.height)))
    val width = (scene.frame.width * scale).roundToInt().coerceAtLeast(1)
    val height = (scene.frame.height * scale).roundToInt().coerceAtLeast(1)
    val projection = Projection(scene.settings, scene.bounds, scene.rotation, scene.target, scene.distance, scene.frame)
    val primitives = scenePrimitives(scene, projection, width / scene.frame.width, height / scene.frame.height)
    val pixels = rasterizePlot3D(primitives, width, height) { !context.isActive }
    return RenderedScene3D(createPlotImage(width, height, pixels), scene, projection)
}

private fun scenePrimitives(scene: PlotScene3D, projection: Projection, scaleX: Float, scaleY: Float): List<PlotRasterPrimitive> {
    val meshes = scene.meshes
    val settings = scene.settings
    val faces = ArrayList<Face>()
    for (mesh in meshes) {
        val points = (mesh.vertices.indices step 6).map {
            Vec3(mesh.vertices[it].toDouble(), mesh.vertices[it + 1].toDouble(), mesh.vertices[it + 2].toDouble())
        }
        fun addFace(vertices: List<Vec3>, filled: Boolean = false, shade: Float = 1f) {
            val clipped = scene.bounds.clip(vertices, filled)
            if (clipped.isNotEmpty()) faces += Face(clipped.map {
                projection.point(it.x.toFloat(), it.y.toFloat(), it.z.toFloat())
            }, Color(mesh.color), filled, shade)
        }
        when (mesh.primitive) {
            Mesh3D.TRIANGLES -> for (i in 0 until points.size - 2 step 3) {
                val normal = abs(mesh.vertices[i * 6 + 5]).coerceIn(0f, 1f)
                addFace(points.subList(i, i + 3), true, .55f + normal * .4f)
            }
            Mesh3D.LINES -> for (i in 0 until points.size - 1 step 2) addFace(points.subList(i, i + 2))
            Mesh3D.LINE_STRIP -> for (i in 0 until points.size - 1) addFace(points.subList(i, i + 2))
            Mesh3D.POINTS -> points.forEach { addFace(listOf(it)) }
        }
    }
    if (settings.showGrid && projection.zMin <= 0f && projection.zMax >= 0f) {
        faces += Face(listOf(
            projection.point(projection.xMin, projection.yMin, 0f),
            projection.point(projection.xMax, projection.yMin, 0f),
            projection.point(projection.xMax, projection.yMax, 0f),
            projection.point(projection.xMin, projection.yMax, 0f),
        ), Color(graphPlaneColor), true, depthWrite = false)
        val xStep = coordinateStep((projection.xMax.toDouble() - projection.xMin) / 10.0)
        val yStep = coordinateStep((projection.yMax.toDouble() - projection.yMin) / 10.0)
        fun gridLines(low: Float, high: Float, step: Double, alongX: Boolean) {
            if (!low.isFinite() || !high.isFinite() || low > high) return
            val first = ceil(low / step) * step
            val count = floor((high - first) / step).toInt()
            for (index in 0..count) {
                val value = (first + index * step).toFloat()
                val start = if (alongX) projection.point(value, projection.yMin, 0f)
                    else projection.point(projection.xMin, value, 0f)
                val end = if (alongX) projection.point(value, projection.yMax, 0f)
                    else projection.point(projection.xMax, value, 0f)
                faces += Face(listOf(start, end), Color(0xFFCBD0D7))
            }
        }
        gridLines(projection.xMin, projection.xMax, xStep, true)
        gridLines(projection.yMin, projection.yMax, yStep, false)
    }
    if (settings.showAxes) {
        faces += Face(listOf(projection.point(projection.xMin, 0f, 0f), projection.point(projection.xMax, 0f, 0f)), Color(graphAxisColors[0]))
        faces += Face(listOf(projection.point(0f, projection.yMin, 0f), projection.point(0f, projection.yMax, 0f)), Color(graphAxisColors[1]))
        faces += Face(listOf(projection.point(0f, 0f, projection.zMin), projection.point(0f, 0f, projection.zMax)), Color(graphAxisColors[2]))
        val arrowX = (projection.xMax - projection.xMin) * .035f
        val arrowY = (projection.yMax - projection.yMin) * .035f
        val arrowZ = (projection.zMax - projection.zMin) * .035f
        faces += Face(listOf(projection.point(projection.xMax, 0f, 0f), projection.point(projection.xMax - arrowX, arrowY, 0f)), Color(graphAxisColors[0]))
        faces += Face(listOf(projection.point(projection.xMax, 0f, 0f), projection.point(projection.xMax - arrowX, -arrowY, 0f)), Color(graphAxisColors[0]))
        faces += Face(listOf(projection.point(0f, projection.yMax, 0f), projection.point(arrowX, projection.yMax - arrowY, 0f)), Color(graphAxisColors[1]))
        faces += Face(listOf(projection.point(0f, projection.yMax, 0f), projection.point(-arrowX, projection.yMax - arrowY, 0f)), Color(graphAxisColors[1]))
        faces += Face(listOf(projection.point(0f, 0f, projection.zMax), projection.point(arrowX, 0f, projection.zMax - arrowZ)), Color(graphAxisColors[2]))
        faces += Face(listOf(projection.point(0f, 0f, projection.zMax), projection.point(-arrowX, 0f, projection.zMax - arrowZ)), Color(graphAxisColors[2]))
    }
    return faces.mapNotNull { face ->
        val points = projection.clip(face.points, face.filled)
        if (points.isEmpty() || (face.filled && points.size < 3)) return@mapNotNull null
        val color = face.color.copy(red = face.color.red * face.shade,
            green = face.color.green * face.shade, blue = face.color.blue * face.shade).toArgb()
        PlotRasterPrimitive(points.map { PlotRasterVertex(it.position.x * scaleX, it.position.y * scaleY, it.depth, it.reciprocalW) },
            color, face.filled, (if (points.size == 1) 8f else 1.3f) * scene.density * minOf(scaleX, scaleY), face.depthWrite)
    }
}

private fun rotateCamera(rotation: Quaternion, delta: Offset): Quaternion {
    val right = rotation.inverse().rotate(Vec3(1.0, 0.0, 0.0))
    val up = rotation.rotate(Vec3(0.0, 0.0, 1.0))
    val yaw = atan2(-right.y, right.x) + Math.toRadians(delta.x.toDouble())
    val pitch = (atan2(-up.y, up.z) + Math.toRadians(delta.y.toDouble())).coerceIn(-PI / 2, PI / 2)
    // World-Z yaw followed by screen-X elevation keeps the vertical axis upright.
    return (Quaternion.fromAxis(Vec3(1.0, 0.0, 0.0), pitch) *
        Quaternion.fromAxis(Vec3(0.0, 0.0, 1.0), yaw)).normalized()
}

private data class CameraDestination3D(val rotation: Quaternion, val target: Vec3, val distance: Float)

private fun panCamera(target: Vec3, rotation: Quaternion, delta: Offset, bounds: Bounds,
    distance: Float, width: Float, height: Float): Vec3 {
    val extent = maxOf(bounds.max.x - bounds.min.x, bounds.max.y - bounds.min.y, bounds.max.z - bounds.min.z)
        .coerceAtLeast(.0001) / 10
    val scale = extent / distance.coerceAtLeast(.01f) / (minOf(width, height).coerceAtLeast(1f) * 1.4)
    return target + rotation.inverse().rotate(Vec3(-delta.x * scale, 0.0, delta.y * scale))
}

private fun inspectPoint(meshes: List<Mesh3D>, bounds: Bounds, projection: Projection, tap: Offset, radius: Float): String? {
    var nearest: FloatArray? = null
    var best = radius
    meshes.forEach { mesh ->
        for (i in mesh.vertices.indices step 6) {
            if (!bounds.contains(Vec3(mesh.vertices[i].toDouble(), mesh.vertices[i + 1].toDouble(), mesh.vertices[i + 2].toDouble()))) continue
            val point = projection.point(mesh.vertices, i)
            val error = (point.position - tap).getDistance()
            if (point.visible && error < best) { best = error; nearest = mesh.vertices.copyOfRange(i, i + 3) }
        }
    }
    return nearest?.let { String.format(Locale.US, "(%.4g, %.4g, %.4g)", it[0], it[1], it[2]) }
}

private data class ProjectedPoint(val position: Offset, val depth: Float, val visible: Boolean,
    val reciprocalW: Float, val cameraX: Float, val cameraZ: Float)
private data class Face(val points: List<ProjectedPoint>, val color: Color, val filled: Boolean = false,
    val shade: Float = 1f, val depthWrite: Boolean = true)

private data class Bounds(val min: Vec3, val max: Vec3) {
    val center get() = (min + max) * .5
    val radius get() = (max - center).length
    fun asList() = listOf(min.x, max.x, min.y, max.y, min.z, max.z)
    fun contains(point: Vec3) = point.x in min.x..max.x && point.y in min.y..max.y && point.z in min.z..max.z

    /** Clip in world space so the plotting cube never distorts the mathematics. */
    fun clip(points: List<Vec3>, filled: Boolean): List<Vec3> {
        if (points.isEmpty() || points.any { !it.finite }) return emptyList()
        if (points.all(::contains)) return points
        fun coordinate(point: Vec3, axis: Int) = when (axis) { 0 -> point.x; 1 -> point.y; else -> point.z }
        var result = points
        for (side in 0..5) {
            if (result.isEmpty()) return result
            val axis = side / 2
            val lower = side % 2 == 0
            val boundary = coordinate(if (lower) min else max, axis)
            fun distance(point: Vec3) = if (lower) coordinate(point, axis) - boundary else boundary - coordinate(point, axis)
            val input = result
            val output = ArrayList<Vec3>()
            var previous = if (filled) input.last() else input.first()
            var previousDistance = distance(previous)
            if (!filled && previousDistance >= 0.0) output.add(previous)
            val start = if (filled) 0 else 1
            for (index in start until input.size) {
                val point = input[index]
                val pointDistance = distance(point)
                if ((previousDistance >= 0.0) != (pointDistance >= 0.0)) {
                    val fraction = (previousDistance / (previousDistance - pointDistance)).coerceIn(0.0, 1.0)
                    output.add(previous + (point - previous) * fraction)
                }
                if (pointDistance >= 0.0) output.add(point)
                previous = point
                previousDistance = pointDistance
            }
            result = output
        }
        return result
    }
}

private fun cubicBounds(settings: PlotSettings): Bounds {
    fun range(low: Double?, high: Double?): Pair<Double, Double> {
        val first = low ?: -5.0
        val last = high ?: 5.0
        return if (first.isFinite() && last.isFinite() && first < last) first to last else -5.0 to 5.0
    }
    val x = range(settings.xMin, settings.xMax)
    val y = range(settings.yMin, settings.yMax)
    val z = range(settings.zMin, settings.zMax)
    val center = Vec3(x.first / 2 + x.second / 2, y.first / 2 + y.second / 2, z.first / 2 + z.second / 2)
    val halfSide = maxOf(x.second / 2 - x.first / 2, y.second / 2 - y.first / 2, z.second / 2 - z.first / 2)
    val radius = Vec3(halfSide, halfSide, halfSide)
    return Bounds(center - radius, center + radius)
}

private fun fitDistance(bounds: Bounds, perspective: Float): Float {
    val radius = bounds.radius.coerceAtLeast(1e-4)
    val extent = maxOf(bounds.max.x - bounds.min.x, bounds.max.y - bounds.min.y, bounds.max.z - bounds.min.z)
        .coerceAtLeast(1e-4) / 10.0
    val blend = perspective.coerceIn(0f, 2f).toDouble()
    // Projection scales by 1.4, so fit the bounding sphere into half the
    // shorter viewport dimension, including its perspective depth variation.
    return (radius / extent * sqrt(2.8 * 2.8 + blend * blend) * 1.5)
        .coerceIn(0.01, 1e7).toFloat()
}

private data class Quaternion(val w: Double, val x: Double, val y: Double, val z: Double) {
    operator fun times(other: Quaternion) = Quaternion(
        w * other.w - x * other.x - y * other.y - z * other.z,
        w * other.x + x * other.w + y * other.z - z * other.y,
        w * other.y - x * other.z + y * other.w + z * other.x,
        w * other.z + x * other.y - y * other.x + z * other.w)
    fun normalized(): Quaternion {
        val length = sqrt(w * w + x * x + y * y + z * z).coerceAtLeast(1e-20)
        return Quaternion(w / length, x / length, y / length, z / length)
    }
    fun interpolate(other: Quaternion, fraction: Double): Quaternion {
        val sign = if (w * other.w + x * other.x + y * other.y + z * other.z < 0.0) -1.0 else 1.0
        return Quaternion(w + (other.w * sign - w) * fraction, x + (other.x * sign - x) * fraction,
            y + (other.y * sign - y) * fraction, z + (other.z * sign - z) * fraction).normalized()
    }
    fun inverse() = Quaternion(w, -x, -y, -z)
    fun rotate(v: Vec3): Vec3 {
        val p = this * Quaternion(0.0, v.x, v.y, v.z) * inverse()
        return Vec3(p.x, p.y, p.z)
    }
    companion object {
        fun fromAxis(axis: Vec3, angle: Double): Quaternion {
            val s = sin(angle / 2.0)
            return Quaternion(cos(angle / 2.0), axis.x * s, axis.y * s, axis.z * s)
        }
        fun fromEuler(yaw: Float, pitch: Float): Quaternion =
            fromAxis(Vec3(1.0, 0.0, 0.0), Math.toRadians(pitch.toDouble())) *
                fromAxis(Vec3(0.0, 0.0, 1.0), Math.toRadians(yaw.toDouble()))
    }
}

private fun formatCoordinate(value: Double): String =
    if (abs(value - value.roundToInt()) < 1e-7) value.roundToInt().toString()
    else String.format(Locale.US, "%.3g", value)

private fun coordinateStep(target: Double): Double {
    if (!target.isFinite() || target <= 0.0) return 1.0
    val magnitude = 10.0.pow(floor(log10(target)))
    val normalized = target / magnitude
    return magnitude * when {
        normalized < 1.4 -> 1.0
        normalized < 3.2 -> 2.0
        normalized < 7 -> 5.0
        else -> 10.0
    }
}

private class Projection(settings: PlotSettings, bounds: Bounds, private val rotation: Quaternion, private val target: Vec3,
    private val distance: Float,
    private val frame: Rect) {
    val xMin = bounds.min.x.toFloat(); val xMax = bounds.max.x.toFloat()
    val yMin = bounds.min.y.toFloat(); val yMax = bounds.max.y.toFloat()
    val zMin = bounds.min.z.toFloat(); val zMax = bounds.max.z.toFloat()
    private val extent = maxOf(xMax - xMin, yMax - yMin, zMax - zMin).coerceAtLeast(.0001f) / 10
    private val perspective = settings.appearance.perspective.coerceIn(0f, 2f)
    private val eyeDistance = fitDistance(bounds, perspective)
    private val nearPlane = minOf(.1f, eyeDistance * .01f).coerceAtLeast(.000001f)
    fun point(vertices: FloatArray, index: Int) = point(vertices[index], vertices[index + 1], vertices[index + 2])
    fun point(x: Float, y: Float, z: Float): ProjectedPoint {
        val view = rotation.rotate(Vec3(x.toDouble(), y.toDouble(), z.toDouble()) - target)
        val horizontal = (view.x / extent).toFloat()
        val vertical = (view.z / extent).toFloat()
        val depth = (view.y / extent).toFloat()
        return project(horizontal, vertical, depth)
    }

    private fun project(horizontal: Float, vertical: Float, depth: Float): ProjectedPoint {
        // Scale the projected view without moving the eye through the geometry.
        val divisor = eyeDistance + depth * perspective
        val scale = minOf(frame.width, frame.height) * 1.4f *
            (eyeDistance / distance) / divisor.coerceAtLeast(nearPlane)
        val center = frame.center - frame.topLeft
        val position = Offset(center.x + horizontal * scale, center.y - vertical * scale)
        return ProjectedPoint(position, depth, divisor > nearPlane && position.x.isFinite() && position.y.isFinite(),
            1f / divisor, horizontal, vertical)
    }

    fun clip(points: List<ProjectedPoint>, filled: Boolean): List<ProjectedPoint> {
        if (points.any { !it.cameraX.isFinite() || !it.cameraZ.isFinite() || !it.depth.isFinite() }) return emptyList()
        fun intersection(a: ProjectedPoint, b: ProjectedPoint): ProjectedPoint {
            val aw = eyeDistance + a.depth * perspective
            val bw = eyeDistance + b.depth * perspective
            val t = ((nearPlane * 1.001f - aw) / (bw - aw)).coerceIn(0f, 1f)
            return project(a.cameraX + (b.cameraX - a.cameraX) * t,
                a.cameraZ + (b.cameraZ - a.cameraZ) * t, a.depth + (b.depth - a.depth) * t)
        }
        if (!filled) {
            if (points.all { it.visible }) return points
            if (points.size != 2 || points.none { it.visible }) return emptyList()
            return if (points[0].visible) listOf(points[0], intersection(points[0], points[1]))
                else listOf(intersection(points[0], points[1]), points[1])
        }
        val result = ArrayList<ProjectedPoint>()
        var previous = points.lastOrNull() ?: return result
        for (point in points) {
            if (point.visible != previous.visible) result += intersection(previous, point)
            if (point.visible) result += point
            previous = point
        }
        return result
    }
}
