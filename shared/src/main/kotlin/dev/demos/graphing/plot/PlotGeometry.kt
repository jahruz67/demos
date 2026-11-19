package dev.demos.graphing.plot

import kotlin.math.*

data class PlotStroke(val id:Long,val color:Int,val coordinates:FloatArray,val points:Boolean=false)
data class PlotFill(val color:Int,val coordinates:FloatArray)
data class Geometry2D(val strokes:List<PlotStroke> = emptyList(),val fills:List<PlotFill> = emptyList(),val interests:List<PlotInterest> = emptyList())

object PlotGeometry {
    fun sample2D(program:PlotProgram,view:Viewport,samples:Int=800,cancelled:()->Boolean={false}):Geometry2D {
        val strokes=ArrayList<PlotStroke>();val fills=ArrayList<PlotFill>();val c=program.context
        val appearance=program.settings.appearance
        fun realX(x:Double)=axisValue(x,appearance.logX)
        fun realY(y:Double)=axisValue(y,appearance.logY)
        fun plotX(x:Double)=axisCoordinate(x,appearance.logX)
        fun plotY(y:Double)=axisCoordinate(y,appearance.logY)
        for(relation in program.relations) {
            if(cancelled()) break
            val node=relation.expressions[0]
            when(relation.kind) {
                PlotKind.Y_FUNCTION,PlotKind.X_FUNCTION,PlotKind.POLAR,PlotKind.PARAMETRIC -> {
                    var line=ArrayList<Float>()
                    fun flush() {if(line.size>=4) strokes.add(PlotStroke(relation.id,relation.color,line.toFloatArray()));line=ArrayList()}
                    val parameterBounds=bounds(program.settings.tMin,program.settings.tMax,10.0)
                    val start=when(relation.kind) {PlotKind.POLAR -> 0.0;PlotKind.PARAMETRIC -> parameterBounds.first;PlotKind.X_FUNCTION -> view.bottom;else -> view.left}
                    val end=when(relation.kind) {PlotKind.POLAR -> 12*PI;PlotKind.PARAMETRIC -> parameterBounds.second;PlotKind.X_FUNCTION -> view.top;else -> view.right}
                    var previousX=Double.NaN;var previousY=Double.NaN;var previousT=start
                    for(i in 0..samples) {
                        if(i%64==0 && cancelled()) return Geometry2D(strokes,fills)
                        val t=start+(end-start)*i/samples
                        c.x=realX(t);c.y=realY(t);c.t=t
                        val result=node.evaluate(c)
                        val x=when(relation.kind) {PlotKind.X_FUNCTION -> plotX(result);PlotKind.POLAR -> plotX(result*cos(t));PlotKind.PARAMETRIC -> plotX(result);else -> t}
                        val y=when(relation.kind) {PlotKind.X_FUNCTION -> t;PlotKind.POLAR -> plotY(result*sin(t));PlotKind.PARAMETRIC -> plotY(relation.expressions[1].evaluate(c));else -> plotY(result)}
                        var broken=!x.isFinite() || !y.isFinite()
                        if(!broken && previousX.isFinite()) {
                            if(abs(x-previousX)>view.width*0.5 || abs(y-previousY)>view.height*0.5) broken=true
                            else if(relation.kind==PlotKind.Y_FUNCTION || relation.kind==PlotKind.X_FUNCTION) {
                                c.x=realX((previousT+t)/2);c.y=realY((previousT+t)/2)
                                val middle=if(relation.kind==PlotKind.X_FUNCTION) plotX(node.evaluate(c)) else plotY(node.evaluate(c))
                                val expected=if(relation.kind==PlotKind.X_FUNCTION) (x+previousX)/2 else (y+previousY)/2
                                val span=if(relation.kind==PlotKind.X_FUNCTION) view.width else view.height
                                if(!middle.isFinite() || abs(middle-expected)>span*0.2) broken=true
                            }
                        }
                        if(broken) flush()
                        if(x.isFinite() && y.isFinite() && abs(x-view.centerX)<view.width*100 && abs(y-view.centerY)<view.height*100) {line.add(x.toFloat());line.add(y.toFloat())}
                        else flush()
                        previousX=x;previousY=y;previousT=t
                    }
                    flush()
                    if(relation.inequality!=null && relation.kind in setOf(PlotKind.Y_FUNCTION,PlotKind.X_FUNCTION)) {
                        sampleInequality(program,relation,view,fills,cancelled)
                    }
                }
                PlotKind.POINT -> {
                    val values=relation.expressions.mapIndexed { index, expression ->
                        val value=expression.evaluate(c)
                        if(index==0) plotX(value) else plotY(value)
                    }
                    if(values.take(2).all {it.isFinite()}) strokes.add(PlotStroke(relation.id,relation.color,floatArrayOf(values[0].toFloat(),values[1].toFloat()),true))
                }
                PlotKind.IMPLICIT -> {
                    val nx=150;val ny=max(50,(150*view.height/view.width).toInt()).coerceAtMost(180)
                    val values=DoubleArray((nx+1)*(ny+1))
                    for(j in 0..ny) {
                        if(cancelled()) return Geometry2D(strokes,fills)
                        c.y=realY(view.bottom+view.height*j/ny)
                        for(i in 0..nx) {c.x=realX(view.left+view.width*i/nx);values[j*(nx+1)+i]=node.evaluate(c)}
                    }
                    for(j in 0 until ny) for(i in 0 until nx) {
                        val x0=view.left+view.width*i/nx;val x1=x0+view.width/nx
                        val y0=view.bottom+view.height*j/ny;val y1=y0+view.height/ny
                        val v=doubleArrayOf(values[j*(nx+1)+i],values[j*(nx+1)+i+1],values[(j+1)*(nx+1)+i+1],values[(j+1)*(nx+1)+i])
                        if(v.any {!it.isFinite()}) continue
                        if(relation.inequality!=null && inequality(v.average(),relation.inequality)) fills.add(PlotFill(relation.color,floatArrayOf(x0.toFloat(),y0.toFloat(),x1.toFloat(),y0.toFloat(),x1.toFloat(),y1.toFloat(),x0.toFloat(),y1.toFloat())))
                        val xs=doubleArrayOf(x0,x1,x1,x0);val ys=doubleArrayOf(y0,y0,y1,y1)
                        val crossings=ArrayList<Float>()
                        for(edge in 0..3) {
                            val next=(edge+1)%4
                            if((v[edge]<=0)!=(v[next]<=0)) {
                                val f=v[edge]/(v[edge]-v[next])
                                crossings.add((xs[edge]+f*(xs[next]-xs[edge])).toFloat());crossings.add((ys[edge]+f*(ys[next]-ys[edge])).toFloat())
                            }
                        }
                        if(crossings.size>=4) strokes.add(PlotStroke(relation.id,relation.color,crossings.take(4).toFloatArray()))
                        if(crossings.size==8) strokes.add(PlotStroke(relation.id,relation.color,crossings.drop(4).toFloatArray()))
                    }
                }
                PlotKind.Z_SURFACE,PlotKind.XY_FIELD,PlotKind.PARAMETRIC_SURFACE -> Unit
            }
        }
        val x0=realX(view.left);val x1=realX(view.right);val y0=realY(view.bottom);val y1=realY(view.top)
        val realView=Viewport((x0+x1)/2,(y0+y1)/2,x1-x0,y1-y0)
        val interests=if(listOf(x0,x1,y0,y1).all {it.isFinite()} && realView.width>0 && realView.height>0)
            PointOfInterestAnalyzer.analyze(program,realView,cancelled=cancelled).mapNotNull {
                val x=plotX(it.x);val y=plotY(it.y)
                if(x.isFinite() && y.isFinite()) it.copy(x=x,y=y) else null
            } else emptyList()
        return Geometry2D(strokes,fills,interests)
    }
    private fun inequality(value:Double,op:String)=if(op.startsWith('<') || op=="≤") value<=0 else value>=0
    private fun sampleInequality(program:PlotProgram,r:PlotRelation,v:Viewport,fills:MutableList<PlotFill>,cancelled:()->Boolean) {
        val c=program.context;val count=220
        for(i in 0 until count) {
            if(cancelled()) return
            val a=if(r.kind==PlotKind.Y_FUNCTION) v.left+v.width*i/count else v.bottom+v.height*i/count
            val b=a+(if(r.kind==PlotKind.Y_FUNCTION) v.width else v.height)/count
            c.x=axisValue((a+b)/2,program.settings.appearance.logX)
            c.y=axisValue((a+b)/2,program.settings.appearance.logY)
            val raw=r.expressions[0].evaluate(c)
            if(!raw.isFinite()) continue
            val logarithmic=if(r.kind==PlotKind.Y_FUNCTION) program.settings.appearance.logY else program.settings.appearance.logX
            val f=if(logarithmic && raw<=0.0) Double.NEGATIVE_INFINITY else axisCoordinate(raw,logarithmic)
            val lower=r.inequality!!.startsWith('<') || r.inequality=="≤"
            val coords=if(r.kind==PlotKind.Y_FUNCTION) {
                val y0=if(lower) v.bottom else f.coerceIn(v.bottom,v.top)
                val y1=if(lower) f.coerceIn(v.bottom,v.top) else v.top
                floatArrayOf(a.toFloat(),y0.toFloat(),b.toFloat(),y0.toFloat(),b.toFloat(),y1.toFloat(),a.toFloat(),y1.toFloat())
            } else {
                val x0=if(lower) v.left else f.coerceIn(v.left,v.right)
                val x1=if(lower) f.coerceIn(v.left,v.right) else v.right
                floatArrayOf(x0.toFloat(),a.toFloat(),x1.toFloat(),a.toFloat(),x1.toFloat(),b.toFloat(),x0.toFloat(),b.toFloat())
            }
            fills.add(PlotFill(r.color,coords))
        }
    }

    private fun bounds(low:Double?,high:Double?,radius:Double):Pair<Double,Double> {
        val a=low?:-radius;val b=high?:radius
        return if(a.isFinite() && b.isFinite() && a<b) a to b else -radius to radius
    }
}