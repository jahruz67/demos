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
    @Test fun implicitSphereProducesFiniteTrianglesNearRadiusThree() {
        val meshes=PlotGeometry.sample3D(program("x^2+y^2+z^2=9"))
        val vertices=meshes.single().vertices
        assertTrue(vertices.size>1000)
        for(i in vertices.indices step 6) {
            val x=vertices[i];val y=vertices[i+1];val z=vertices[i+2]
            assertTrue(x.isFinite() && y.isFinite() && z.isFinite())
            assertEquals(3.0,sqrt((x*x+y*y+z*z).toDouble()),0.04)
            val normalLength=sqrt((vertices[i+3].pow(2)+vertices[i+4].pow(2)+vertices[i+5].pow(2)).toDouble())
            assertEquals(1.0,normalLength,1e-5)
        }
    }
    @Test fun twoDimensionalRelationBecomesCylinderIn3D() {
        val vertices=PlotGeometry.sample3D(program("y=x^2")).single().vertices
        assertTrue(vertices.isNotEmpty())
        val zs=HashSet<Float>()
        for(i in vertices.indices step 6) {
            assertEquals((vertices[i]*vertices[i]).toDouble(),vertices[i+1].toDouble(),1e-5)
            zs.add(vertices[i+2])
        }
        assertTrue(zs.size>10)
    }
    @Test fun parametricSurfaceAndCancellation() {
        val p=program("(u,v,u^2+v^2)")
        assertEquals(PlotKind.PARAMETRIC_SURFACE,p.relations.single().kind)
        assertTrue(PlotGeometry.sample3D(p).single().vertices.isNotEmpty())
        assertTrue(PlotGeometry.sample3D(p,cancelled={true}).isEmpty())
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
    @Test fun configurableParameterAndImplicitBoundsAreHonored() {
        val p=PlotProgram.compile(listOf(PlotExpression(1,"(t,t^2)",0)),PlotSettings(tMin=2.0,tMax=3.0))
        val curve=PlotGeometry.sample2D(p,Viewport()).strokes.single().coordinates
        assertEquals(2.0,curve[0].toDouble(),0.0)
        assertEquals(3.0,curve[curve.size-2].toDouble(),0.0)
        val shiftedSphere=PlotProgram.compile(listOf(PlotExpression(1,"(x-20)^2+y^2+z^2=9",0)),PlotSettings(xMin=15.0,xMax=25.0))
        val mesh=PlotGeometry.sample3D(shiftedSphere).single().vertices
        assertTrue(mesh.isNotEmpty())
        for(i in mesh.indices step 6) assertTrue(mesh[i] in 16.9f..23.1f)
    }
}
