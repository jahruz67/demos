package dev.demos.graphing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DemosDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(expanded, onDismissRequest, modifier = modifier, offset = offset,
        shape = MaterialTheme.shapes.medium, containerColor = DemosColors.Paper,
        tonalElevation = 0.dp, shadowElevation = 6.dp, border = BorderStroke(1.dp, DemosColors.Border), content = content)
}

@Composable
internal fun MenuControl(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.small,
        color = DemosColors.Paper, contentColor = DemosColors.Ink, border = BorderStroke(1.dp, DemosColors.Border)) {
        Row(Modifier.heightIn(min = 40.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { content() }
            ChevronIcon(color = DemosColors.Muted)
        }
    }
}

@Composable
internal fun DisclosureHeader(title: String, expanded: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small)
        .clickable(role = Role.Button, onClick = onClick)
        .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChevronIcon(expanded, color = DemosColors.Muted)
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
internal fun ChevronIcon(expanded: Boolean = true, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Canvas(modifier.size(18.dp)) {
        val path = Path().apply {
            if (expanded) {
                moveTo(size.width * .25f, size.height * .38f)
                lineTo(size.width * .5f, size.height * .63f)
                lineTo(size.width * .75f, size.height * .38f)
            } else {
                moveTo(size.width * .38f, size.height * .25f)
                lineTo(size.width * .63f, size.height * .5f)
                lineTo(size.width * .38f, size.height * .75f)
            }
        }
        drawPath(path, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
internal fun CompactNumberField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    label: String? = null, placeholder: String = "", onFocus: (Boolean) -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    val style = interfaceTextStyle(14.sp).copy(color = DemosColors.Ink)
    BasicTextField(value, onValueChange, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), textStyle = style,
        cursorBrush = SolidColor(DemosColors.Blue),
        modifier = modifier.heightIn(min = 36.dp).onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) },
        decorationBox = { inner ->
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                .background(if (focused) DemosColors.BlueSoft else DemosColors.Surface)
                .border(1.dp, if (focused) DemosColors.Blue else DemosColors.Border, MaterialTheme.shapes.small)
                .padding(horizontal = 8.dp, vertical = 7.dp)) {
                if (label != null) Text(label, color = DemosColors.Muted, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 4.dp))
                Box {
                    if (value.isEmpty()) Text(placeholder, style = style, color = DemosColors.Subtle, maxLines = 1)
                    inner()
                }
            }
        })
}

@Composable
internal fun PlaybackIcon(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(16.dp)) {
        if (playing) {
            for (x in listOf(.35f, .65f)) drawLine(color, Offset(size.width * x, size.height * .2f),
                Offset(size.width * x, size.height * .8f), 2.dp.toPx(), StrokeCap.Round)
        } else {
            val path = Path().apply {
                moveTo(size.width * .3f, size.height * .15f)
                lineTo(size.width * .8f, size.height * .5f)
                lineTo(size.width * .3f, size.height * .85f)
                close()
            }
            drawPath(path, color)
        }
    }
}

@Composable
internal fun CheckmarkIcon(modifier: Modifier = Modifier, color: Color = DemosColors.Blue) {
    Canvas(modifier.size(16.dp)) {
        val path = Path().apply {
            moveTo(size.width * .15f, size.height * .5f)
            lineTo(size.width * .4f, size.height * .75f)
            lineTo(size.width * .85f, size.height * .25f)
        }
        drawPath(path, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
