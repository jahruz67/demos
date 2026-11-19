package dev.demos.graphing.math

import org.junit.Assert.*
import org.junit.Test

class MathSyntaxTest {
    @Test fun adjacentCoordinatesAreProductsWithNormalPrecedence() {
        assertEquals("x*y", MathSyntax.explicitProducts("xy"))
        assertEquals("2*x*y^2+x*y*(x+1)", MathSyntax.explicitProducts("2xy^2+xy(x+1)"))
        assertEquals("x*y*z+u*v+r*t", MathSyntax.explicitProducts("xyz+uv+rt"))
        assertNull(MathSyntax.definition("xy=1"))
        assertEquals(emptySet<String>(), MathSyntax.symbols(listOf("xy=1")).variables)
    }

    @Test fun namedQuantitiesFunctionsAndParametersKeepTheirNames() {
        assertEquals("rate+x_1+sqrt(x)+2e-3", MathSyntax.explicitProducts("rate+x_1+sqrt(x)+2e-3"))
        val sources = listOf("xy:=5", "uv(x)=x+1")
        assertEquals("xy+uv(x)", MathSyntax.explicitProducts("xy+uv(x)", MathSyntax.symbols(sources)))
        assertEquals("xy:=x*y", MathSyntax.explicitProducts("xy:=x*y"))
        assertEquals("uv(xy)=xy+x*y", MathSyntax.explicitProducts("uv(xy)=xy+x*y"))
    }

    @Test fun coordinateProductsLoadTheirDefinitionsAndRespectParameterScope() {
        val sources = listOf("x:=2", "y:=3", "unrelated:=9")
        assertEquals(sources.take(2), MathSyntax.relevantDefinitions("xy", sources))
        assertEquals(listOf("f(uv):=uv+x*y"), MathSyntax.relevantDefinitions("f(2)", listOf("uv:=9", "f(uv):=uv+x*y")))
        assertEquals(listOf("xy:=5"), MathSyntax.relevantDefinitions("xy", listOf("x:=2", "y:=3", "xy:=5")))
    }
}
