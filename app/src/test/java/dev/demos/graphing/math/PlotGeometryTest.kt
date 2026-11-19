package dev.demos.graphing.math

import dev.demos.graphing.plot.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class PlotGeometryTest {
    private fun program(vararg sources:String,parameters:Map<String,Double> = emptyMap())=PlotProgram.compile(sources.mapIndexed {i,s -> PlotExpression(i.toLong(),s,0xFF6042A6.toInt())},PlotSettings(parameters=parameters))
    @Test fun resolvesAssignmentsFunctionsAndSliderOverrides() {
        val p=program("a=2","f(x)=a*x^2","y=f(x)+1",parameters=mapOf("a" to 3.0))
        assertEquals(2,p.relations.size)
        p.context.x=2.0
        assertEquals(13.0,p.relations.last().expressions[0].evaluate(p.context),0.0)
    }
    @Test fun asymptoteDoesNotCreateLineAcrossGraph() {
        val geometry=PlotGeometry.sample2D(program("y=1/x"),Viewport(),801)
        assertTrue(geometry.strokes.size>=2)
        for(stroke in geometry.strokes) {
            val xs=stroke.coordinates.filterIndexed {i,_ -> i%2==0}
            assertFalse("No stroke should cross a pole",xs.any {it<0} && xs.any {it>0})
        }
    }
    @Test fun samplesDefiniteIntegralAsContinuousFunction() {
        val geometry=PlotGeometry.sample2D(program("y=integrate(t^2,t,0,x)"),Viewport(width=4.0,height=8.0),200)
        assertTrue(geometry.strokes.isNotEmpty())
        for(line in geometry.strokes) for(i in line.coordinates.indices step 2) {
            val x=line.coordinates[i].toDouble();val y=line.coordinates[i+1].toDouble()
            assertEquals(x*x*x/3,y,1e-5)
        }
    }
    @Test fun implicitCircleContoursFollowEquation() {
        val geometry=PlotGeometry.sample2D(program("x^2+y^2=9"),Viewport(width=10.0,height=10.0))
        assertTrue(geometry.strokes.size>100)
        for(line in geometry.strokes) for(i in line.coordinates.indices step 2) {
            val x=line.coordinates[i];val y=line.coordinates[i+1]
            assertEquals(9.0,(x*x+y*y).toDouble(),0.01)
        }
    }
    @Test fun adjacentCoordinatesPlotAsAFieldSurfaceOrImplicitRelation() {
        assertEquals(PlotKind.XY_FIELD, program("xy").relations.single().kind)
        val surface = program("z=xy")
        assertEquals(PlotKind.Z_SURFACE, surface.relations.single().kind)
        assertEquals(6.0, surface.relations.single().expressions.single().evaluate(mapOf("x" to 2.0, "y" to 3.0)), 0.0)
        val hyperbola = program("xy=1")
        assertEquals(PlotKind.IMPLICIT, hyperbola.relations.single().kind)
        val geometry = PlotGeometry.sample2D(hyperbola, Viewport(width = 6.0, height = 6.0))
        assertTrue(geometry.strokes.isNotEmpty())
        for (line in geometry.strokes) for (i in line.coordinates.indices step 2) {
            assertEquals(1.0, (line.coordinates[i] * line.coordinates[i + 1]).toDouble(), .01)
        }
    }
    @Test fun nativeGraphTranslationRecognizesCoordinateProducts() {
        val sources = listOf("xy=1", "z=xy")
        val symbols = MathSyntax.symbols(sources)
        val translations = sources.map { GeoGebraExpressions.translate(it, "g", symbols.variables, symbols.functions, PlotSettings()) }
        assertEquals("implicit_2d", translations[0].kind)
        assertEquals("g:x*y=1", translations[0].command.replace(" ", ""))
        assertEquals("explicit_3d", translations[1].kind)
        assertEquals("g(x,y)=x*y", translations[1].command.replace(" ", ""))
        assertTrue(translations.all { it.undefined.isEmpty() })
    }
    @Test fun bareExpressionInXAndYIsNotGraphedIn2D() {
        val p=program("y√x − y² −2x +7y")
        assertEquals(PlotKind.XY_FIELD,p.relations.single().kind)
        val node=p.relations.single().expressions.single()
        assertEquals(setOf("x","y"),node.variables)
        assertEquals(-2.0,node.evaluate(mapOf("x" to 1.0, "y" to 0.0)),1e-8)
        assertEquals(5.0,node.evaluate(mapOf("x" to 1.0, "y" to 1.0)),1e-8)
        val geometry=PlotGeometry.sample2D(p,Viewport(centerX=2.0,centerY=4.0,width=2.0,height=10.0))
        assertTrue(geometry.strokes.isEmpty())
        assertTrue(geometry.fills.isEmpty())
    }
    @Test fun inequalityProducesShadedRegions() {
        assertTrue(PlotGeometry.sample2D(program("y<x^2"),Viewport()).fills.isNotEmpty())
        assertTrue(PlotGeometry.sample2D(program("x^2+y^2<9"),Viewport()).fills.isNotEmpty())
    }
    @Test fun tablesAndListTuplesProducePointsAndEvaluateCells() {
        val p=program("a=2","table([1,2,a+1],[2,4,6])")
        val geometry=PlotGeometry.sample2D(p,Viewport())
        assertEquals(3,geometry.strokes.size)
        assertTrue(geometry.strokes.all {it.points})
        assertArrayEquals(floatArrayOf(3f,6f),geometry.strokes.last().coordinates,0f)
        val tuple=PlotGeometry.sample2D(program("([1,2,3],5)"),Viewport())
        assertEquals(3,tuple.strokes.size)
        assertTrue(tuple.strokes.all {it.coordinates[1]==5f})
    }
    @Test fun configurableParameterBoundsAreHonored() {
        val p=PlotProgram.compile(listOf(PlotExpression(1,"(t,t^2)",0)),PlotSettings(tMin=2.0,tMax=3.0))
        val curve=PlotGeometry.sample2D(p,Viewport()).strokes.single().coordinates
        assertEquals(2.0,curve[0].toDouble(),0.0)
        assertEquals(3.0,curve[curve.size-2].toDouble(),0.0)

    }
}
