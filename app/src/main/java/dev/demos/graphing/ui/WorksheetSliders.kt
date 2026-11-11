package dev.demos.graphing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.demos.graphing.math.MathSyntax
import dev.demos.graphing.math.NumericCompiler
import kotlin.math.round

internal fun missingSliderVariables(source: String, state: CalculatorUiState): List<String> = runCatching {
    val definition = MathSyntax.definition(source)
    if (definition != null && definition.name !in setOf("x", "y", "z", "r")) return emptyList()
    val declared = MathSyntax.symbols(state.rows.filter { parseTableData(it.text) == null && parseNoteText(it.text) == null && parseFolderData(it.text) == null && parseImageData(it.text) == null && parseInferenceData(it.text) == null }.map { it.text })
    val tableNames = state.rows.mapNotNull { parseTableData(it.text) }.flatMapIndexed { index, table -> (0 until table.columnCount).map { table.header(it, index + 1).substringBefore('=').trim() } }.toSet()
    val compiled = NumericCompiler.compile(definition?.body ?: source, !state.degreeMode, declared)
    (compiled.variables - declared.variables - tableNames - MathSyntax.functions - setOf("x", "y", "z", "r", "theta", "pi", "e", "i", "C"))
        .filter { it.matches(Regex("[A-Za-z][A-Za-z0-9_]*")) }.sorted().take(6)
}.getOrDefault(emptyList())

@Composable
internal fun InlineSlider(slider: SliderUi, selected: Boolean, onAction: (CalculatorAction) -> Unit, onInputFocus: (Boolean) -> Unit = {}) {
    val latest by rememberUpdatedState(slider)
    fun configure(min: Double = latest.min, max: Double = latest.max, step: Double = latest.step) {
        onAction(CalculatorAction.ConfigureSlider(slider.name, SliderConfiguration(min, max, step, latest.animation, latest.speed)))
    }
    if (selected) {
        Row(Modifier.fillMaxWidth().padding(end = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            SliderNumberField(slider.name + "min", number(slider.min), "−10", Modifier.weight(1f), onInputFocus) { it.toDoubleOrNull()?.let { n -> if (n < latest.max) configure(min = n) } }
            Text("≤ ${slider.name} ≤", fontSize = 14.sp, color = DemosColors.Muted)
            SliderNumberField(slider.name + "max", number(slider.max), "10", Modifier.weight(1f), onInputFocus) { it.toDoubleOrNull()?.let { n -> if (n > latest.min) configure(max = n) } }
            Text("Step:", fontSize = 11.sp, color = DemosColors.Muted)
            SliderNumberField(slider.name + "step", if (slider.step == 0.0) "" else number(slider.step), "", Modifier.weight(1f), onInputFocus) { input ->
                val value = if (input.isBlank()) 0.0 else input.toDoubleOrNull()
                value?.let { if (it >= 0) configure(step = it) }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(number(slider.min), color = DemosColors.Muted, style = MaterialTheme.typography.labelSmall)
        Slider(value = slider.value.toFloat().coerceIn(slider.min.toFloat(), slider.max.toFloat()),
            onValueChange = { v ->
                val value = if (slider.step > 0) slider.min + round((v - slider.min) / slider.step) * slider.step else v.toDouble()
                onAction(CalculatorAction.SetSlider(slider.name, value.coerceIn(slider.min, slider.max)))
            }, valueRange = slider.min.toFloat()..slider.max.toFloat(),
            modifier = Modifier.weight(1f).height(30.dp), colors = SliderDefaults.colors(thumbColor = DemosColors.Blue, activeTrackColor = DemosColors.Blue, inactiveTrackColor = DemosColors.KeySecondary))
        Text(number(slider.max), color = DemosColors.Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SliderNumberField(key: String, source: String, placeholder: String, modifier: Modifier, onFocus: (Boolean) -> Unit, onChange: (String) -> Unit) {
    var value by remember(key) { mutableStateOf(source) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(source) { if (!focused) value = source }
    CompactNumberField(value, { value = it; onChange(it) }, modifier, placeholder = placeholder,
        onFocus = { focused = it; onFocus(it) })
}

@Composable
internal fun SliderAnimationMenu(slider: SliderUi, onAction: (CalculatorAction) -> Unit, selected: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    val configuration = SliderConfiguration(slider.min, slider.max, slider.step, slider.animation, slider.speed)
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp).semantics { contentDescription = "Slider animation options" }) {
            AnimationDirectionIcon(slider.animation, if (selected) Color.White else DemosColors.Muted)
        }
        DemosDropdownMenu(expanded, { expanded = false }, modifier = Modifier.width(230.dp)) {
            listOf(SliderAnimation.Oscillate to "Back and forth", SliderAnimation.Forward to "Repeat forward", SliderAnimation.Reverse to "Repeat backward", SliderAnimation.Once to "Play once").forEach { (mode, label) ->
                DropdownMenuItem(text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                    trailingIcon = { if (mode == slider.animation) CheckmarkIcon() }, onClick = {
                    onAction(CalculatorAction.ConfigureSlider(slider.name, configuration.copy(animation = mode))); expanded = false
                })
            }
            HorizontalDivider()
            Row(Modifier.padding(10.dp).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                Text("Speed", fontSize = 12.sp, modifier = Modifier.weight(1f))
                listOf(.5, 1.0, 2.0).forEach { speed ->
                    Text("${number(speed)}×", color = if (slider.speed == speed) DemosColors.Blue else DemosColors.Muted, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(2.dp).clip(MaterialTheme.shapes.extraSmall)
                            .background(if (slider.speed == speed) DemosColors.BlueSoft else DemosColors.Surface)
                            .selectable(selected = slider.speed == speed, role = Role.RadioButton, onClick = {
                                onAction(CalculatorAction.ConfigureSlider(slider.name, configuration.copy(speed = speed)))
                            }).padding(horizontal = 8.dp, vertical = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun AnimationDirectionIcon(mode: SliderAnimation, color: Color) {
    Canvas(Modifier.size(18.dp)) {
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color,
            Offset(size.width * x1, size.height * y1), Offset(size.width * x2, size.height * y2), 1.6.dp.toPx(), StrokeCap.Round)
        fun arrow(y: Float, forward: Boolean) {
            val tip = if (forward) .8f else .2f
            val head = if (forward) .6f else .4f
            line(.2f, y, .8f, y)
            line(head, y - .18f, tip, y)
            line(head, y + .18f, tip, y)
        }
        if (mode == SliderAnimation.Oscillate) { arrow(.28f, true); arrow(.72f, false) }
        else arrow(.5f, mode != SliderAnimation.Reverse)
        if (mode == SliderAnimation.Once) line(.95f, .25f, .95f, .75f)
    }
}

private fun number(value: Double) = if (value == value.toLong().toDouble()) value.toLong().toString() else java.lang.String.format(java.util.Locale.US, "%.4g", value)
