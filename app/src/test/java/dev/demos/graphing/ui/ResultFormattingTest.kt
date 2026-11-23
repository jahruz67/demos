package dev.demos.graphing.ui

import androidx.compose.ui.text.AnnotatedString
import dev.demos.graphing.data.GraphDocument
import dev.demos.graphing.math.FastArithmetic
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ResultFormattingTest {
    @Test fun decimalPlacesCountFractionalDigitsWithoutRoundingTheWholeNumberPart() {
        assertEquals("1234.57", formatResultNumber("1234.56789", 2))
        assertEquals("12345678901234567890.57", formatResultNumber("12345678901234567890.56789", 2))
        assertEquals("1.01", formatResultNumber("1.005", 2))
        assertEquals("-1.01", formatResultNumber("-1.005", 2))
        assertEquals("3.14", formatResultNumber("0.3141592653589793e1", 2))
        assertEquals("0.00000123", formatResultNumber("1.23456e-6", 8))
        assertEquals("x+1.2345", formatResultNumber("x+1.2345", 2))
        assertEquals("1/3", formatResultNumber("1/3", 2))
    }

    @Test fun zeroDecimalPlacesAndTrailingZerosDoNotLeaveADecimalPoint() {
        assertEquals("13", formatResultNumber("12.6", 0))
        assertEquals("12", formatResultNumber("12.00000", 4))
        assertEquals("12.34", formatResultNumber("12.34000", 4))
        assertEquals("10", formatResultNumber("9.999", 2))
        assertEquals("0", formatResultNumber("-0.004", 2))
        assertEquals("0", formatResultNumber("1e-300000", 2))
        assertEquals("1e300000", formatResultNumber("1e300000", 2))
    }

    @Test fun exactArithmeticUsesDecimalPlacesEvenForLargeWholeNumberParts() {
        assertEquals("0.13", FastArithmetic.evaluate("1/8", 2)?.approximate)
        assertEquals("1", FastArithmetic.evaluate("2/3", 0)?.approximate)
        assertEquals("12345678901234567890.33", FastArithmetic.evaluate("12345678901234567890+1/3", 2)?.approximate)
        assertEquals("0", FastArithmetic.evaluate("1/1000000", 2)?.approximate)
    }

    @Test fun savedDecimalPlacesAreIndependentOfCalculationPrecisionAndOlderGraphsStillLoad() {
        val document = GraphDocument(precisionDigits = 40, decimalPlaces = 0)
        val restored = Json.decodeFromString<GraphDocument>(Json.encodeToString(document))
        assertEquals(0, restored.decimalPlaces)
        assertEquals(40, restored.precisionDigits)
        assertEquals(3, Json.decodeFromString<GraphDocument>("""{"precisionDigits":40}""").decimalPlaces)
        assertEquals(3, GraphDocument().decimalPlaces)
        assertEquals(3, CalculatorUiState(emptyList(), 0).decimalPlaces)
    }

    @Test fun ordinaryMagnitudesUseDecimalsWithoutLosingPrecision() {
        assertEquals("743.136595788418", formatResultNumber("0.743136595788418e3"))
        assertEquals("-0.00001234567890123456789", formatResultNumber("-1.234567890123456789e-5"))
        assertEquals("1e-30", formatResultNumber("1e-30"))
        assertEquals("1e300000", formatResultNumber("1e300000"))
        assertEquals("x+1e3", formatResultNumber("x+1e3"))
    }

    @Test fun plainPiDisplaysAsOneSymbolWithSourceOffsetsPreserved() {
        val transformed = LatexVisualTransformation.filter(AnnotatedString("pi+pilot"))
        assertEquals("π+pilot", transformed.text.text)
        assertEquals(2, transformed.offsetMapping.transformedToOriginal(1))
        assertEquals(1, transformed.offsetMapping.originalToTransformed(2))
    }
    @Test fun emptyLatexArgumentsHaveNoBoxesAndKeepValidCaretMappings() {
        val cases = mapOf("\\frac{}{}" to "⁄", "\\sqrt{}" to "√()", "\\sqrt[3]{}" to "3√()", "x^{}" to "x", "x_{}" to "x")
        for ((source, expected) in cases) {
            val transformed = LatexVisualTransformation.filter(AnnotatedString(source))
            assertEquals(source, expected, transformed.text.text)
            val forward = (0..source.length).map(transformed.offsetMapping::originalToTransformed)
            val backward = (0..expected.length).map(transformed.offsetMapping::transformedToOriginal)
            assertTrue(source, forward.all { it in 0..expected.length })
            assertTrue(source, backward.all { it in 0..source.length })
            assertEquals(source, forward.sorted(), forward)
            assertEquals(source, backward.sorted(), backward)
        }
    }
}
