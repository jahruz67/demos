package dev.demos.graphing.math

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class NumericCompilerTest {
    private fun value(source:String,variables:Map<String,Double> = emptyMap())=NumericCompiler.compile(source).evaluate(variables)
    @Test fun powersAreRightAssociativeAndBindBeforeUnaryMinus() {
        assertEquals(-4.0,value("-2^2"),0.0)
        assertEquals(512.0,value("2^3^2"),0.0)
        assertEquals(0.125,value("2^-3"),1e-12)
    }
    @Test fun supportsImplicitProductsAndUnicode() {
        assertEquals(24.0,value("2x(x+1)",mapOf("x" to 3.0)),1e-12)
        assertEquals(15.0,value("x²+2×x",mapOf("x" to 3.0)),1e-12)
        assertEquals(2*PI,value("2π"),1e-12)
        assertEquals(2.0,value("|−2|"),1e-12)
    }
    @Test fun realFunctionsAndDegreeMode() {
        assertEquals(1.0,value("sin(pi/2)"),1e-12)
        assertEquals(3.0,value("log(1000)"),1e-12)
        assertEquals(3.0,value("log(8,2)"),1e-12)
        assertEquals(1.0,NumericCompiler.compile("sin(90)",false).evaluate(),1e-12)
        assertTrue(value("sqrt(-1)").isNaN())
        assertEquals(-2.0,value("(-8)^(1/3)"),1e-12)
        assertEquals(4.0,value("(-8)^(2/3)"),1e-12)
    }
    @Test fun numericalIntegralRespectsBoundVariableScope() {
        assertEquals(435000.0,value("integrate(87x,x,0,100)"),1e-6)
        assertEquals(9.0,value("integrate(x^2,x,0,x)",mapOf("x" to 3.0)),1e-7)
        assertEquals(2.0,value("int(sin(t),t,0,pi)"),1e-7)
        val c=EvaluationContext();c.x=3.0
        assertEquals(12.0,NumericCompiler.compile("integrate(x^2,x,0,x)+x").evaluate(c),1e-7)
    }
    @Test fun restrictionsAndChainedComparisons() {
        assertEquals(1.0,value("x^2{-2<x<2}",mapOf("x" to 1.0)),0.0)
        assertTrue(value("x^2{-2<x<2}",mapOf("x" to 3.0)).isNaN())
        assertEquals(4.0,value("if(x>0,x^2,-x)",mapOf("x" to 2.0)),0.0)
    }
    @Test fun userFunctionsRestoreLocalParameters() {
        val functions=mapOf("f" to NumericFunction(listOf("x"),NumericCompiler.compile("x^2+1")))
        val context=EvaluationContext(functions=functions);context.x=3.0
        assertEquals(8.0,NumericCompiler.compile("f(2)+x").evaluate(context),0.0)
    }
    @Test fun quadratureRejectsUnresolvedSingularityRatherThanReturningInventedNumber() {
        assertTrue(value("integrate(1/x,x,-1,1)").isNaN())
    }
    @Test(expected=IllegalArgumentException::class) fun incompleteExpressionRejected() {
        NumericCompiler.compile("x+(")
    }
}
