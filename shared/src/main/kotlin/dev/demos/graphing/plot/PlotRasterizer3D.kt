package dev.demos.graphing.plot

import kotlinx.coroutines.CancellationException
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.abs
import kotlin.math.sqrt

internal data class PlotRasterVertex(
    val x: Float,
    val y: Float,
    val depth: Float,
    val reciprocalW: Float,
)

internal data class PlotRasterPrimitive(
    val vertices: List<PlotRasterVertex>,
    val color: Int,
    val filled: Boolean = false,
    val width: Float = 1f,
    val depthWrite: Boolean = true,
)

/** Depth-test opaque geometry, then composite transparent fragments back to front per pixel. */
internal fun rasterizePlot3D(
    primitives: List<PlotRasterPrimitive>,
    width: Int,
    height: Int,
    cancelled: () -> Boolean,
): IntArray {
    require(width > 0 && height > 0 && width.toLong() * height <= Int.MAX_VALUE)
    val buffer = RasterBuffer(width, height, cancelled)
    var top = 0
    while (top < height) {
        buffer.beginStrip(top)
        for (opaquePass in listOf(true, false)) {
            buffer.opaquePass = opaquePass
            for (primitive in primitives) {
                buffer.checkCancellation()
                val opaque = primitive.color ushr 24 == 255 && primitive.depthWrite
                if (opaque != opaquePass || primitive.color ushr 24 == 0) continue
                if (primitive.vertices.any { !it.valid() }) continue
                buffer.color = primitive.color
                rasterPrimitive(primitive, buffer)
            }
        }
        buffer.compositeStrip()
        top = buffer.bottom + 1
    }
    return buffer.result()
}

private fun rasterPrimitive(primitive: PlotRasterPrimitive, buffer: RasterBuffer) {
    val vertices = primitive.vertices
    when {
        primitive.filled && vertices.size >= 3 -> {
            for (i in 1 until vertices.lastIndex) {
                buffer.checkCancellation()
                rasterTriangle(vertices[0], vertices[i], vertices[i + 1], buffer)
            }
        }
        vertices.size == 1 -> rasterLine(vertices[0], vertices[0], primitive.width, buffer)
        else -> for (i in 0 until vertices.lastIndex) {
            rasterLine(vertices[i], vertices[i + 1], primitive.width, buffer)
        }
    }
}

private fun PlotRasterVertex.valid() =
    x.isFinite() && y.isFinite() && depth.isFinite() && reciprocalW.isFinite() && reciprocalW > 0f

private class RasterBuffer(val width: Int, val height: Int, private val cancelled: () -> Boolean) {
    private val pixels = IntArray(width * height) { -1 }
    // Keep depth/layer storage local to about 64K pixels, reusing it for every strip.
    // Every primitive is still processed; the raster and geometry are never truncated.
    private val stripHeight = minOf(height, maxOf(1, 65_536 / width))
    private val opaqueDepths = FloatArray(width * stripHeight)
    private val heads = IntArray(opaqueDepths.size)
    private var fragmentDepths = FloatArray(minOf(heads.size, 4096))
    private var fragmentColors = IntArray(fragmentDepths.size)
    private var nextFragments = IntArray(fragmentDepths.size)
    private var fragmentCount = 0
    var opaquePass = true
    var color = 0
    var top = 0
        private set
    var bottom = -1
        private set

    fun beginStrip(firstRow: Int) {
        checkCancellation()
        top = firstRow
        bottom = top + minOf(stripHeight, height - top) - 1
        opaqueDepths.fill(Float.POSITIVE_INFINITY)
        heads.fill(-1)
        fragmentCount = 0
    }

    fun checkCancellation() {
        if (cancelled()) throw CancellationException("A newer graph frame is ready")
    }

    fun emit(index: Int, depth: Double) {
        if (!depth.isFinite()) return
        val sampledDepth = depth.toFloat()
        val local = index - top * width
        if (sampledDepth > opaqueDepths[local]) return
        if (opaquePass) {
            opaqueDepths[local] = sampledDepth
            pixels[index] = color
            return
        }
        if (fragmentCount and 16383 == 0) checkCancellation()
        if (fragmentCount == fragmentDepths.size) growFragments()
        val added = fragmentCount++
        fragmentDepths[added] = sampledDepth
        fragmentColors[added] = color
        var previous = -1
        var current = heads[local]
        // Larger camera-space depth is farther away. Equal-depth later primitives go last.
        var traversed = 0
        while (current != -1 && fragmentDepths[current] >= sampledDepth) {
            if (traversed++ and 1023 == 0) checkCancellation()
            previous = current
            current = nextFragments[current]
        }
        nextFragments[added] = current
        if (previous == -1) heads[local] = added else nextFragments[previous] = added
    }

    private fun growFragments() {
        val capacity = minOf(Int.MAX_VALUE.toLong(), maxOf(16L, fragmentDepths.size.toLong() * 2)).toInt()
        check(capacity > fragmentCount) { "The graph contains too many overlapping fragments" }
        fragmentDepths = fragmentDepths.copyOf(capacity)
        fragmentColors = fragmentColors.copyOf(capacity)
        nextFragments = nextFragments.copyOf(capacity)
    }

    fun compositeStrip() {
        for (y in top..bottom) {
            checkCancellation()
            for (x in 0 until width) {
                val index = y * width + x
                var fragment = heads[index - top * width]
                var result = pixels[index]
                var traversed = 0
                while (fragment != -1) {
                    if (traversed++ and 1023 == 0) checkCancellation()
                    result = sourceOver(fragmentColors[fragment], result)
                    fragment = nextFragments[fragment]
                }
                pixels[index] = result
            }
        }
    }

