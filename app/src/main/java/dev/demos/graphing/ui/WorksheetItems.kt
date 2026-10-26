package dev.demos.graphing.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.demos.graphing.data.GraphImages
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
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<android.graphics.Bitmap?>(null, image.uri) { value = withContext(Dispatchers.IO) { GraphImages.decode(context, image.uri) } }
    ItemFrame(number, selected, "▧", onSelect, onDelete) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            bitmap?.let { Image(it.asImageBitmap(), image.name, Modifier.size(54.dp).clickable(onClick = onVisibility)) }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                BasicTextField(image.name, { onChange(image.copy(name = it)) }, textStyle = TextStyle(fontSize = 16.sp), modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onSelect() })
                Text(if (row.hidden) "Show image" else "Hide image", fontSize = 11.sp, color = Color(0xFF2D70DA), modifier = Modifier.padding(top = 7.dp).clickable(onClick = onVisibility))
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
                Text("Opacity", fontSize = 11.sp, color = Color.Gray)
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
    ItemFrame(number, row.id == state.activeId, "∩", onSelect, onDelete) {
        var kindMenu by remember { mutableStateOf(false) }
        Box {
            Text((if (data.kind == "mean") "One-Sample t-Test" else "One-Proportion z-Test") + "  ▾", fontSize = 15.sp, modifier = Modifier.clickable { onSelect(); kindMenu = true }.padding(vertical = 8.dp))
            DropdownMenu(kindMenu, { kindMenu = false }, modifier = Modifier.background(Color.White)) {
                DropdownMenuItem(text = { Text("One-Sample t-Test") }, onClick = { onChange(data.copy(kind = "mean", nullValue = "0")); kindMenu = false })
                DropdownMenuItem(text = { Text("One-Proportion z-Test") }, onClick = { onChange(data.copy(kind = "proportion", nullValue = "0.5", tableId = null)); kindMenu = false })
            }
        }
        if (data.kind == "mean") {
            var sourceMenu by remember { mutableStateOf(false) }
            Box {
                Text((if (tableIndex < 0) "Summary statistics" else tables[tableIndex].second.header(data.column, tableIndex + 1)) + "  ▾", color = Color(0xFF666666), fontSize = 12.sp, modifier = Modifier.clickable { sourceMenu = true }.padding(vertical = 7.dp))
                DropdownMenu(sourceMenu, { sourceMenu = false }, modifier = Modifier.background(Color.White)) {
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
        } else Text("n = $n, mean = ${number(mean ?: 0.0)}", color = Color.Gray, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberSetting("Null value", data.nullValue, Modifier.weight(1f), onSelect) { onChange(data.copy(nullValue = it)) }
            NumberSetting("Confidence", data.confidence, Modifier.weight(1f), onSelect) { onChange(data.copy(confidence = it)) }
            var alternatives by remember { mutableStateOf(false) }
            Box {
                Text(data.alternative + " ▾", fontSize = 19.sp, modifier = Modifier.clickable { alternatives = true }.padding(5.dp))
                DropdownMenu(alternatives, { alternatives = false }) { listOf("≠", "<", ">").forEach { a -> DropdownMenuItem(text = { Text(a) }, onClick = { onChange(data.copy(alternative = a)); alternatives = false }) } }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 7.dp))
        if (result == null) Text("Enter valid sample statistics to calculate.", color = Color.Gray, fontSize = 11.sp)
        else {
            MathExpression("${if (result.degreesOfFreedom == null) "z" else "t"}=${number(result.statistic)}", 19)
            Text("p = ${number(result.probability)}" + (result.degreesOfFreedom?.let { "     df = $it" } ?: ""), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            Text("${number(confidence * 100)}% interval: (${number(result.lower)}, ${number(result.upper)})", fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun ItemFrame(number: Int, selected: Boolean, icon: String, onSelect: () -> Unit, onDelete: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).border(if (selected) 1.dp else .5.dp, if (selected) Color(0xFF2D70DA) else Color(0xFFD3D3D3))) {
        Column(Modifier.width(34.dp).fillMaxHeight().background(if (selected) Color(0xFF2D70DA) else Color(0xFFEEEEEE)).clickable(onClick = onSelect)) {
            Text("$number", fontSize = 10.sp, color = if (selected) Color.White else Color.Gray, modifier = Modifier.padding(2.dp))
            Text(icon, fontSize = 24.sp, color = if (selected) Color.White else Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxWidth().padding(start = 13.dp, top = 8.dp, end = 35.dp, bottom = 12.dp), content = content)
            Text("×", color = Color.LightGray, fontSize = 25.sp, modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).clickable(onClick = onDelete))
        }
    }
}

@Composable
private fun NumberSetting(label: String, source: String, modifier: Modifier, onSelect: () -> Unit, onChange: (String) -> Unit) {
    Column(modifier.padding(top = 8.dp)) {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        BasicTextField(source, onChange, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), textStyle = TextStyle(fontSize = 15.sp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).onFocusChanged { if (it.isFocused) onSelect() })
        HorizontalDivider(color = Color(0xFFCCCCCC))
    }
}

private fun number(value: Double) = java.lang.String.format(java.util.Locale.US, "%.6g", value)
