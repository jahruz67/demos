package dev.demos.graphing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
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
            Text("≤ ${slider.name} ≤", fontSize = 14.sp, color = Color(0xFF777777))
            SliderNumberField(slider.name + "max", number(slider.max), "10", Modifier.weight(1f), onInputFocus) { it.toDoubleOrNull()?.let { n -> if (n > latest.min) configure(max = n) } }
            Text("Step:", fontSize = 11.sp, color = Color(0xFF555555))
            SliderNumberField(slider.name + "step", if (slider.step == 0.0) "" else number(slider.step), "", Modifier.weight(1f), onInputFocus) { input ->
                val value = if (input.isBlank()) 0.0 else input.toDoubleOrNull()
                value?.let { if (it >= 0) configure(step = it) }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(number(slider.min), color = Color.Gray, fontSize = 9.sp)
        Slider(value = slider.value.toFloat().coerceIn(slider.min.toFloat(), slider.max.toFloat()),
            onValueChange = { v ->
                val value = if (slider.step > 0) slider.min + round((v - slider.min) / slider.step) * slider.step else v.toDouble()
                onAction(CalculatorAction.SetSlider(slider.name, value.coerceIn(slider.min, slider.max)))
            }, valueRange = slider.min.toFloat()..slider.max.toFloat(),
            modifier = Modifier.weight(1f).height(30.dp), colors = SliderDefaults.colors(thumbColor = Color(0xFF2D70DA), activeTrackColor = Color(0xFFB6CFF2), inactiveTrackColor = Color(0xFFE2E2E2)))
        Text(number(slider.max), color = Color.Gray, fontSize = 9.sp)
    }
}

@Composable
private fun SliderNumberField(key: String, source: String, placeholder: String, modifier: Modifier, onFocus: (Boolean) -> Unit, onChange: (String) -> Unit) {
    var value by remember(key) { mutableStateOf(source) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(source) { if (!focused) value = source }
    BasicTextField(value, { value = it; onChange(it) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = TextStyle(fontSize = 12.sp, color = Color(0xFF555555)),
        modifier = modifier.height(24.dp).onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) },
        decorationBox = { inner -> Column { Box(Modifier.weight(1f)) { if (value.isBlank()) Text(placeholder, color = Color.LightGray, fontSize = 12.sp); inner() }; HorizontalDivider(color = Color(0xFFCCCCCC)) } })
}

@Composable
internal fun SliderAnimationMenu(slider: SliderUi, onAction: (CalculatorAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val configuration = SliderConfiguration(slider.min, slider.max, slider.step, slider.animation, slider.speed)
    Box {
        Text(when (slider.animation) { SliderAnimation.Oscillate -> "⇄"; SliderAnimation.Forward -> "→"; SliderAnimation.Reverse -> "←"; SliderAnimation.Once -> "⇥" },
            color = Color(0xFFBBBBBB), fontSize = 19.sp, modifier = Modifier.clickable { expanded = true }.padding(4.dp))
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.background(Color.White).width(210.dp)) {
            listOf(SliderAnimation.Oscillate to "Back and forth", SliderAnimation.Forward to "Repeat forward", SliderAnimation.Reverse to "Repeat backward", SliderAnimation.Once to "Play once").forEach { (mode, label) ->
                DropdownMenuItem(text = { Text((if (mode == slider.animation) "✓ " else "") + label, fontSize = 13.sp) }, onClick = {
                    onAction(CalculatorAction.ConfigureSlider(slider.name, configuration.copy(animation = mode))); expanded = false
                })
            }
            HorizontalDivider()
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Speed", fontSize = 12.sp, modifier = Modifier.weight(1f))
                listOf(.5, 1.0, 2.0).forEach { speed ->
                    Text("${number(speed)}×", color = if (slider.speed == speed) Color.White else Color.Gray, fontSize = 12.sp,
                        modifier = Modifier.padding(2.dp).background(if (slider.speed == speed) Color(0xFF2D70DA) else Color(0xFFF1F1F1), RoundedCornerShape(3.dp)).clickable {
                            onAction(CalculatorAction.ConfigureSlider(slider.name, configuration.copy(speed = speed)))
                        }.padding(6.dp))
                }
            }
        }
    }
}

private fun number(value: Double) = if (value == value.toLong().toDouble()) value.toLong().toString() else java.lang.String.format(java.util.Locale.US, "%.4g", value)
