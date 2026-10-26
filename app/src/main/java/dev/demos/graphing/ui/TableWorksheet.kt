@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.demos.graphing.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.demos.graphing.math.RegressionFit
import dev.demos.graphing.math.RegressionModel

internal data class TableCellSelection(val id: Long, val row: Int, val column: Int)

private val TableBlue = Color(0xFF2D70DA)
private val TableBorder = Color(0xFFD3D3D3)

@Composable
internal fun TableWorksheet(
    row: ExpressionRow, number: Int, tableNumber: Int, selected: Boolean, table: TableData,
    editing: TableCellSelection?, value: TextFieldValue?, fit: RegressionFit?,
    onCell: (Int, Int) -> Unit, onInput: (TextFieldValue) -> Unit, onFocus: (Boolean) -> Unit,
    onSelect: () -> Unit, onDelete: () -> Unit, onChange: (TableData) -> Unit,
    onVisibility: () -> Unit, onColor: () -> Unit, onExport: () -> Unit, onCopyEquation: (String) -> Unit,
) {
    var columnMenu by remember(row.id) { mutableStateOf(false) }
    var regressionMenu by remember(row.id) { mutableStateOf(false) }
    var colorColumn by remember(row.id) { mutableStateOf<Int?>(null) }
    val scroll = rememberLazyListState()
    LaunchedEffect(editing?.row) {
        if (editing?.id == row.id && editing.row >= 0) {
            val target = editing.row.coerceIn(0, table.size)
            if (scroll.layoutInfo.visibleItemsInfo.none { it.index == target }) scroll.animateScrollToItem(target)
        }
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).border(if (selected) 1.dp else .5.dp, if (selected) TableBlue else TableBorder)) {
        Column(Modifier.width(34.dp).fillMaxHeight().background(if (selected) TableBlue else Color(0xFFEEEEEE)), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$number", color = if (selected) Color.White else Color.Gray, fontSize = 10.sp, modifier = Modifier.align(Alignment.Start).padding(2.dp))
            if (table.size >= 2) Text("⁙", color = if (selected) Color.White else Color.Gray, fontSize = 25.sp,
                modifier = Modifier.clickable { onSelect(); onChange(table.copy(options = table.options.copy(regression = table.options.regression ?: RegressionModel.Linear))) }
                    .semantics { contentDescription = "Add regression to table $tableNumber" })
            Spacer(Modifier.weight(1f))
            Text("⊕", color = if (selected) Color.White else Color.Gray, fontSize = 24.sp, modifier = Modifier.padding(bottom = 7.dp).clickable {
                onCopyEquation("zoom-table:${row.id}")
            }.semantics { contentDescription = "Zoom to table data" })
        }
        Column(Modifier.weight(1f).padding(top = 8.dp, bottom = 8.dp)) {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 15.dp, end = 44.dp).horizontalScroll(rememberScrollState())) {
                    Row(Modifier.height(44.dp)) {
                        repeat(table.columnCount) { column ->
                            Row(Modifier.width(if (column == 0) 64.dp else 84.dp).fillMaxHeight().borderBottom(Color(0xFF555555)), verticalAlignment = Alignment.CenterVertically) {
                                if (column > 0) {
                                    Box {
                                        PointStyleIcon(if (column == 1) row.color else table.options.extraColumns[column - 2].color,
                                            if (column == 1) row.hidden else table.options.extraColumns[column - 2].hidden,
                                            Modifier.size(27.dp).combinedClickable(onClick = {
                                                if (column == 1) onVisibility() else onChange(table.copy(options = table.options.copy(extraColumns = table.options.extraColumns.mapIndexed { i, c -> if (i == column - 2) c.copy(hidden = !c.hidden) else c })))
                                            }, onLongClick = { if (column == 1) onColor() else colorColumn = column }))
                                    }
                                }
                                WorksheetCell(table.header(column, tableNumber), editing?.id == row.id && editing.row == -1 && editing.column == column,
                                    value, number, Modifier.weight(1f), { onCell(-1, column) }, onInput, onFocus)
                            }
                        }
                        Text("+", color = Color(0xFFAAAAAA), fontSize = 23.sp, modifier = Modifier.width(30.dp).clickable {
                            onChange(table.copy(options = table.options.copy(extraColumns = table.options.extraColumns + TableColumn("y_${tableNumber + table.options.extraColumns.size + 1}"))))
                        }.padding(5.dp))
                    }
                    LazyColumn(Modifier.width((64 + 84 * (table.columnCount - 1) + 30).dp).height(minOf(240, maxOf(1, table.size + 1) * 34).dp), state = scroll) {
                        items(maxOf(1, table.size + 1)) { index ->
                            Row(Modifier.height(34.dp)) {
                                repeat(table.columnCount) { column ->
                                    WorksheetCell(table.column(column).getOrElse(index) { "" }, editing?.id == row.id && editing.row == index && editing.column == column,
                                        value, number, Modifier.width(if (column == 0) 64.dp else 84.dp).fillMaxHeight().borderBottom(TableBorder)
                                            .borderLeft(if (column == 0) Color.Transparent else if (column == 1) Color(0xFF555555) else TableBorder),
                                        { onCell(index, column) }, onInput, onFocus)
                                }
                                if (selected && editing?.id == row.id && editing.row == index && index < table.size) {
                                    Text("×", color = Color.LightGray, fontSize = 19.sp, modifier = Modifier.width(30.dp).clickable { onChange(table.withoutRow(index)) }
                                        .semantics { contentDescription = "Delete table row ${index + 1}" }.padding(4.dp))
                                }
                            }
                        }
                    }
                }
                Text("×", color = Color(0xFFBBBBBB), fontSize = 28.sp, modifier = Modifier.align(Alignment.TopEnd).padding(end = 7.dp).clickable(onClick = onDelete)
                    .semantics { contentDescription = "Delete table $tableNumber" })
                Box(Modifier.align(Alignment.BottomEnd)) {
                    Text("⋮", color = Color.Gray, fontSize = 23.sp, modifier = Modifier.padding(end = 12.dp).clickable { columnMenu = true }.semantics { contentDescription = "Table options" })
                    DropdownMenu(columnMenu, { columnMenu = false }, modifier = Modifier.background(Color.White)) {
                        DropdownMenuItem(text = { Text(if (table.options.regression == null) "Add regression" else "Remove regression") }, onClick = {
                            onChange(table.copy(options = table.options.copy(regression = if (table.options.regression == null) RegressionModel.Linear else null, residuals = false))); columnMenu = false
                        })
                        DropdownMenuItem(text = { Text("Export table…") }, onClick = { onExport(); columnMenu = false })
                        DropdownMenuItem(text = { Text("Sort by ${table.header(0, tableNumber)}") }, onClick = {
                            val order = (0 until table.size).sortedWith(compareBy { table.x.getOrNull(it)?.toDoubleOrNull() ?: Double.POSITIVE_INFINITY })
                            onChange(table.copy(x = order.map { table.x.getOrElse(it) { "" } }, y = order.map { table.y.getOrElse(it) { "" } },
                                options = table.options.copy(extraColumns = table.options.extraColumns.map { c -> c.copy(values = order.map { c.values.getOrElse(it) { "" } }) }))); columnMenu = false
                        })
                        if (table.columnCount > 2) DropdownMenuItem(text = { Text("Remove last column") }, onClick = {
                            onChange(table.copy(options = table.options.copy(extraColumns = table.options.extraColumns.dropLast(1)))); columnMenu = false
                        })
                    }
                }
            }
            table.options.regression?.let { model ->
                Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 9.dp, top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RegressionStyleIcon(table.options.regressionColor, table.options.regressionHidden, Modifier.size(29.dp).combinedClickable(onClick = {
                            onChange(table.copy(options = table.options.copy(regressionHidden = !table.options.regressionHidden)))
                        }, onLongClick = { colorColumn = -1 }))
                        Box(Modifier.weight(1f).padding(start = 6.dp)) {
                            Row(Modifier.fillMaxWidth().height(36.dp).border(1.dp, TableBorder, RoundedCornerShape(4.dp)).clickable { regressionMenu = true }
                                .padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(model.label, fontSize = 14.sp, color = Color(0xFF444444), modifier = Modifier.weight(1f)); Text("▾", color = Color.Gray)
                            }
                            DropdownMenu(regressionMenu, { regressionMenu = false }, modifier = Modifier.width(225.dp).background(Color.White)) {
                                RegressionModel.entries.forEach { item ->
                                    DropdownMenuItem(text = { Text(item.label, fontSize = 13.sp) }, modifier = Modifier.height(35.dp).background(if (model == item) Color(0xFFE4ECF9) else Color.Transparent), onClick = {
                                        onChange(table.copy(options = table.options.copy(regression = item))); regressionMenu = false
                                    })
                                }
                                HorizontalDivider()
                                Text("Make a custom regression model in a new expression using ~.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(10.dp))
                            }
                        }
                        var options by remember { mutableStateOf(false) }
                        Box {
                            Text("⋮", color = Color.Gray, fontSize = 22.sp, modifier = Modifier.padding(start = 5.dp).clickable { options = true })
                            DropdownMenu(options, { options = false }, modifier = Modifier.background(Color.White)) {
                                DropdownMenuItem(text = { Text("Remove regression") }, onClick = { onChange(table.copy(options = table.options.copy(regression = null, residuals = false))); options = false })
                                DropdownMenuItem(text = { Text("Copy equation to expression") }, enabled = fit != null, onClick = { fit?.let { onCopyEquation("y=${it.expression}") }; options = false })
                                if (model == RegressionModel.Exponential || model == RegressionModel.Power) DropdownMenuItem(text = { Text(if (table.options.logMode) "✓ Log Mode" else "Log Mode") }, onClick = {
                                    onChange(table.copy(options = table.options.copy(logMode = !table.options.logMode))); options = false
                                })
                            }
                        }
                    }
                    Row(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("EQUATION", fontSize = 10.sp, color = Color.Gray)
                        Text("  ▣", color = Color.Gray, fontSize = 16.sp, modifier = Modifier.clickable { fit?.let { onCopyEquation("y=${it.expression}") } }
                            .semantics { contentDescription = "Copy fitted equation to a new expression" })
                    }
                    if (fit != null) Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { MathExpression("y=${displayEquation(fit.expression)}", 19) }
                    else Text("Add enough valid, distinct points for this model.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 5.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("STATISTICS", color = Color.Gray, fontSize = 10.sp)
                            fit?.let {
                                MathExpression("R^2=${statNumber(it.rSquared)}", 19)
                                it.correlation?.let { correlation -> MathExpression("r=${statNumber(correlation)}", 18) }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("RESIDUALS", color = Color.Gray, fontSize = 10.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MathExpression("e_$tableNumber", 18)
                                Text(if (table.options.residuals) " hide " else " plot ", fontSize = 13.sp,
                                    modifier = Modifier.padding(start = 6.dp).background(Color(0xFFF4F4F4), RoundedCornerShape(3.dp)).border(.7.dp, TableBorder, RoundedCornerShape(3.dp))
                                        .clickable { onChange(table.copy(options = table.options.copy(residuals = !table.options.residuals))) }.padding(3.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    colorColumn?.let { column ->
        val color = if (column == -1) table.options.regressionColor else table.options.extraColumns.getOrNull(column - 2)?.color ?: row.color
        ColorPaletteDialog(color, { colorColumn = null }) { next ->
            if (column == -1) onChange(table.copy(options = table.options.copy(regressionColor = next)))
            else onChange(table.copy(options = table.options.copy(extraColumns = table.options.extraColumns.mapIndexed { i, item -> if (i == column - 2) item.copy(color = next) else item })))
            colorColumn = null
        }
    }
}

@Composable
private fun WorksheetCell(source: String, selected: Boolean, value: TextFieldValue?, number: Int, modifier: Modifier,
    onSelect: () -> Unit, onInput: (TextFieldValue) -> Unit, onFocus: (Boolean) -> Unit) {
    Box(modifier.then(if (selected) Modifier.border(1.dp, TableBlue) else Modifier).clickable(onClick = onSelect).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        if (selected) MathEditor(value ?: TextFieldValue(source, TextRange(source.length)), true, number, Modifier.fillMaxWidth(), onInput, onFocus, onSelect,
            fontSize = 18, minimumHeight = 28.dp, placeholder = "")
        else if (source.isNotBlank()) MathExpression(source, 18)
    }
}

@Composable
internal fun PointStyleIcon(color: Int, hidden: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = if (hidden) "Show table points; hold for color" else "Hide table points; hold for color" }) {
        drawCircle(if (hidden) Color.White else Color(color))
        if (hidden) drawCircle(Color(color), style = Stroke(1.5.dp.toPx()))
        listOf(.3f to .35f, .67f to .27f, .6f to .68f, .27f to .7f).forEach { (x, y) -> drawCircle(if (hidden) Color(color) else Color.White, size.width * .085f, Offset(size.width * x, size.height * y)) }
    }
}

@Composable
internal fun RegressionStyleIcon(color: Int, hidden: Boolean = false, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawCircle(if (hidden) Color.White else Color(color))
        if (hidden) drawCircle(Color(color), style = Stroke(1.dp.toPx()))
        val wave = Path().apply { moveTo(0f, size.height * .7f); cubicTo(size.width * .35f, -size.height * .45f, size.width * .4f, size.height * 1.5f, size.width, size.height * .2f) }
        drawPath(wave, if (hidden) Color(color) else Color.White, style = Stroke(2.5.dp.toPx()))
    }
}

private fun statNumber(value: Double) = java.lang.String.format(java.util.Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
private fun displayEquation(source: String) = source.replace(Regex("\\((-?\\d+(?:\\.\\d+)?(?:e[+-]?\\d+)?)\\)")) { "(${it.groupValues[1].toDoubleOrNull()?.let { n -> java.lang.String.format(java.util.Locale.US, "%.6g", n) } ?: it.groupValues[1]})" }

private fun Modifier.borderBottom(color: Color): Modifier = this.drawWithContent {
    drawContent()
    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), .7.dp.toPx())
}
private fun Modifier.borderLeft(color: Color): Modifier = this.drawWithContent {
    drawContent()
    drawLine(color, Offset.Zero, Offset(0f, size.height), .7.dp.toPx())
}
