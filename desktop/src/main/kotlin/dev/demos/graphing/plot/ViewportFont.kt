package dev.demos.graphing.plot

import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

private val viewportFont by lazy { FontFamily(
    Font("fonts/PlusJakartaSans-regular.ttf",FontWeight.Normal),Font("fonts/PlusJakartaSans-medium.ttf",FontWeight.Medium),
    Font("fonts/PlusJakartaSans-semibold.ttf",FontWeight.SemiBold),Font("fonts/PlusJakartaSans-bold.ttf",FontWeight.Bold),
) }
internal fun referenceViewportFontFamily()=viewportFont