    fun result(): IntArray = pixels
}

private fun sourceOver(source: Int, destination: Int): Int {
    val alpha = source ushr 24
    val inverse = 255 - alpha
    fun channel(shift: Int) = (((source ushr shift and 255) * alpha +
        (destination ushr shift and 255) * inverse + 127) / 255)
    // The graph's background is opaque white, so every composed pixel remains opaque.
    return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

private fun edge(a: PlotRasterVertex, b: PlotRasterVertex, x: Double, y: Double): Double =
    (b.x.toDouble() - a.x) * (y - a.y) - (b.y.toDouble() - a.y) * (x - a.x)

private fun topLeft(a: PlotRasterVertex, b: PlotRasterVertex): Boolean =
    b.y < a.y || (b.y == a.y && b.x > a.x)

private fun rasterTriangle(a: PlotRasterVertex, second: PlotRasterVertex,
    third: PlotRasterVertex, buffer: RasterBuffer) {
    val signedArea = edge(a, second, third.x.toDouble(), third.y.toDouble())
    if (signedArea == 0.0) return
    val b = if (signedArea > 0) second else third
    val c = if (signedArea > 0) third else second
    val area = kotlin.math.abs(signedArea)
    val left = maxOf(0, ceil(minOf(a.x, b.x, c.x).toDouble() - .5).toInt())
    val right = minOf(buffer.width - 1, floor(maxOf(a.x, b.x, c.x).toDouble() - .5).toInt())
    val top = maxOf(buffer.top, ceil(minOf(a.y, b.y, c.y).toDouble() - .5).toInt())
    val bottom = minOf(buffer.bottom, floor(maxOf(a.y, b.y, c.y).toDouble() - .5).toInt())
    if (left > right || top > bottom) return
    val edgeA = topLeft(b, c)
    val edgeB = topLeft(c, a)
    val edgeC = topLeft(a, b)
    for (y in top..bottom) {
        buffer.checkCancellation()
        for (x in left..right) {
            val first = edge(b, c, x + .5, y + .5)
            val next = edge(c, a, x + .5, y + .5)
            val last = edge(a, b, x + .5, y + .5)
            if (first < 0 || (first == 0.0 && !edgeA) || next < 0 || (next == 0.0 && !edgeB) ||
                last < 0 || (last == 0.0 && !edgeC)) continue
            val wa = first / area * a.reciprocalW
            val wb = next / area * b.reciprocalW
            val wc = last / area * c.reciprocalW
            val weight = wa + wb + wc
            if (weight > 0.0) buffer.emit(y * buffer.width + x,
                (wa * a.depth + wb * b.depth + wc * c.depth) / weight)
        }
    }
}

private fun rasterLine(a: PlotRasterVertex, b: PlotRasterVertex, width: Float, buffer: RasterBuffer) {
    if (!width.isFinite() || width <= 0f) return
    // A subpixel stroke can miss every sample after the output raster is downscaled.
    val minimumRadius = if (a.x == b.x && a.y == b.y) sqrt(.5) else .5
    val radius = maxOf(minimumRadius, width.toDouble() / 2)
    val left = maxOf(0, ceil(minOf(a.x, b.x) - radius - .5).toInt())
    val right = minOf(buffer.width - 1, floor(maxOf(a.x, b.x) + radius - .5).toInt())
    val top = maxOf(buffer.top, ceil(minOf(a.y, b.y) - radius - .5).toInt())
    val bottom = minOf(buffer.bottom, floor(maxOf(a.y, b.y) + radius - .5).toInt())
    if (left > right || top > bottom) return
    val dx = b.x.toDouble() - a.x
    val dy = b.y.toDouble() - a.y
    val lengthSquared = dx * dx + dy * dy
    val xMajor = abs(dx) >= abs(dy) && dx != 0.0
    val originMajor = if (xMajor) a.x.toDouble() else a.y.toDouble()
    val originMinor = if (xMajor) a.y.toDouble() else a.x.toDouble()
    val deltaMajor = if (xMajor) dx else dy
    val deltaMinor = if (xMajor) dy else dx
    val firstMajor = if (xMajor) left else top
    val lastMajor = if (xMajor) right else bottom
    val firstMinor = if (xMajor) top else left
    val lastMinor = if (xMajor) bottom else right
    val slope = if (deltaMajor == 0.0) 0.0 else deltaMinor / deltaMajor
    val halfSpan = radius * sqrt(1 + slope * slope)
    // Visit only a narrow capsule along the segment, rather than its whole bounding rectangle.
    for (major in firstMajor..lastMajor) {
        buffer.checkCancellation()
        val centerAmount = if (deltaMajor == 0.0) 0.0 else
            ((major + .5 - originMajor) / deltaMajor).coerceIn(0.0, 1.0)
        val center = originMinor + centerAmount * deltaMinor
        val start = maxOf(firstMinor, ceil(center - halfSpan - .5).toInt())
        val end = minOf(lastMinor, floor(center + halfSpan - .5).toInt())
        for (minor in start..end) {
            val x = if (xMajor) major else minor
            val y = if (xMajor) minor else major
            val amount = if (lengthSquared == 0.0) 0.0 else
                (((x + .5 - a.x) * dx + (y + .5 - a.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val offsetX = x + .5 - a.x - amount * dx
            val offsetY = y + .5 - a.y - amount * dy
            if (offsetX * offsetX + offsetY * offsetY > radius * radius) continue
            val wa = (1 - amount) * a.reciprocalW
            val wb = amount * b.reciprocalW
            buffer.emit(y * buffer.width + x, (wa * a.depth + wb * b.depth) / (wa + wb))
        }
    }
}
