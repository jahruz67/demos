package dev.demos.graphing.plot

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.demos.graphing.R

private val viewportFont by lazy { FontFamily(
    Font(R.font.viewport_jakarta_regular,FontWeight.Normal),Font(R.font.viewport_jakarta_medium,FontWeight.Medium),
    Font(R.font.viewport_jakarta_semibold,FontWeight.SemiBold),Font(R.font.viewport_jakarta_bold,FontWeight.Bold),
) }
internal fun referenceViewportFontFamily()=viewportFont
