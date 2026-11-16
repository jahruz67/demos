package dev.demos.graphing.desktop

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

/** Opt-in milestones for measuring the packaged launcher, including JVM startup. */
internal object StartupTrace {
    private val output = System.getenv("DEMOS_STARTUP_TRACE")?.let(::File)
    private val drawn = AtomicBoolean()

    fun mark(stage: String) {
        val file = output ?: return
        file.appendText("$stage,${System.currentTimeMillis()}\n")
    }

    fun frameDrawn() {
        if (output == null || !drawn.compareAndSet(false, true)) return
        // Run after the draw callback returns to the event queue.
        SwingUtilities.invokeLater { mark("first-frame") }
    }
}
