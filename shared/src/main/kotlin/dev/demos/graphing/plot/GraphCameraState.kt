package dev.demos.graphing.plot

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal enum class Graph3DTool { Rotate, Pan }
internal enum class Graph3DView { Standard, XY, XZ, YZ, Stop }

internal class GraphCameraState {
    var tool by mutableStateOf(Graph3DTool.Rotate)
    var spinning by mutableStateOf(false)
    var requestedView = Graph3DView.Standard
        private set
    var viewRequestCounter by mutableIntStateOf(0)
        private set
    fun requestView(view: Graph3DView) {
        requestedView = view
        viewRequestCounter++
    }
    var rotationW = 1.0
    var rotationX = 0.0
    var rotationY = 0.0
    var rotationZ = 0.0
    // Inverse view scale, retained in the saved format independently of eye position.
    var distance = 19f
    var targetX = 0.0
    var targetY = 0.0
    var targetZ = 0.0
    var resetCounter by mutableIntStateOf(0)
    var configuredBounds by mutableStateOf<List<Double?>?>(null)
    var sceneBounds: List<Double>? = null

    companion object {
        val Saver = Saver<GraphCameraState, List<Double?>>(
            save = { listOf(it.rotationW, it.rotationX, it.rotationY, it.rotationZ,
                it.targetX, it.targetY, it.targetZ, it.distance.toDouble(), it.resetCounter.toDouble()) +
                (if (it.configuredBounds != null && it.sceneBounds != null) it.configuredBounds!! + it.sceneBounds!! else emptyList()) },
            restore = { values -> GraphCameraState().apply {
                rotationW = values[0]!!; rotationX = values[1]!!; rotationY = values[2]!!; rotationZ = values[3]!!
                targetX = values[4]!!; targetY = values[5]!!; targetZ = values[6]!!; distance = values[7]!!.toFloat()
                resetCounter = values[8]!!.toInt()
                if (values.size == 21) {
                    configuredBounds = values.subList(9, 15)
                    sceneBounds = values.subList(15, 21).map { it!! }
                }
            } },
        )
    }
}
