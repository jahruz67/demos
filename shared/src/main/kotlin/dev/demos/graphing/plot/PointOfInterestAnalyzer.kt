package dev.demos.graphing.plot

import kotlin.math.*

enum class InterestKind { ROOT, EXTREMUM, INTERSECTION, INTERCEPT }
data class PlotInterest(val x:Double,val y:Double,val kind:InterestKind,val expressionIds:Set<Long>)

/** Refines interesting points on real explicit curves without involving the CAS or UI thread. */
object PointOfInterestAnalyzer {
    fun analyze(program:PlotProgram,view:Viewport,samples:Int=384,cancelled:()->Boolean={false}):List<PlotInterest> {
        if(view.width<=0 || view.height<=0) return emptyList()
        val curves=program.relations.filter {it.kind==PlotKind.Y_FUNCTION && it.inequality==null}.take(24)
        val points=ArrayList<PlotInterest>()
        val context=program.context
        var work=0
        fun stopped()=cancelled() || work>180_000
        fun function(relation:PlotRelation):(Double)->Double = {x ->
            work++
            context.x=x
            relation.expressions[0].evaluate(context)
        }
        fun add(x:Double,y:Double,kind:InterestKind,ids:Set<Long>) {
            if(!x.isFinite() || !y.isFinite() || x !in view.left..view.right || y !in view.bottom..view.top) return
            val px=if(abs(x)<view.width*1e-11) 0.0 else x
            val py=if(abs(y)<view.height*1e-11) 0.0 else y
            val existing=points.indexOfFirst {abs(it.x-px)<view.width*1e-6 && abs(it.y-py)<view.height*1e-6}
            if(existing<0) {
                if(points.size<500) points.add(PlotInterest(px,py,kind,ids))
            } else {
                val previous=points[existing]
                val priority=mapOf(InterestKind.INTERSECTION to 4,InterestKind.ROOT to 3,InterestKind.EXTREMUM to 2,InterestKind.INTERCEPT to 1)
                points[existing]=previous.copy(kind=if(priority.getValue(kind)>priority.getValue(previous.kind)) kind else previous.kind,expressionIds=previous.expressionIds+ids)
            }
        }
        val steps=samples.coerceIn(32,2048)
        val xs=DoubleArray(steps+1) {view.left+view.width*it/steps}
        val values=ArrayList<DoubleArray>()
        for(curve in curves) {
            if(stopped()) break
            val f=function(curve)
            val ys=DoubleArray(xs.size) {i -> if(i%32==0 && stopped()) Double.NaN else f(xs[i])}
            values.add(ys)
            if(0.0 in view.left..view.right) add(0.0,f(0.0),InterestKind.INTERCEPT,setOf(curve.id))
            inspect(xs,ys,view,f,::stopped,onRoot={x -> add(x,0.0,InterestKind.ROOT,setOf(curve.id))},onExtremum={x -> add(x,f(x),InterestKind.EXTREMUM,setOf(curve.id))})
        }
        for(a in values.indices) {
            if(stopped()) break
            for(b in a+1 until values.size) {
                if(stopped()) break
                val first=function(curves[a]);val second=function(curves[b])
                val difference:(Double)->Double={x -> first(x)-second(x)}
                val differences=DoubleArray(xs.size) {values[a][it]-values[b][it]}
                inspect(xs,differences,view,difference,::stopped,onRoot={x ->
                    val firstY=first(x);val secondY=second(x)
                    // Reject apparent intersections caused by unresolved poles in either curve.
                    if(firstY.isFinite() && secondY.isFinite() && abs(firstY-secondY)<=max(1e-8,view.height*1e-7)) {
                        add(x,(firstY+secondY)/2,InterestKind.INTERSECTION,setOf(curves[a].id,curves[b].id))
                    }
                },onExtremum={})
            }
        }
        return points
    }

    private fun inspect(xs:DoubleArray,ys:DoubleArray,view:Viewport,f:(Double)->Double,stopped:()->Boolean,onRoot:(Double)->Unit,onExtremum:(Double)->Unit) {
        for(i in xs.indices) {
            if(i%16==0 && stopped()) return
            val y=ys[i]
            if(!y.isFinite()) continue
            // An interval of zeros represents coincident curves, not isolated intersections.
            if(y==0.0 && ((i>0 && ys[i-1].isFinite() && ys[i-1]!=0.0) || (i<ys.lastIndex && ys[i+1].isFinite() && ys[i+1]!=0.0))) onRoot(xs[i])
            if(i<xs.lastIndex && ys[i+1].isFinite() && y!=0.0 && ys[i+1]!=0.0 && ((y<0)!=(ys[i+1]<0))) {
                refine(xs[i],xs[i+1],y,ys[i+1],view.width,f,stopped)?.let(onRoot)
            }
            if(i==0 || i==xs.lastIndex || !ys[i-1].isFinite() || !ys[i+1].isFinite()) continue
            val left=y-ys[i-1];val right=ys[i+1]-y
            if(!((left<0 && right>0) || (left>0 && right<0))) continue
            fun derivative(x:Double):Double {
                val h=max(view.width*1e-6,max(abs(x)*1e-8,1e-10))
                val lo=f(x-h);val hi=f(x+h)
                return if(lo.isFinite() && hi.isFinite()) (hi-lo)/(2*h) else Double.NaN
            }
            val a=xs[i-1];val b=xs[i+1]
            val da=derivative(a);val db=derivative(b)
            if(!da.isFinite() || !db.isFinite() || (da<0)==(db<0)) continue
            val critical=refine(a,b,da,db,view.width,::derivative,stopped) ?: continue
            val criticalY=f(critical)
            if(!criticalY.isFinite()) continue
            // Require a locally continuous curve; a vertical asymptote cannot be an extremum.
            val nearby=max((b-a)*0.02,view.width*1e-8)
            val before=f(critical-nearby);val after=f(critical+nearby)
            if(!before.isFinite() || !after.isFinite() || abs(before-criticalY)>view.height*0.1 || abs(after-criticalY)>view.height*0.1) continue
            onExtremum(critical)
            // Tangent roots/intersections do not change sign, so test the refined extremum.
            val scale=max(1.0,min(abs(ys[i-1]),abs(ys[i+1])))
            if(abs(criticalY)<=scale*1e-12) onRoot(critical)
        }
    }

    /** A sign change alone is insufficient: the residual must actually converge to zero. */
    private fun refine(a:Double,b:Double,fa:Double,fb:Double,width:Double,f:(Double)->Double,stopped:()->Boolean):Double? {
        if(!fa.isFinite() || !fb.isFinite()) return null
        if(fa==0.0) return a
        if(fb==0.0) return b
        if((fa<0)==(fb<0)) return null
        var left=a;var right=b;var leftValue=fa
        var bestX=if(abs(fa)<abs(fb)) a else b
        var bestResidual=min(abs(fa),abs(fb))
        repeat(70) {iteration ->
            if(iteration%8==0 && stopped()) return null
            val middle=(left+right)/2
            if(middle==left || middle==right) return@repeat
            val value=f(middle)
            if(!value.isFinite()) return null
            if(abs(value)<bestResidual) {bestResidual=abs(value);bestX=middle}
            if(value==0.0) return middle
            if((value<0)==(leftValue<0)) {left=middle;leftValue=value} else right=middle
        }
        val residualTolerance=1e-9*max(1.0,min(abs(fa),abs(fb)))
        return bestX.takeIf {bestResidual<=residualTolerance && right-left<=max(width*1e-9,abs(bestX)*1e-13)}
    }
}
