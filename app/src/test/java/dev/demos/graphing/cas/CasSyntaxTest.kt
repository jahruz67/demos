package dev.demos.graphing.cas

import org.junit.Assert.*
import org.junit.Test

class CasSyntaxTest {
    @Test fun unicodeAndLatexIntegralSyntaxIsAdaptedWithoutDoingTheMath() {
        assertEquals("integrate(x^2,x)", CasSyntax.normalize("∫ x² dx"))
        assertEquals("integrate((87x),x,0,100)", CasSyntax.normalize("∫_0^100 (87x) dx"))
        assertEquals("integrate(x^2,x,0,x)", CasSyntax.normalize("∫_0^x x² dx"))
        assertEquals("integrate(x^2,x,0,100)", CasSyntax.normalize("\\int_{0}^{100} x^2 dx"))
        assertTrue(CasSyntax.prepare("integrate(x^2,x)").indefiniteIntegral)
        assertFalse(CasSyntax.prepare("integrate(x^2,x,0,1)").indefiniteIntegral)
        assertFalse(CasSyntax.prepare("diff(integrate(x^2,x),x)").indefiniteIntegral)
    }

    @Test fun regressionShorthandUsesNativeRegressionAndExportsDefinitions() {
        assertEquals("[linear_regression(x_1,y_1)]", CasSyntax.normalize("y_1~m*x_1+b"))
        assertEquals(listOf("m", "b"), CasSyntax.prepare("y_1~m*x_1+b").regressionParameters)
        assertEquals("demos_fit:=linear_regression(x_1,y_1);m:=demos_fit[0];b:=demos_fit[1]", CasSyntax.regressionDefinition("y_1~m*x_1+b"))
    }

    @Test fun nativeCommandsAndDefinitionsKeepTheirSemantics() {
        assertEquals("a:=sqrt(2)", CasSyntax.normalize("a:=sqrt(2)"))
        assertEquals("solve([x+y=3,x-y=1],[x,y])", CasSyntax.normalize("solve([x+y=3,x-y=1],[x,y])"))
        assertEquals("sin(pi*x)", CasSyntax.normalize("sin(π×x)"))
    }

    @Test fun incompleteExpressionsAreRejectedBeforeNativeParsing() {
        listOf("sqrt(", "∫ x²", "∫_0^ x dx", "[1,2)").forEach {
            assertTrue(it, runCatching { CasSyntax.prepare(it) }.isFailure)
        }
    }
}
