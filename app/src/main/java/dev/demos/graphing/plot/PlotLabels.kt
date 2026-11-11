package dev.demos.graphing.plot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp

internal class PlotLabels(private val measurer: TextMeasurer, private val density: Float) {
    var textSize = 13f * density
    fun layout(text: String) = measurer.measure(text, TextStyle(fontSize = (textSize / density).sp))
    fun measureText(text: String) = layout(text).size.width.toFloat()
}

internal fun DrawScope.drawTextWithHalo(text: String, x: Float, y: Float, labels: PlotLabels, density: Float, color: Color = Color.Black) {
    val layout = labels.layout(text)
    val origin = Offset(x, y - layout.firstBaseline)
    drawText(layout, Color.White, origin, drawStyle = Stroke(3 * density))
    drawText(layout, color, origin)
}
