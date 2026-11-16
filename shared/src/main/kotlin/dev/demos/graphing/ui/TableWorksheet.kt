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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.demos.graphing.math.RegressionFit
import dev.demos.graphing.math.RegressionModel
import dev.demos.graphing.math.TableEvaluation

internal data class TableCellSelection(val id: Long, val row: Int, val column: Int)

private val TableBlue = DemosColors.Blue
private val TableBorder = DemosColors.Border

@Composable
internal fun TableWorksheet(
    row: ExpressionRow, number: Int, tableNumber: Int, selected: Boolean, table: TableData,
    editing: TableCellSelection?, value: TextFieldValue?, fit: RegressionFit?, fitting: Boolean,
    parameters: Map<String, Double>, radians: Boolean,
    onCell: (Int, Int) -> Unit, onInput: (TableCellSelection, TextFieldValue) -> Unit, onFocus: (TableCellSelection, Boolean) -> Unit,
    onDeleteRow: (Int) -> Unit, onSort: () -> Unit,
    onSelect: () -> Unit, onDelete: () -> Unit, onChange: (TableOptions) -> Unit,
    onVisibility: () -> Unit, onColor: () -> Unit, onExport: () -> Unit, onCopyEquation: (String) -> Unit,
) {
    val pairs = remember(table, tableNumber, parameters, radians) { TableEvaluation.pairs(table, tableNumber, parameters, radians) }
    val validFit = fit?.takeIf { it.x == pairs.map { point -> point.first } && it.y == pairs.map { point -> point.second } }
    var columnMenu by remember(row.id) { mutableStateOf(false) }
    var regressionMenu by remember(row.id) { mutableStateOf(false) }
    var colorColumn by remember(row.id) { mutableStateOf<Int?>(null) }
    val scroll = rememberLazyListState()
    LaunchedEffect(selected, editing, table.size) {
        if (selected && editing?.id == row.id && editing.row >= 0) {
            val target = editing.row.coerceIn(0, table.size)
            if (scroll.layoutInfo.visibleItemsInfo.none { it.index == target }) scroll.animateScrollToItem(target)
        }
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).border(if (selected) 1.dp else .5.dp, if (selected) TableBlue else TableBorder)) {
        Column(Modifier.width(34.dp).fillMaxHeight().background(if (selected) TableBlue else DemosColors.Surface), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$number", color = if (selected) Color.White else DemosColors.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.Start).padding(start = 6.dp, top = 4.dp))
            if (pairs.size >= 2 && table.options.regression == null) Canvas(Modifier.size(30.dp).clickable {
                onSelect(); onChange(table.options.copy(regression = RegressionModel.Linear))
            }.semantics { contentDescription = "Add regression to table $tableNumber" }) {
                val color = if (selected) Color.White else DemosColors.Muted
                drawLine(color, Offset(size.width * .16f, size.height * .76f), Offset(size.width * .84f, size.height * .24f), 1.5.dp.toPx())
                listOf(.24f to .62f, .5f to .54f, .76f to .28f).forEach { (x, y) -> drawCircle(color, 2.dp.toPx(), Offset(size.width * x, size.height * y)) }
            }
            Spacer(Modifier.weight(1f))
            Canvas(Modifier.padding(bottom = 4.dp).size(30.dp).clickable(enabled = pairs.isNotEmpty()) {
                onCopyEquation("zoom-table:${row.id}")
            }.semantics { contentDescription = "Zoom to table data" }) {
                val color = (if (selected) Color.White else DemosColors.Muted).copy(alpha = if (pairs.isEmpty()) .35f else 1f)
                val center = Offset(size.width * .4f, size.height * .4f)
                drawCircle(color, size.width * .24f, center, style = Stroke(1.5.dp.toPx()))
                drawLine(color, Offset(size.width * .58f, size.height * .58f), Offset(size.width * .82f, size.height * .82f), 2.dp.toPx())
                drawLine(color, center - Offset(size.width * .12f, 0f), center + Offset(size.width * .12f, 0f), 1.dp.toPx())
                drawLine(color, center - Offset(0f, size.height * .12f), center + Offset(0f, size.height * .12f), 1.dp.toPx())
            }
        }
        Column(Modifier.weight(1f).padding(top = 8.dp, bottom = 8.dp)) {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 15.dp, end = 44.dp).horizontalScroll(rememberScrollState())) {
                    Row(Modifier.heightIn(min = 44.dp).height(IntrinsicSize.Min)) {
                        repeat(table.columnCount) { column ->
                            Row(Modifier.width(if (column == 0) 80.dp else 96.dp).fillMaxHeight().background(DemosColors.Surface).borderBottom(DemosColors.Subtle), verticalAlignment = Alignment.CenterVertically) {
                                if (column > 0) {
                                    Box {
                                        PointStyleIcon(if (column == 1) row.color else table.options.extraColumns[column - 2].color,
                                            if (column == 1) row.hidden else table.options.extraColumns[column - 2].hidden,
                                            Modifier.size(27.dp).combinedClickable(onClick = {
                                                if (column == 1) onVisibility() else onChange(table.options.copy(extraColumns = table.options.extraColumns.mapIndexed { i, c -> if (i == column - 2) c.copy(hidden = !c.hidden) else c }))
                                            }, onLongClick = { if (column == 1) onColor() else colorColumn = column }))
                                    }
                                }
                                WorksheetCell(table.header(column, tableNumber), selected && editing?.id == row.id && editing.row == -1 && editing.column == column,
                                    value, number, Modifier.weight(1f), { onCell(-1, column) },
                                    { onInput(TableCellSelection(row.id, -1, column), it) }, { onFocus(TableCellSelection(row.id, -1, column), it) })
                            }
                        }
                        IconButton(enabled = table.columnCount < 12, modifier = Modifier.width(30.dp).fillMaxHeight()
                            .semantics { contentDescription = "Add table column" }, onClick = {
                            onChange(table.options.copy(extraColumns = table.options.extraColumns + TableColumn("y_${tableNumber + table.options.extraColumns.size + 1}")))
                        }) {
                            Canvas(Modifier.size(16.dp)) {
                                val color = if (table.columnCount < 12) DemosColors.Muted else DemosColors.Disabled
                                drawLine(color, Offset(size.width * .15f, size.height * .5f), Offset(size.width * .85f, size.height * .5f), 1.8.dp.toPx(), StrokeCap.Round)
                                drawLine(color, Offset(size.width * .5f, size.height * .15f), Offset(size.width * .5f, size.height * .85f), 1.8.dp.toPx(), StrokeCap.Round)
                            }
                        }
                    }
                    LazyColumn(Modifier.width((80 + 96 * (table.columnCount - 1) + 30).dp).height(minOf(252, maxOf(1, table.size + 1) * 40).dp), state = scroll) {
                        items(maxOf(1, table.size + 1)) { index ->
                            Row(Modifier.heightIn(min = 40.dp).height(IntrinsicSize.Min)) {
                                repeat(table.columnCount) { column ->
                                    key(row.id, index, column) {
                                        WorksheetCell(table.column(column).getOrElse(index) { "" }, selected && editing?.id == row.id && editing.row == index && editing.column == column,
                                            value, number, Modifier.width(if (column == 0) 80.dp else 96.dp).fillMaxHeight().borderBottom(TableBorder)
                                                .borderLeft(if (column == 0) Color.Transparent else if (column == 1) DemosColors.Subtle else TableBorder),
                                            { onCell(index, column) },
                                            { onInput(TableCellSelection(row.id, index, column), it) }, { onFocus(TableCellSelection(row.id, index, column), it) })
                                    }
                                }
                                Spacer(Modifier.width(30.dp))
                            }
                        }
                    }
                }
                DismissControl("Delete table $tableNumber", Modifier.align(Alignment.TopEnd), onDelete)
                Box(Modifier.align(Alignment.BottomEnd)) {
                    OverflowControl("Table options", onClick = { columnMenu = true })
                    DemosDropdownMenu(columnMenu, { columnMenu = false }) {
                        DropdownMenuItem(text = { Text(if (table.options.regression == null) "Add regression" else "Remove regression") }, onClick = {
                            onChange(table.options.copy(regression = if (table.options.regression == null) RegressionModel.Linear else null, residuals = false)); columnMenu = false
                        })
                        DropdownMenuItem(text = { Text("Export table…") }, onClick = { onExport(); columnMenu = false })
                        DropdownMenuItem(text = { Text("Sort by ${table.header(0, tableNumber)}") }, enabled = pairs.isNotEmpty(), onClick = { onSort(); columnMenu = false })
                        DropdownMenuItem(text = { Text("Delete selected row") }, enabled = selected && editing?.id == row.id && editing.row in 0 until table.size, onClick = {
                            editing?.let { onDeleteRow(it.row) }; columnMenu = false
                        })
                        if (table.columnCount > 2) DropdownMenuItem(text = { Text("Remove last column") }, onClick = {
                            onChange(table.options.copy(extraColumns = table.options.extraColumns.dropLast(1))); columnMenu = false
                        })
                    }
                }
            }
            table.options.regression?.let { model ->
                Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 9.dp, top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RegressionStyleIcon(table.options.regressionColor, table.options.regressionHidden, Modifier.size(29.dp).combinedClickable(onClick = {
                            onChange(table.options.copy(regressionHidden = !table.options.regressionHidden))
                        }, onLongClick = { colorColumn = -1 }))
                        Box(Modifier.weight(1f).padding(start = 6.dp)) {
                            MenuControl(Modifier.fillMaxWidth(), onClick = { regressionMenu = true }) {
                                Text(model.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            DemosDropdownMenu(regressionMenu, { regressionMenu = false }, modifier = Modifier.width(225.dp)) {
                                RegressionModel.entries.forEach { item ->
                                    DropdownMenuItem(text = { Text(item.label, style = MaterialTheme.typography.bodyMedium) }, modifier = Modifier.background(if (model == item) DemosColors.BlueSoft else Color.Transparent), onClick = {
                                        onChange(table.options.copy(regression = item)); regressionMenu = false
                                    })
                                }
                                HorizontalDivider()
                                Text("Make a custom regression model in a new expression using ~.", color = DemosColors.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                            }
                        }
                        var options by remember { mutableStateOf(false) }
                        Box {
                            OverflowControl("Regression options", onClick = { options = true })
                            DemosDropdownMenu(options, { options = false }) {
                                DropdownMenuItem(text = { Text("Remove regression") }, onClick = { onChange(table.options.copy(regression = null, residuals = false)); options = false })
                                DropdownMenuItem(text = { Text("Copy equation to expression") }, enabled = validFit != null, onClick = { validFit?.let { onCopyEquation("y=${it.expression}") }; options = false })
                                if (model == RegressionModel.Exponential || model == RegressionModel.Power) DropdownMenuItem(text = { Text("Log Mode") }, trailingIcon = { if (table.options.logMode) CheckmarkIcon() }, onClick = {
                                    onChange(table.options.copy(logMode = !table.options.logMode)); options = false
                                })
                            }
                        }
                    }
                    Row(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("EQUATION", style = MaterialTheme.typography.labelSmall, color = DemosColors.Muted)
                        IconButton(onClick = { validFit?.let { onCopyEquation("y=${it.expression}") } }, enabled = validFit != null,
                            modifier = Modifier.size(40.dp).semantics { contentDescription = "Copy fitted equation to a new expression" }) {
                            Canvas(Modifier.size(18.dp)) {
                                val color = if (validFit == null) DemosColors.Disabled else DemosColors.Muted
                                drawRect(color, Offset(size.width * .3f, size.height * .1f), androidx.compose.ui.geometry.Size(size.width * .6f, size.height * .6f), style = Stroke(1.5.dp.toPx()))
                                val back = Path().apply { moveTo(size.width * .1f, size.height * .3f); lineTo(size.width * .1f, size.height * .9f); lineTo(size.width * .7f, size.height * .9f) }
                                drawPath(back, color, style = Stroke(1.5.dp.toPx()))
                            }
                        }
                    }
                    if (fitting) Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        Text("  Fitting curve…", color = DemosColors.Muted, fontSize = 12.sp)
                    }
                    else if (validFit != null) Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { MathExpression("y=${displayEquation(validFit.expression)}", 19) }
                    else Text(regressionMessage(model, pairs), color = DemosColors.Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(vertical = 6.dp))
                    if (validFit != null) Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 5.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("STATISTICS", color = DemosColors.Muted, style = MaterialTheme.typography.labelSmall)
                            validFit?.let {
                                MathExpression("R^2=${statNumber(it.rSquared)}", 19)
                                it.correlation?.let { correlation -> MathExpression("r=${statNumber(correlation)}", 18) }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("RESIDUALS", color = DemosColors.Muted, style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MathExpression("e_$tableNumber", 18)
                                TextButton(onClick = { onChange(table.options.copy(residuals = !table.options.residuals)) },
                                    shape = MaterialTheme.shapes.small, modifier = Modifier.padding(start = 6.dp),
                                    colors = ButtonDefaults.textButtonColors(containerColor = if (table.options.residuals) DemosColors.BlueSoft else DemosColors.Surface),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                    Text(if (table.options.residuals) "Hide" else "Plot", style = MaterialTheme.typography.labelMedium)
                                }
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
            if (column == -1) onChange(table.options.copy(regressionColor = next))
            else onChange(table.options.copy(extraColumns = table.options.extraColumns.mapIndexed { i, item -> if (i == column - 2) item.copy(color = next) else item }))
            colorColumn = null
        }
    }
}

@Composable
private fun WorksheetCell(source: String, selected: Boolean, value: TextFieldValue?, number: Int, modifier: Modifier,
    onSelect: () -> Unit, onInput: (TextFieldValue) -> Unit, onFocus: (Boolean) -> Unit) {
    Box(modifier.then(if (selected) Modifier.border(1.dp, TableBlue) else Modifier).clipToBounds().clickable(onClick = onSelect).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        if (selected) MathEditor(value ?: TextFieldValue(source, TextRange(source.length)), true, number, Modifier.fillMaxWidth(), onInput, onFocus, onSelect,
            fontSize = 18, minimumHeight = 28.dp, placeholder = "")
        else if (source.isNotBlank()) Box(Modifier.padding(vertical = 6.dp)) { MathExpression(source, 18) }
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

private fun regressionMessage(model: RegressionModel, pairs: List<Pair<Double, Double>>): String {
    val needed = when (model) {
        RegressionModel.Quadratic, RegressionModel.Logistic -> 3
        RegressionModel.Cubic, RegressionModel.Sinusoidal -> 4
        RegressionModel.Quartic -> 5
        else -> 2
    }
    if (pairs.size < needed) return "Enter at least $needed complete x, y pairs for this model (${pairs.size} entered)."
    if (model in setOf(RegressionModel.Logarithmic, RegressionModel.Power) && pairs.any { it.first <= 0 }) return "This model requires positive x values."
    if (model in setOf(RegressionModel.Exponential, RegressionModel.Power) && pairs.any { it.second <= 0 }) return "This model requires positive y values."
    val distinct = if (model in setOf(RegressionModel.Linear, RegressionModel.Quadratic, RegressionModel.Cubic, RegressionModel.Quartic)) needed else 2
    if (pairs.map { it.first }.distinct().size < distinct) return "Use at least $distinct distinct x values for this model."
    return "This model could not be fitted to the data. Try another model."
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
