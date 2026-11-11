package dev.demos.graphing.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.PlatformTextStyle
import dev.demos.graphing.desktop.WindowsImages
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.util.Base64

@Composable
internal fun PlatformUi(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalAppConfiguration provides AppConfiguration(maxWidth.value.toInt(), maxHeight.value.toInt()), content = content)
    }
}

// Escape is handled on the desktop window; Android retains its system back stack.
@Composable
internal fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

internal fun platformTextStyle(): PlatformTextStyle? = null

@Composable
internal fun rememberImageLoader(): (String) -> ImageBitmap? = remember { { source -> WindowsImages.decode(source) } }

@Composable
internal fun rememberUiActions(): UiActions {
    val actions = remember { WindowsUiActions() }
    DisposableEffect(actions) { onDispose { actions.close() } }
    return actions
}

private class WindowsUiActions : UiActions {
    override val showKeypadOnInput = false
    private var speech: Process? = null
    private val clipboard get() = Toolkit.getDefaultToolkit().systemClipboard
    override fun readClipboard(): String? = runCatching { clipboard.getData(DataFlavor.stringFlavor) as? String }.getOrNull()
    override fun copy(text: String) { clipboard.setContents(StringSelection(text), null) }
    override fun speak(text: String) {
        speech?.destroyForcibly()
        // Encode user text as data; it is never interpolated into PowerShell code.
        val data = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
        val script = "Add-Type -AssemblyName System.Speech; " +
            "\u0024voice = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
            "try { \u0024voice.Speak([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('$data'))) } finally { \u0024voice.Dispose() }"
        val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        speech = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-EncodedCommand", encoded)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    }
    fun close() { speech?.destroyForcibly() }
}
