package dev.demos.graphing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

private data class FunctionKey(val title: String, val source: String, val retreat: Int = 1)

private val functionSections = listOf(
    "TRIG FUNCTIONS" to listOf("sin", "cos", "tan", "csc", "sec", "cot").map { FunctionKey(it, "$it()") },
    "INVERSE TRIG FUNCTIONS" to listOf(
        FunctionKey("sin⁻¹", "asin()"), FunctionKey("cos⁻¹", "acos()"), FunctionKey("tan⁻¹", "atan()")),
    "STATISTICS" to listOf(
        FunctionKey("mean", "mean()"), FunctionKey("median", "median()"), FunctionKey("stddev", "stddev()")),
    "REGRESSION" to listOf(
        FunctionKey("linear fit", "linear_regression(x_1,y_1)", 0),
        FunctionKey("poly fit", "polynomial_regression(x_1,y_1,2)", 0),
        FunctionKey("exp fit", "exponential_regression(x_1,y_1)", 0),
        FunctionKey("log fit", "logarithmic_regression(x_1,y_1)", 0),
        FunctionKey("power fit", "power_regression(x_1,y_1)", 0)),
    "CALCULUS" to listOf(
        FunctionKey("exp", "exp()"), FunctionKey("ln", "ln()"), FunctionKey("log", "log10()"),
        FunctionKey("d/dx", "diff(,x)", 3), FunctionKey("∂²/∂x²", "diff(,x,2)", 5), FunctionKey("lim", "limit(,x,0)", 5),
        FunctionKey("∫", "integrate(,x)", 3), FunctionKey("Σ", "sum(,n,1,10)", 8), FunctionKey("Π", "product(,n,1,10)", 8),
        FunctionKey("∫ₐᵇ", "integrate(,x,0,1)", 7), FunctionKey("series", "series(,x,0,6)", 7)),
    "HYPERBOLIC TRIG FUNCTIONS" to listOf("sinh", "cosh", "tanh", "asinh", "acosh", "atanh").map { FunctionKey(it, "$it()") },
    "MATRICES" to listOf(
        FunctionKey("det", "det()"), FunctionKey("inverse", "inv()"), FunctionKey("rref", "rref()"),
        FunctionKey("transpose", "tran()"), FunctionKey("eigenvals", "eigenvals()"), FunctionKey("dot", "dot(,)", 2),
        FunctionKey("[ ]", "[]"), FunctionKey("matrix", "[[,],[,]]", 7)),
    "CAS" to listOf(
        FunctionKey("solve", "solve(,x)", 3), FunctionKey("factor", "factor()"), FunctionKey("expand", "expand()"),
        FunctionKey("simplify", "simplify()"), FunctionKey("fsolve", "fsolve(,x)", 3), FunctionKey("desolve", "desolve(,y)", 3),
        FunctionKey("apart", "partfrac()"), FunctionKey("assume", "assume()")),
    "MATH" to listOf(
        FunctionKey("a²", "^2", 0), FunctionKey("aᵇ", "^{}"), FunctionKey("√", "sqrt()"),
        FunctionKey("π", "pi", 0), FunctionKey("|x|", "abs()"), FunctionKey("eˣ", "exp()"),
        FunctionKey("n!", "!", 0), FunctionKey("∞", "infinity", 0), FunctionKey("i", "i", 0), FunctionKey(",", ",", 0)),
    "LATEX" to listOf(
        FunctionKey("a/b", "\\frac{}{}", 3), FunctionKey("√", "\\sqrt{}"), FunctionKey("ⁿ√", "\\sqrt[3]{}"),
        FunctionKey("aᵇ", "^{}"), FunctionKey("aₙ", "_{}"), FunctionKey("sin", "\\sin()"),
        FunctionKey("θ", "\\theta", 0), FunctionKey("\\", "\\", 0)),
)

/** Keep the ordinary keypad in place while functions float above its button. */
@Composable
internal fun FunctionMenuKey(modifier: Modifier = Modifier, onTemplate: (String, Int) -> Unit, keypadTop: Int) {
    var expanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val margin = with(density) { 8.dp.roundToPx() }
    val position = remember(margin, keypadTop) { FunctionMenuPosition(margin, keypadTop) }
    val menuWidth = minOf(320.dp, (configuration.screenWidthDp.dp - 16.dp).coerceAtLeast(0.dp))
    val spaceAboveKeypad = with(density) { (keypadTop - margin * 2).coerceAtLeast(0).toDp() }
    val menuHeight = minOf(360.dp, configuration.screenHeightDp.dp * .45f,
        (spaceAboveKeypad - 7.dp).coerceAtLeast(0.dp))
    val border = Color(0xFFD3D3D3)

    Box(modifier.fillMaxHeight()) {
        KeyTile("funcs", Modifier.fillMaxSize(), gray = true) { expanded = !expanded }
        if (expanded) Popup(
            popupPositionProvider = position,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true),
        ) {
            Box(Modifier.width(menuWidth)) {
                Surface(
                    modifier = Modifier.padding(bottom = 7.dp),
                    shape = RoundedCornerShape(4.dp), color = Color.White,
                    border = BorderStroke(1.dp, border), shadowElevation = 5.dp,
                ) {
                    Column(Modifier.heightIn(max = menuHeight).verticalScroll(scrollState).padding(10.dp)) {
                        functionSections.forEachIndexed { index, (title, functions) ->
                            if (index > 0) Spacer(Modifier.height(18.dp))
                            Text(title, color = Color(0xFF777777), fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 7.dp))
                            functions.chunked(3).forEach { row ->
                                Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    row.forEach { item ->
                                        KeyTile(item.title, Modifier.weight(1f)) {
                                            expanded = false
                                            onTemplate(item.source, item.retreat)
                                        }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
                Canvas(Modifier.align(Alignment.BottomStart)
                    .offset { IntOffset(position.arrowCenter - margin, 0) }.size(16.dp, 8.dp)) {
                    val tip = Offset(size.width / 2, size.height)
                    drawPath(Path().apply { moveTo(0f, 0f); lineTo(tip.x, tip.y); lineTo(size.width, 0f); close() }, Color.White)
                    drawLine(border, Offset.Zero, tip, 1.dp.toPx())
                    drawLine(border, tip, Offset(size.width, 0f), 1.dp.toPx())
                }
            }
        }
    }
}

private class FunctionMenuPosition(private val margin: Int, private val keypadTop: Int) : PopupPositionProvider {
    var arrowCenter by mutableIntStateOf(0)
        private set

    override fun calculatePosition(
        anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(margin, maxX)
        arrowCenter = (anchorBounds.center.x - x)
            .coerceIn(margin * 2, (popupContentSize.width - margin * 2).coerceAtLeast(margin * 2))
        return IntOffset(
            x,
            (keypadTop - popupContentSize.height - margin).coerceIn(margin, maxY),
        )
    }
}
