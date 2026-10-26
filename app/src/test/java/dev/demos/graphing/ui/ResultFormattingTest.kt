package dev.demos.graphing.ui

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.*
import org.junit.Test

class ResultFormattingTest {
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
}
