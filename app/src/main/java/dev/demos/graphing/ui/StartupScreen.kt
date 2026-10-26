package dev.demos.graphing.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.demos.graphing.R

/** Lightweight first content while the saved workspace is read in the background. */
@Composable
fun StartupScreen() {
    Box(
        Modifier.fillMaxSize().background(Color(0xFF101B30)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(
                colors = listOf(Color(0xFF1E3657), Color(0xFF101B30)),
                center = center,
                radius = size.maxDimension * 0.65f,
            ))
            val spacing = 40.dp.toPx()
            val gridColor = Color(0xFF96B5E4).copy(alpha = 0.045f)
            var x = center.x % spacing
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height))
                x += spacing
            }
            var y = center.y % spacing
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y))
                y += spacing
            }
        }
        Column(
            Modifier.safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(painterResource(R.drawable.ic_startup), contentDescription = null, modifier = Modifier.size(120.dp))
            Spacer(Modifier.height(12.dp))
            Text("Demos", color = Color(0xFFF3F7FF), fontSize = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
            Spacer(Modifier.height(8.dp))
            Text("A space for your next idea", color = Color(0xFFADC0DC), fontSize = 15.sp)
            Spacer(Modifier.height(32.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(120.dp).height(3.dp),
                color = Color(0xFF80CBC4),
                trackColor = Color(0xFF2B3D57),
            )
            Spacer(Modifier.height(12.dp))
            Text("Opening your workspace", color = Color(0xFF91A7C6), fontSize = 12.sp)
        }
    }
}
