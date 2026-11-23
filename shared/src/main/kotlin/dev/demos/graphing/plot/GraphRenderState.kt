package dev.demos.graphing.plot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.demos.graphing.ui.GraphBounds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Exports wait for the native sampling/rendering pipeline and its drawn frame. */
class GraphRenderState {
    internal var ready by mutableStateOf(false)
    internal var visibleBounds2D by mutableStateOf<GraphBounds?>(null)
    internal var boundsResetCounter by mutableIntStateOf(0)
        private set
    internal var boundsReset3DCounter by mutableIntStateOf(0)
        private set

    internal fun resetBounds(is3D: Boolean = false) {
        if (is3D) boundsReset3DCounter++ else boundsResetCounter++
    }

    fun invalidate() { ready = false }

    suspend fun awaitReady(waitFrame: suspend () -> Unit) {
        val drawn = withTimeoutOrNull(30_000) {
            do {
                snapshotFlow { ready }.first { it }
                waitFrame()
                waitFrame()
            } while (!ready)
            true
        }
        check(drawn == true) { "The graph is still rendering; try exporting again" }
    }
}
