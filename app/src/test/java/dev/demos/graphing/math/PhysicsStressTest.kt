package dev.demos.graphing.math

import dev.demos.graphing.cas.CasSyntax
import dev.demos.graphing.stress.PhysicsStressCases
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

class PhysicsStressTest {
    @Test fun tenSubstitutedPhysicsEquationsMatchIndependentHighPrecisionAnswers() {
        for (case in PhysicsStressCases.all) {
            val tolerance = max(1e-12, abs(case.expected) * 1e-11)
            assertEquals(case.name, case.expected, NumericCompiler.compile(case.expression).evaluate(), tolerance)
            assertEquals("${case.name} LaTeX", case.expected,
                NumericCompiler.compile(case.latex.substringAfter('=')).evaluate(), tolerance)
            assertEquals("${case.name} original equation", 0.0,
                NumericCompiler.compile(case.residual).evaluate(mapOf("x" to case.expected)), 1e-10)
            assertTrue(case.name, CasSyntax.normalize(case.equation).isNotBlank())
            assertTrue(case.name, CasSyntax.normalize(case.latex).isNotBlank())
        }
    }
}
