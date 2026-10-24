package dev.demos.graphing.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test
import dev.demos.graphing.math.FastArithmetic
import dev.demos.graphing.math.NumericCompiler

class MathEditingTest {
    private fun type(value: TextFieldValue, text: String): TextFieldValue {
        var next = value
        for (c in text) {
            val at = next.selection.min
            val proposed = TextFieldValue(next.text.replaceRange(at, next.selection.max, c.toString()), TextRange(at + 1))
            next = MathInputRules.apply(next, proposed)
        }
        return next
    }

    @Test fun slashCreatesEditableSlotsAndArrowsExitThem() {
        var value = type(TextFieldValue(), "900/8")
        assertEquals("\\frac{900}{8}", value.text)
        assertEquals(value.text.lastIndex, value.selection.end)
        value = type(MathInputRules.move(value, 1), "+2")
        assertEquals("\\frac{900}{8}+2", value.text)
    }

    @Test fun divisionWrapsASelectionOrThePrecedingOperand() {
        assertEquals("1+\\frac{900}{}", type(TextFieldValue("1+900", TextRange(5)), "/").text)
        assertEquals("\\frac{1+900}{}", type(TextFieldValue("1+900", TextRange(0, 5)), "/").text)
        assertEquals("\\frac{sin(x)}{}", type(TextFieldValue("sin(x)", TextRange(6)), "/").text)
    }

    @Test fun nestedFractionsRemainValidWhenBackspacing() {
        var value = type(TextFieldValue(), "900/8/2")
        assertEquals("\\frac{900}{\\frac{8}{2}}", value.text)
        value = MathInputRules.backspace(value)
        assertEquals("\\frac{900}{\\frac{8}{}}", value.text)
        value = MathInputRules.backspace(value)
        assertEquals("\\frac{900}{8}", value.text)
        value = MathInputRules.backspace(value)
        assertEquals("\\frac{900}{}", value.text)
        value = MathInputRules.backspace(value)
        assertEquals("900", value.text)
    }

    @Test fun exponentsHaveTheirOwnEditableSlot() {
        var value = type(TextFieldValue(), "2^3")
        assertEquals("2^{3}", value.text)
        assertTrue(editableMath(value.text).slots().any { it.start == 3 && it.end == 4 })
        value = type(MathInputRules.move(value, 1), "+1")
        assertEquals("2^{3}+1", value.text)
    }

    @Test fun rawAndLatexDivisionBothKeepDocumentOffsets() {
        val plain = editableMath("900/8") as EditableMath.Fraction
        assertEquals("900", "900/8".substring(plain.top.start, plain.top.end))
        val latex = editableMath("\\frac{900}{8}") as EditableMath.Fraction
        assertEquals(EditableMath.Text(6, 9), latex.top)
        assertEquals(EditableMath.Text(11, 12), latex.bottom)
        assertTrue(latex.slots().contains(MathSlot(13, 13)))
    }

    @Test fun selectingAcrossFractionSlotsPreservesStructure() {
        val source = "\\frac{900}{82}"
        assertEquals("\\frac{9}{2}", MathInputRules.backspace(TextFieldValue(source, TextRange(7, 12))).text)
        assertEquals("", MathInputRules.backspace(TextFieldValue(source, TextRange(6, 13))).text)
        val replacement = MathInputRules.apply(TextFieldValue(source, TextRange(7, 12)),
            TextFieldValue(source.replaceRange(7, 12, "5"), TextRange(8)))
        assertEquals("\\frac{95}{2}", replacement.text)
    }

    @Test fun structuredInputStillEvaluatesAndPlots() {
        val source = type(TextFieldValue(), "900/8").text
        assertEquals("225/2", FastArithmetic.evaluate(source, 15)?.exact)
        assertEquals(112.5, NumericCompiler.compile(source).evaluate(), 0.0)
        assertEquals("8", FastArithmetic.evaluate("2^{3}", 15)?.exact)
        val root = editableMath("\\sqrt[3]{8}") as EditableMath.Root
        assertEquals(EditableMath.Text(6, 7), root.index)
        assertEquals(EditableMath.Text(9, 10), root.body)
    }
}
