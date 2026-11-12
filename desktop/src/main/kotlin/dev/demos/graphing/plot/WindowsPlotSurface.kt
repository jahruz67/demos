package dev.demos.graphing.plot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.*

/** Desktop canvas renderer for the same sampled meshes used by Android's GLES renderer. */
@Composable
internal fun PlotSurface3D(meshes: List<Mesh3D>, settings: PlotSettings, resetCounter: Int,
    cameraState: GraphCameraState, zoom: Float, onInspect: (String) -> Unit) {
    var rotation by remember { mutableStateOf(Quaternion.fromEuler(38f, 28f)) }
    var target by remember { mutableStateOf(Vec3(0.0, 0.0, 0.0)) }
    var distance by remember { mutableFloatStateOf(19f) }
    var zoomTarget by remember { mutableFloatStateOf(19f) }
    var previousZoom by remember { mutableFloatStateOf(1f) }
    var viewportSize by remember { mutableStateOf(IntSize(1, 1)) }
    val currentMeshes by rememberUpdatedState(meshes)
    val currentSettings by rememberUpdatedState(settings)
    val bounds = meshBounds(meshes, settings)
    val currentBounds by rememberUpdatedState(bounds)
    val currentRotation by rememberUpdatedState(rotation)
    val currentTarget by rememberUpdatedState(target)
    val currentDistance by rememberUpdatedState(distance)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    val labels = remember(measurer, density) { PlotLabels(measurer, density) }
    LaunchedEffect(resetCounter, bounds, viewportSize) {
        if (resetCounter > 0 || cameraState.configuredBounds != bounds.asList()) {
            target = bounds.center
            rotation = Quaternion.fromEuler(38f, 28f)
            distance = fitDistance(bounds, viewportSize.width.toFloat(), viewportSize.height.toFloat(), settings.appearance.perspective)
            zoomTarget = distance
            cameraState.configuredBounds = bounds.asList()
        }
    }
    LaunchedEffect(zoom) {
        zoomTarget = (zoomTarget * previousZoom / zoom).coerceIn(0.01f, 1e7f)
        previousZoom = zoom
    }
    LaunchedEffect(zoomTarget) {
        val start = distance
        val end = zoomTarget
        repeat(12) { frame ->
            val t = (frame + 1) / 12f
            distance = start + (end - start) * t
            delay(16)
        }
    }
    SideEffect {
        cameraState.targetX = target.x.toFloat()
        cameraState.targetY = target.y.toFloat()
        cameraState.targetZ = target.z.toFloat()
        cameraState.distance = distance
    }

    Canvas(Modifier.fillMaxSize().onSizeChanged { viewportSize = it }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var rotate = true
                val startRotation = currentRotation
                val startTarget = currentTarget
                var lastPosition = down.position
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) break
                    rotate = (event.buttons.hashCode() and 1) != 0
                    if (change.positionChanged()) {
                        if (rotate) {
                            val deltaX = (change.position.x - down.position.x).toDouble()
                            val deltaY = (change.position.y - down.position.y).toDouble()
                            val scale = 0.0065
                            val yaw = Quaternion.fromAxis(Vec3(0.0, 0.0, 1.0), -deltaX * scale)
                            val pitchAxis = startRotation.inverse().rotate(Vec3(1.0, 0.0, 0.0)).normalized()
                            val pitch = Quaternion.fromAxis(pitchAxis, -deltaY * scale)
                            rotation = (pitch * yaw * startRotation).normalized()
                        } else {
                            val delta = change.position - lastPosition
                            val scale = currentDistance / minOf(size.width, size.height).coerceAtLeast(1) * .9f
                            val cameraDelta = Vec3((-delta.x * scale).toDouble(), 0.0, (delta.y * scale).toDouble())
                            target = startTarget + rotation.inverse().rotate(cameraDelta)
                        }
                        lastPosition = change.position
                        change.consume()
                    }
                }
            }
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Scroll) {
                        val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                        val factor = if (delta < 0f) 1.1f else 1f / 1.1f
                        zoomTarget = (zoomTarget / factor).coerceIn(.01f, 1e7f)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
        .pointerInput(Unit) {
            detectTapGestures { tap ->
                val projection = Projection(currentSettings, currentBounds, currentRotation, currentTarget, currentDistance,
                    size.width.toFloat(), size.height.toFloat())
                var nearest: FloatArray? = null
                var best = 28f * density
                currentMeshes.forEach { mesh ->
                    for (i in mesh.vertices.indices step 6) {
                        val point = projection.point(mesh.vertices, i)
                        val error = (point.position - tap).getDistance()
                        if (error < best) { best = error; nearest = mesh.vertices.copyOfRange(i, i + 3) }
                    }
                }
                nearest?.let { onInspect(String.format(Locale.US, "(%.4g, %.4g, %.4g)", it[0], it[1], it[2])) }
            }
        }) {
        val projection = Projection(settings, bounds, rotation, target, distance, size.width, size.height)
        val faces = ArrayList<Face>()
        for (mesh in meshes) {
            val points = (mesh.vertices.indices step 6).map { projection.point(mesh.vertices, it) }
            when (mesh.primitive) {
                Mesh3D.TRIANGLES -> for (i in 0 until points.size - 2 step 3) {
                    val normal = abs(mesh.vertices[i * 6 + 5]).coerceIn(0f, 1f)
                    faces += Face(points.subList(i, i + 3), Color(mesh.color), true, .55f + normal * .4f)
                }
                Mesh3D.LINES -> for (i in 0 until points.size - 1 step 2) faces += Face(points.subList(i, i + 2), Color(mesh.color))
                Mesh3D.LINE_STRIP -> for (i in 0 until points.size - 1) faces += Face(points.subList(i, i + 2), Color(mesh.color))
                Mesh3D.POINTS -> points.forEach { faces += Face(listOf(it), Color(mesh.color)) }
            }
        }
        if (settings.showGrid && projection.zMin <= 0f && projection.zMax >= 0f) {
            faces += Face(listOf(
                projection.point(projection.xMin, projection.yMin, 0f),
                projection.point(projection.xMax, projection.yMin, 0f),
                projection.point(projection.xMax, projection.yMax, 0f),
                projection.point(projection.xMin, projection.yMax, 0f),
            ), Color(graphPlaneColor), true)
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
        faces.sortByDescending { face -> face.points.sumOf { it.depth.toDouble() } / face.points.size }
        for (face in faces) {
            if (face.points.any { !it.visible }) continue
            val points = face.points.map { it.position }
            if (face.filled) {
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(path, face.color.copy(red = face.color.red * face.shade,
                    green = face.color.green * face.shade, blue = face.color.blue * face.shade))
            } else if (points.size == 1) drawCircle(face.color, 4f * density, points[0])
            else drawLine(face.color, points[0], points[1], 1.3f * density)
        }
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

private data class ProjectedPoint(val position: Offset, val depth: Float, val visible: Boolean)
private data class Face(val points: List<ProjectedPoint>, val color: Color, val filled: Boolean = false, val shade: Float = 1f)

private data class Bounds(val min: Vec3, val max: Vec3) {
    val center get() = (min + max) * .5
    val radius get() = (max - center).length
    fun asList() = listOf(min.x, max.x, min.y, max.y, min.z, max.z)
}

private fun meshBounds(meshes: List<Mesh3D>, settings: PlotSettings): Bounds {
    var min = Vec3(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
    var max = Vec3(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)
    meshes.forEach { mesh ->
        for (i in mesh.vertices.indices step 6) {
            val p = Vec3(mesh.vertices[i].toDouble(), mesh.vertices[i + 1].toDouble(), mesh.vertices[i + 2].toDouble())
            min = Vec3(minOf(min.x, p.x), minOf(min.y, p.y), minOf(min.z, p.z))
            max = Vec3(maxOf(max.x, p.x), maxOf(max.y, p.y), maxOf(max.z, p.z))
        }
    }
    if (!min.finite || !max.finite) {
        min = Vec3(settings.xMin ?: -5.0, settings.yMin ?: -5.0, settings.zMin ?: -5.0)
        max = Vec3(settings.xMax ?: 5.0, settings.yMax ?: 5.0, settings.zMax ?: 5.0)
    }
    return Bounds(min, max)
}

private fun fitDistance(bounds: Bounds, width: Float, height: Float, perspective: Float): Float {
    val radius = bounds.radius.coerceAtLeast(1e-4)
    val extent = maxOf(bounds.max.x - bounds.min.x, bounds.max.y - bounds.min.y, bounds.max.z - bounds.min.z)
        .coerceAtLeast(1e-4) / 10.0
    val fov = Math.toRadians(55.0)
    val viewport = minOf(width, height).coerceAtLeast(1f)
    return (radius / extent / tan(fov / 2.0) * (1.0 + perspective) * 1.15)
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
            fromAxis(Vec3(0.0, 0.0, 1.0), Math.toRadians(yaw.toDouble())) *
                fromAxis(Vec3(1.0, 0.0, 0.0), Math.toRadians(pitch.toDouble()))
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
    private val width: Float, private val height: Float) {
    val xMin = bounds.min.x.toFloat(); val xMax = bounds.max.x.toFloat()
    val yMin = bounds.min.y.toFloat(); val yMax = bounds.max.y.toFloat()
    val zMin = bounds.min.z.toFloat(); val zMax = bounds.max.z.toFloat()
    private val extent = maxOf(xMax - xMin, yMax - yMin, zMax - zMin).coerceAtLeast(.0001f) / 10
    private val perspective = settings.appearance.perspective.coerceIn(0f, 2f)
    fun point(vertices: FloatArray, index: Int) = point(vertices[index], vertices[index + 1], vertices[index + 2])
    fun point(x: Float, y: Float, z: Float): ProjectedPoint {
        val view = rotation.rotate(Vec3(x.toDouble(), y.toDouble(), z.toDouble()) - target)
        val horizontal = (view.x / extent).toFloat()
        val vertical = (view.z / extent).toFloat()
        val depth = (view.y / extent).toFloat()
        val divisor = distance + depth * perspective
        val scale = minOf(width, height) * 1.4f / divisor.coerceAtLeast(.1f)
        val position = Offset(width / 2 + horizontal * scale, height / 2 - vertical * scale)
        return ProjectedPoint(position, depth, divisor > .1f && position.x.isFinite() && position.y.isFinite())
    }
}
