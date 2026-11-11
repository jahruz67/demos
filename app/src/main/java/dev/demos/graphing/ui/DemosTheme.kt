package dev.demos.graphing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object DemosColors {
    val Blue = Color(0xFF2D70DA)
    val BlueSoft = Color(0xFFEAF1FC)
    val Header = Color(0xFF202B3A)
    val OnHeader = Color(0xFFF4F7FC)
    val HeaderBorder = Color(0xFF536174)
    val Ink = Color(0xFF202B3A)
    val Muted = Color(0xFF657386)
    val Subtle = Color(0xFF7B8795)
    val Disabled = Color(0xFFB8C2D0)
    val Border = Color(0xFFD6DDE5)
    val Paper = Color.White
    val Surface = Color(0xFFF5F7FA)
    val Keypad = Color(0xFFEEF2F7)
    val KeySecondary = Color(0xFFE3E9F1)
    val Error = Color(0xFFB34436)
}

internal fun interfaceTextStyle(size: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = weight, fontSize = size,
    lineHeight = size * 1.4f, letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

// Each math fragment needs its own line height; inherited body metrics distort roots and scripts.
internal fun mathTextStyle(size: TextUnit) = interfaceTextStyle(size).copy(
    fontFamily = FontFamily.Serif, lineHeight = size * 1.3f, textDirection = TextDirection.Ltr, color = DemosColors.Ink,
)

private val Colors = lightColorScheme(
    primary = DemosColors.Blue, onPrimary = Color.White,
    primaryContainer = DemosColors.BlueSoft, onPrimaryContainer = DemosColors.Blue,
    secondary = DemosColors.Muted, onSecondary = Color.White,
    secondaryContainer = DemosColors.BlueSoft, onSecondaryContainer = DemosColors.Ink,
    tertiary = DemosColors.Blue, onTertiary = Color.White,
    background = DemosColors.Paper, onBackground = DemosColors.Ink,
    surface = DemosColors.Paper, onSurface = DemosColors.Ink,
    surfaceVariant = DemosColors.Surface, onSurfaceVariant = DemosColors.Muted,
    surfaceTint = DemosColors.Blue,
    surfaceContainerLowest = DemosColors.Paper, surfaceContainerLow = DemosColors.Paper,
    surfaceContainer = DemosColors.Surface, surfaceContainerHigh = DemosColors.Surface,
    surfaceContainerHighest = DemosColors.KeySecondary,
    outline = DemosColors.Subtle, outlineVariant = DemosColors.Border,
    error = DemosColors.Error, onError = Color.White,
    errorContainer = Color(0xFFFBECE9), onErrorContainer = DemosColors.Error,
    inverseSurface = DemosColors.Ink, inverseOnSurface = DemosColors.Paper,
    inversePrimary = Color(0xFFBFD3F7), scrim = DemosColors.Ink,
)

private val Type = Typography(
    displayLarge = interfaceTextStyle(57.sp), displayMedium = interfaceTextStyle(45.sp),
    displaySmall = interfaceTextStyle(36.sp), headlineLarge = interfaceTextStyle(32.sp),
    headlineMedium = interfaceTextStyle(28.sp), headlineSmall = interfaceTextStyle(24.sp, FontWeight.Medium),
    titleLarge = interfaceTextStyle(22.sp, FontWeight.Medium),
    titleMedium = interfaceTextStyle(16.sp, FontWeight.Medium),
    titleSmall = interfaceTextStyle(14.sp, FontWeight.Medium),
    bodyLarge = interfaceTextStyle(16.sp), bodyMedium = interfaceTextStyle(14.sp),
    bodySmall = interfaceTextStyle(12.sp), labelLarge = interfaceTextStyle(14.sp, FontWeight.Medium),
    labelMedium = interfaceTextStyle(12.sp, FontWeight.Medium),
    labelSmall = interfaceTextStyle(11.sp, FontWeight.Medium),
)

@Composable
internal fun DemosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors, typography = Type,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(20.dp),
        ),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides DemosColors.Ink,
            LocalTextSelectionColors provides TextSelectionColors(DemosColors.Blue, DemosColors.Blue.copy(alpha = .22f)),
            content = content,
        )
    }
}

@Composable
internal fun GraphControl(description: String, modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier.size(48.dp).semantics { contentDescription = description },
        shape = MaterialTheme.shapes.small, color = DemosColors.Paper,
        contentColor = DemosColors.Muted, border = BorderStroke(1.dp, DemosColors.Border), shadowElevation = 2.dp,
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

@Composable
internal fun DismissControl(description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier.size(40.dp).clip(MaterialTheme.shapes.small).semantics { contentDescription = description }) {
        Canvas(Modifier.size(18.dp)) {
            val inset = size.width * .2f
            drawLine(DemosColors.Subtle, Offset(inset, inset), Offset(size.width - inset, size.height - inset), 1.8.dp.toPx(), StrokeCap.Round)
            drawLine(DemosColors.Subtle, Offset(size.width - inset, inset), Offset(inset, size.height - inset), 1.8.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
internal fun OverflowControl(description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier.size(40.dp).clip(MaterialTheme.shapes.small).semantics { contentDescription = description }) {
        Canvas(Modifier.size(18.dp)) {
            for (y in listOf(.2f, .5f, .8f)) {
                drawCircle(DemosColors.Muted, 1.6.dp.toPx(), Offset(size.width / 2, size.height * y))
            }
        }
    }
}
