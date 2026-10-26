package dev.demos.graphing.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun WorksheetItemIcon(kind: String, modifier: Modifier = Modifier) {
    val color = Color(0xFF808080)
    if (kind == "expression") {
        Text("ƒ(x)", color = color, fontSize = 18.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, modifier = modifier)
        return
    }
    if (kind == "note") {
        Text("❝❞", color = color, fontSize = 23.sp, fontFamily = FontFamily.Serif, modifier = modifier)
        return
    }
    Canvas(modifier) {
        val w = size.width; val h = size.height; val stroke = Stroke(1.4.dp.toPx())
        when (kind) {
            "table" -> {
                drawRect(color, Offset(w * .1f, h * .08f), Size(w * .8f, h * .84f), style = stroke)
                drawRect(color, Offset(w * .1f, h * .08f), Size(w * .8f, h * .2f))
                for (fraction in listOf(.34f, .63f)) drawLine(color, Offset(w * .1f, h * fraction), Offset(w * .9f, h * fraction), 1.dp.toPx())
                drawLine(color, Offset(w * .5f, h * .28f), Offset(w * .5f, h * .92f), 1.dp.toPx())
            }
            "inference" -> {
                val curve = Path().apply {
                    moveTo(w * .05f, h * .85f)
                    cubicTo(w * .38f, h * .85f, w * .29f, h * .12f, w * .5f, h * .12f)
                    cubicTo(w * .71f, h * .12f, w * .62f, h * .85f, w * .95f, h * .85f)
                }
                drawPath(curve, color, style = stroke)
                drawLine(color.copy(alpha = .6f), Offset(w * .05f, h * .87f), Offset(w * .95f, h * .87f), 1.dp.toPx())
            }
            "folder" -> {
                val folder = Path().apply {
                    moveTo(w * .08f, h * .24f); lineTo(w * .38f, h * .24f); lineTo(w * .48f, h * .36f)
                    lineTo(w * .92f, h * .36f); lineTo(w * .92f, h * .84f); lineTo(w * .08f, h * .84f); close()
                }
                drawPath(folder, color)
            }
            "image" -> {
                drawRect(color, Offset(w * .08f, h * .12f), Size(w * .84f, h * .76f), style = stroke)
                drawCircle(color, w * .09f, Offset(w * .7f, h * .34f))
                val mountains = Path().apply {
                    moveTo(w * .16f, h * .78f); lineTo(w * .37f, h * .4f); lineTo(w * .56f, h * .68f)
                    lineTo(w * .69f, h * .55f); lineTo(w * .84f, h * .78f); close()
                }
                drawPath(mountains, color)
            }
        }
    }
}
