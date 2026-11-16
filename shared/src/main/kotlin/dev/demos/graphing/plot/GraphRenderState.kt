package dev.demos.graphing.plot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Native exports wait for the shared sampling/rendering pipeline and its drawn frame. */
class GraphRenderState {
    internal var ready by mutableStateOf(false)

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
