package dev.demos.graphing.plot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import java.util.Locale
import kotlin.math.*

/** Desktop canvas renderer for the same sampled meshes used by Android's GLES renderer. */
@Composable
internal fun PlotSurface3D(meshes: List<Mesh3D>, settings: PlotSettings, resetCounter: Int,
    cameraState: GraphCameraState, zoom: Float, onInspect: (String) -> Unit) {
    var yaw by remember { mutableFloatStateOf(cameraState.yaw) }
    var pitch by remember { mutableFloatStateOf(cameraState.pitch) }
    var distance by remember { mutableFloatStateOf(cameraState.distance) }
    var previousZoom by remember { mutableFloatStateOf(1f) }
    val currentMeshes by rememberUpdatedState(meshes)
    val currentSettings by rememberUpdatedState(settings)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    val labels = remember(measurer, density) { PlotLabels(measurer, density) }
    val bounds = listOf(settings.xMin, settings.xMax, settings.yMin, settings.yMax, settings.zMin, settings.zMax)
    LaunchedEffect(resetCounter, bounds) {
        if (resetCounter > 0 || cameraState.configuredBounds != bounds) {
            yaw = 38f; pitch = 28f; distance = 19f
            cameraState.configuredBounds = bounds
        }
    }
    LaunchedEffect(zoom) { distance = (distance * previousZoom / zoom).coerceIn(2f, 200f); previousZoom = zoom }
    SideEffect { cameraState.yaw = yaw; cameraState.pitch = pitch; cameraState.distance = distance }

    Canvas(Modifier.fillMaxSize()
        .pointerInput(Unit) {
            detectDragGestures { change, drag ->
                change.consume()
                yaw += drag.x * .4f
                pitch = (pitch + drag.y * .4f).coerceIn(-89f, 89f)
            }
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Scroll) {
                        val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                        distance = (distance * exp(delta * .12f)).coerceIn(2f, 200f)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
        .pointerInput(Unit) {
            detectTapGestures { tap ->
                val projection = Projection(currentSettings, yaw, pitch, distance, size.width.toFloat(), size.height.toFloat())
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
        val projection = Projection(settings, yaw, pitch, distance, size.width, size.height)
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
        if (settings.showGrid) {
            for (index in 0..10) {
                val x = projection.xMin + (projection.xMax - projection.xMin) * index / 10
                val y = projection.yMin + (projection.yMax - projection.yMin) * index / 10
                faces += Face(listOf(projection.point(x, projection.yMin, 0f), projection.point(x, projection.yMax, 0f)), Color(0xFFDDDDDD))
                faces += Face(listOf(projection.point(projection.xMin, y, 0f), projection.point(projection.xMax, y, 0f)), Color(0xFFDDDDDD))
            }
        }
        if (settings.showAxes) {
            faces += Face(listOf(projection.point(projection.xMin, 0f, 0f), projection.point(projection.xMax, 0f, 0f)), Color(graphAxisColors[0]))
            faces += Face(listOf(projection.point(0f, projection.yMin, 0f), projection.point(0f, projection.yMax, 0f)), Color(graphAxisColors[1]))
            faces += Face(listOf(projection.point(0f, 0f, projection.zMin), projection.point(0f, 0f, projection.zMax)), Color(graphAxisColors[2]))
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
                if (settings.appearance.numbers) for (i in 1..4) {
                    val value = low + (high - low) * i / 5
                    if (abs(value) > 1e-6f) label(value, String.format(Locale.US, "%.3g", value))
                }
            }
        }
    }
}

private data class ProjectedPoint(val position: Offset, val depth: Float, val visible: Boolean)
private data class Face(val points: List<ProjectedPoint>, val color: Color, val filled: Boolean = false, val shade: Float = 1f)

private class Projection(settings: PlotSettings, yaw: Float, pitch: Float, private val distance: Float,
    private val width: Float, private val height: Float) {
    val xMin = (settings.xMin ?: -5.0).toFloat(); val xMax = (settings.xMax ?: 5.0).toFloat()
    val yMin = (settings.yMin ?: -5.0).toFloat(); val yMax = (settings.yMax ?: 5.0).toFloat()
    val zMin = (settings.zMin ?: -5.0).toFloat(); val zMax = (settings.zMax ?: 5.0).toFloat()
    private val extent = maxOf(xMax - xMin, yMax - yMin, zMax - zMin).coerceAtLeast(.0001f) / 10
    private val cy = cos(yaw * PI / 180).toFloat(); private val sy = sin(yaw * PI / 180).toFloat()
    private val cp = cos(pitch * PI / 180).toFloat(); private val sp = sin(pitch * PI / 180).toFloat()
    private val perspective = settings.appearance.perspective.coerceIn(0f, 2f)
    fun point(vertices: FloatArray, index: Int) = point(vertices[index], vertices[index + 1], vertices[index + 2])
    fun point(x: Float, y: Float, z: Float): ProjectedPoint {
        val nx = (x - (xMin + xMax) / 2) / extent
        val ny = (y - (yMin + yMax) / 2) / extent
        val nz = (z - (zMin + zMax) / 2) / extent
        val horizontal = cy * nx - sy * ny
        val forward = sy * nx + cy * ny
        val vertical = cp * nz - sp * forward
        val depth = cp * forward + sp * nz
        val divisor = distance + depth * perspective
        val scale = minOf(width, height) * 1.4f / divisor.coerceAtLeast(.1f)
        val position = Offset(width / 2 + horizontal * scale, height / 2 - vertical * scale)
        return ProjectedPoint(position, depth, divisor > .1f && position.x.isFinite() && position.y.isFinite())
    }
}
