package dev.demos.graphing.ui

import org.junit.Assert.*
import org.junit.Test

class MathDisplayTest {
    private fun text(value: String) = MathDisplay.Text(value)
    private fun fraction(a: String, b: String) = MathDisplay.Fraction(text(a), text(b))

    @Test fun plainDivisionAndLatexUseTheSameStackedFraction() {
        val expected = fraction("900", "8")
        assertEquals(expected, mathDisplay("900/8"))
        assertEquals(expected, mathDisplay("900 ÷ 8"))
        assertEquals(expected, mathDisplay("\\frac{900}{8}"))
        assertEquals(expected, mathDisplay("$$\\dfrac{900}{8}$$"))
    }

    @Test fun precedenceAndAssociativityKeepTheOriginalMeaning() {
        assertEquals(MathDisplay.Row(listOf(text("1"), text("+"), fraction("900", "8"))), mathDisplay("1+900/8"))
        assertEquals(MathDisplay.Row(listOf(fraction("900", "8"), text("*"), text("2"))), mathDisplay("900/8*2"))
        assertEquals(MathDisplay.Fraction(fraction("900", "8"), text("2")), mathDisplay("900/8/2"))
        assertEquals(MathDisplay.Fraction(text("900"), fraction("8", "2")), mathDisplay("900/(8/2)"))
        assertEquals(fraction("-900", "-8"), mathDisplay("-900/-8"))
        assertEquals(fraction("1e-3", "8"), mathDisplay("1e-3/8"))
        assertEquals(fraction("x^2", "3"), mathDisplay("x^2/3"))
    }

    @Test fun fractionsCanAppearInEquationsAndFunctions() {
        assertTrue(mathDisplay("y=900/8+x/2").hasFraction())
        assertTrue(mathDisplay("sin(x/2)").hasFraction())
        assertTrue(mathDisplay("\\frac{1}{\\frac{2}{3}}").hasFraction())
        assertEquals(MathDisplay.Fraction(
            MathDisplay.Row(listOf(text("x"), text("+"), text("1"))),
            MathDisplay.Row(listOf(text("x"), text("-"), text("1"))),
        ), mathDisplay("(x+1)/(x-1)"))
        assertEquals(MathDisplay.Row(listOf(text("x"), text("<="), fraction("1", "2"))), mathDisplay("x<=1/2"))
    }

    @Test fun editingAnIncompleteFractionShowsAnEmptySlot() {
        assertEquals(fraction("900", "□"), mathDisplay("900/"))
        assertEquals(fraction("□", "8"), mathDisplay("/8"))
        assertEquals(fraction("□", "□"), mathDisplay("\\frac{}{}"))
        assertFalse(mathDisplay("\\frac{900").hasFraction())
    }

    @Test fun ordinaryPowersStayTogetherForSuperscriptRendering() {
        assertEquals(text("x^(2+1)"), mathDisplay("x^(2+1)"))
        assertEquals(fraction("x^(1/2)", "3"), mathDisplay("x^(1/2)/3"))
        assertEquals(text("sin(x)"), mathDisplay("sin(x)"))
    }
}
