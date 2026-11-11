package dev.demos.graphing.ui

import androidx.compose.runtime.staticCompositionLocalOf

internal data class AppConfiguration(val screenWidthDp: Int, val screenHeightDp: Int) {
    val smallestScreenWidthDp get() = minOf(screenWidthDp, screenHeightDp)
    val orientation get() = if (screenWidthDp >= screenHeightDp) ORIENTATION_LANDSCAPE else 1
    companion object { const val ORIENTATION_LANDSCAPE = 2 }
}

internal val LocalAppConfiguration = staticCompositionLocalOf { AppConfiguration(1280, 900) }

internal interface UiActions {
    val showKeypadOnInput: Boolean
    fun readClipboard(): String?
    fun copy(text: String)
    fun speak(text: String)
}
