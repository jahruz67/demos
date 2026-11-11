package dev.demos.graphing.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import dev.demos.graphing.data.GraphImages

@Composable
internal fun PlatformUi(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    CompositionLocalProvider(LocalAppConfiguration provides AppConfiguration(configuration.screenWidthDp, configuration.screenHeightDp), content = content)
}

@Composable
internal fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

internal fun platformTextStyle() = PlatformTextStyle(includeFontPadding = false)

@Composable
internal fun rememberImageLoader(): (String) -> ImageBitmap? {
    val context = LocalContext.current.applicationContext
    return remember(context) { { source -> GraphImages.decode(context, source)?.asImageBitmap() } }
}

@Composable
internal fun rememberUiActions(): UiActions {
    val context = LocalContext.current.applicationContext
    val actions = remember(context) { AndroidUiActions(context) }
    DisposableEffect(actions) { onDispose { actions.close() } }
    return actions
}

private class AndroidUiActions(private val context: Context) : UiActions {
    override val showKeypadOnInput = true
    private val clipboard get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var speech: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    override fun readClipboard() = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    override fun copy(text: String) { clipboard.setPrimaryClip(ClipData.newPlainText("Result", text)) }
    override fun speak(text: String) {
        if (ready) speech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "expression")
        else {
            pending = text
            if (speech == null) speech = TextToSpeech(context) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) pending?.let(::speak)
                pending = null
            }
        }
    }
    fun close() { speech?.shutdown() }
}
