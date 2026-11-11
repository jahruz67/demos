package dev.demos.graphing.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.demos.graphing.math.StatisticalInference
import dev.demos.graphing.math.TableEvaluation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.sqrt

@Composable
internal fun ImageWorksheet(row: ExpressionRow, number: Int, selected: Boolean, onSelect: () -> Unit, onChange: (ImageData) -> Unit,
    onDelete: () -> Unit, onVisibility: () -> Unit) {
    val image = parseImageData(row.text) ?: return
    val loadImage = rememberImageLoader()
    val bitmap by produceState<ImageBitmap?>(null, image.uri) { value = withContext(Dispatchers.IO) { loadImage(image.uri) } }
    ItemFrame(number, selected, "image", onSelect, onDelete) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            bitmap?.let { Image(it, image.name, Modifier.size(54.dp).clip(MaterialTheme.shapes.small).clickable(onClick = onVisibility)) }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                BasicTextField(image.name, { onChange(image.copy(name = it)) }, singleLine = true, textStyle = interfaceTextStyle(16.sp).copy(color = DemosColors.Ink), modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onSelect() })
                TextButton(onClick = onVisibility, shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(if (row.hidden) "Show image" else "Hide image", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (selected) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberSetting("Center x", image.x.toString(), Modifier.weight(1f), onSelect) { it.toDoubleOrNull()?.takeIf(Double::isFinite)?.let { x -> onChange(image.copy(x = x)) } }
                NumberSetting("Center y", image.y.toString(), Modifier.weight(1f), onSelect) { it.toDoubleOrNull()?.takeIf(Double::isFinite)?.let { y -> onChange(image.copy(y = y)) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberSetting("Width", image.width.toString(), Modifier.weight(1f), onSelect) { it.toDoubleOrNull()?.takeIf { x -> x.isFinite() && x > 0 }?.let { x -> onChange(image.copy(width = x)) } }
                NumberSetting("Height", image.height.toString(), Modifier.weight(1f), onSelect) { it.toDoubleOrNull()?.takeIf { x -> x.isFinite() && x > 0 }?.let { x -> onChange(image.copy(height = x)) } }
                NumberSetting("Angle", image.angle.toString(), Modifier.weight(1f), onSelect) { it.toDoubleOrNull()?.takeIf(Double::isFinite)?.let { angle -> onChange(image.copy(angle = angle)) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Opacity", fontSize = 11.sp, color = DemosColors.Muted)
                Slider(image.opacity.toFloat(), { onChange(image.copy(opacity = it.toDouble())) }, modifier = Modifier.weight(1f).height(30.dp))
            }
        }
    }
}

@Composable
internal fun InferenceWorksheet(row: ExpressionRow, number: Int, state: CalculatorUiState, onSelect: () -> Unit,
    onChange: (InferenceData) -> Unit, onDelete: () -> Unit) {
    val data = parseInferenceData(row.text) ?: return
    val tables = state.rows.mapNotNull { r -> parseTableData(r.text)?.let { r.id to it } }
    val tableIndex = tables.indexOfFirst { it.first == data.tableId }
    val values = if (tableIndex < 0) emptyList() else TableEvaluation.columns(tables[tableIndex].second, tableIndex + 1, state.sliders.associate { it.name to it.value }, !state.degreeMode)
        .getOrNull(data.column).orEmpty().filterNotNull()
    val n = if (data.tableId != null) values.size else data.count.toIntOrNull() ?: 0
    val mean = if (data.tableId != null && values.isNotEmpty()) values.average() else data.mean.toDoubleOrNull()
    val deviation = if (data.tableId != null && n > 1 && mean != null) sqrt(values.sumOf { (it - mean).pow(2) } / (n - 1)) else data.deviation.toDoubleOrNull()
    val confidence = data.confidence.toDoubleOrNull() ?: .95
    val result = if (data.kind == "proportion") StatisticalInference.proportion(data.mean.toDoubleOrNull() ?: Double.NaN, n, data.nullValue.toDoubleOrNull() ?: .5, confidence, data.alternative)
        else StatisticalInference.mean(mean ?: Double.NaN, deviation ?: Double.NaN, n, data.nullValue.toDoubleOrNull() ?: 0.0, confidence, data.alternative)
    ItemFrame(number, row.id == state.activeId, "inference", onSelect, onDelete) {
        var kindMenu by remember { mutableStateOf(false) }
        Box {
            MenuControl(Modifier.fillMaxWidth(), onClick = { onSelect(); kindMenu = true }) {
                Text(if (data.kind == "mean") "One-Sample t-Test" else "One-Proportion z-Test", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            DemosDropdownMenu(kindMenu, { kindMenu = false }) {
                DropdownMenuItem(text = { Text("One-Sample t-Test") }, onClick = { onChange(data.copy(kind = "mean", nullValue = "0")); kindMenu = false })
                DropdownMenuItem(text = { Text("One-Proportion z-Test") }, onClick = { onChange(data.copy(kind = "proportion", nullValue = "0.5", tableId = null)); kindMenu = false })
            }
        }
        if (data.kind == "mean") {
            var sourceMenu by remember { mutableStateOf(false) }
            Box {
                MenuControl(Modifier.fillMaxWidth(), onClick = { sourceMenu = true }) {
                    if (tableIndex < 0) Text("Summary statistics", style = MaterialTheme.typography.bodyMedium)
                    else MathExpression(tables[tableIndex].second.header(data.column, tableIndex + 1), 18)
                }
                DemosDropdownMenu(sourceMenu, { sourceMenu = false }) {
                    DropdownMenuItem(text = { Text("Summary statistics") }, onClick = { onChange(data.copy(tableId = null)); sourceMenu = false })
                    tables.forEachIndexed { i, (id, table) -> repeat(table.columnCount) { c ->
                        DropdownMenuItem(text = { MathExpression(table.header(c, i + 1), 17) }, onClick = { onChange(data.copy(tableId = id, column = c)); sourceMenu = false })
                    } }
                }
            }
        }
        if (data.tableId == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberSetting(if (data.kind == "mean") "Mean" else "Successes", data.mean, Modifier.weight(1f), onSelect) { onChange(data.copy(mean = it)) }
            if (data.kind == "mean") NumberSetting("Std. dev.", data.deviation, Modifier.weight(1f), onSelect) { onChange(data.copy(deviation = it)) }
            NumberSetting("n", data.count, Modifier.weight(1f), onSelect) { onChange(data.copy(count = it)) }
        } else Text("n = $n, mean = ${number(mean ?: 0.0)}", color = DemosColors.Muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberSetting("Null value", data.nullValue, Modifier.weight(1f), onSelect) { onChange(data.copy(nullValue = it)) }
            NumberSetting("Confidence", data.confidence, Modifier.weight(1f), onSelect) { onChange(data.copy(confidence = it)) }
            var alternatives by remember { mutableStateOf(false) }
            Box {
                MenuControl(Modifier.width(72.dp), onClick = { alternatives = true }) { MathExpression(data.alternative, 18) }
                DemosDropdownMenu(alternatives, { alternatives = false }) { listOf("≠", "<", ">").forEach { a -> DropdownMenuItem(text = { Text(a) }, onClick = { onChange(data.copy(alternative = a)); alternatives = false }) } }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 7.dp))
        if (result == null) Text("Enter valid sample statistics to calculate.", color = DemosColors.Muted, fontSize = 11.sp)
        else {
            MathExpression("${if (result.degreesOfFreedom == null) "z" else "t"}=${number(result.statistic)}", 19)
            Text("p = ${number(result.probability)}" + (result.degreesOfFreedom?.let { "     df = $it" } ?: ""), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            Text("${number(confidence * 100)}% interval: (${number(result.lower)}, ${number(result.upper)})", fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun ItemFrame(number: Int, selected: Boolean, icon: String, onSelect: () -> Unit, onDelete: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).border(if (selected) 1.dp else .5.dp, if (selected) DemosColors.Blue else DemosColors.Border)) {
        Column(Modifier.width(34.dp).fillMaxHeight().background(if (selected) DemosColors.Blue else DemosColors.Surface).clickable(onClick = onSelect)) {
            Text("$number", style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White else DemosColors.Muted, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
            WorksheetItemIcon(icon, Modifier.align(Alignment.CenterHorizontally).size(24.dp), if (selected) Color.White else DemosColors.Muted)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp, top = 6.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(icon.replaceFirstChar { it.uppercaseChar() }, color = DemosColors.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                DismissControl("Delete item $number", onClick = onDelete)
            }
            content()
        }
    }
}

@Composable
private fun NumberSetting(label: String, source: String, modifier: Modifier, onSelect: () -> Unit, onChange: (String) -> Unit) {
    CompactNumberField(source, onChange, modifier, label = label, onFocus = { if (it) onSelect() })
}

private fun number(value: Double) = java.lang.String.format(java.util.Locale.US, "%.6g", value)
