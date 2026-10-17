package dev.demos.graphing.plot

import kotlin.math.*

data class PlotStroke(val id:Long,val color:Int,val coordinates:FloatArray,val points:Boolean=false)
data class PlotFill(val color:Int,val coordinates:FloatArray)
data class Geometry2D(val strokes:List<PlotStroke> = emptyList(),val fills:List<PlotFill> = emptyList(),val interests:List<PlotInterest> = emptyList())
data class Mesh3D(val color:Int,val vertices:FloatArray,val primitive:Int=TRIANGLES) {
    companion object { const val TRIANGLES=4;const val LINES=1;const val LINE_STRIP=3;const val POINTS=0 }
}

object PlotGeometry {
    fun sample2D(program:PlotProgram,view:Viewport,samples:Int=800,cancelled:()->Boolean={false}):Geometry2D {
        val strokes=ArrayList<PlotStroke>();val fills=ArrayList<PlotFill>();val c=program.context
        for(relation in program.relations) {
            if(cancelled()) break
            val node=relation.expressions[0]
            when(relation.kind) {
                PlotKind.Y_FUNCTION,PlotKind.X_FUNCTION,PlotKind.POLAR,PlotKind.PARAMETRIC -> {
                    var line=ArrayList<Float>()
                    fun flush() {if(line.size>=4) strokes.add(PlotStroke(relation.id,relation.color,line.toFloatArray()));line=ArrayList()}
                    val parameterBounds=bounds(program.settings.tMin,program.settings.tMax,10.0)
                    val start=when(relation.kind) {PlotKind.POLAR -> 0.0;PlotKind.PARAMETRIC -> parameterBounds.first;PlotKind.X_FUNCTION -> view.bottom;else -> view.left}
                    val end=when(relation.kind) {PlotKind.POLAR -> 2*PI;PlotKind.PARAMETRIC -> parameterBounds.second;PlotKind.X_FUNCTION -> view.top;else -> view.right}
                    var previousX=Double.NaN;var previousY=Double.NaN;var previousT=start
                    for(i in 0..samples) {
                        if(i%64==0 && cancelled()) return Geometry2D(strokes,fills)
                        val t=start+(end-start)*i/samples
                        c.x=t;c.y=t;c.t=t
                        val result=node.evaluate(c)
                        val x=when(relation.kind) {PlotKind.X_FUNCTION -> result;PlotKind.POLAR -> result*cos(t);PlotKind.PARAMETRIC -> result;else -> t}
                        val y=when(relation.kind) {PlotKind.X_FUNCTION -> t;PlotKind.POLAR -> result*sin(t);PlotKind.PARAMETRIC -> relation.expressions[1].evaluate(c);else -> result}
                        var broken=!x.isFinite() || !y.isFinite()
                        if(!broken && previousX.isFinite()) {
                            if(abs(x-previousX)>view.width*0.5 || abs(y-previousY)>view.height*0.5) broken=true
                            else if(relation.kind==PlotKind.Y_FUNCTION || relation.kind==PlotKind.X_FUNCTION) {
                                c.x=(previousT+t)/2;c.y=c.x
                                val middle=node.evaluate(c)
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
                    val values=relation.expressions.map {it.evaluate(c)}
                    if(values.take(2).all {it.isFinite()}) strokes.add(PlotStroke(relation.id,relation.color,floatArrayOf(values[0].toFloat(),values[1].toFloat()),true))
                }
                PlotKind.IMPLICIT -> {
                    val nx=150;val ny=max(50,(150*view.height/view.width).toInt()).coerceAtMost(180)
                    val values=DoubleArray((nx+1)*(ny+1))
                    for(j in 0..ny) {
                        if(cancelled()) return Geometry2D(strokes,fills)
                        c.y=view.bottom+view.height*j/ny
                        for(i in 0..nx) {c.x=view.left+view.width*i/nx;values[j*(nx+1)+i]=node.evaluate(c)}
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
                PlotKind.Z_SURFACE,PlotKind.PARAMETRIC_SURFACE -> Unit
            }
        }
        return Geometry2D(strokes,fills,PointOfInterestAnalyzer.analyze(program,view,cancelled=cancelled))
    }
    private fun inequality(value:Double,op:String)=if(op.startsWith('<') || op=="≤") value<=0 else value>=0
    private fun sampleInequality(program:PlotProgram,r:PlotRelation,v:Viewport,fills:MutableList<PlotFill>,cancelled:()->Boolean) {
        val c=program.context;val count=220
        for(i in 0 until count) {
            if(cancelled()) return
            val a=if(r.kind==PlotKind.Y_FUNCTION) v.left+v.width*i/count else v.bottom+v.height*i/count
            val b=a+(if(r.kind==PlotKind.Y_FUNCTION) v.width else v.height)/count
            c.x=(a+b)/2;c.y=c.x
            val f=r.expressions[0].evaluate(c)
            if(!f.isFinite()) continue
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

    fun sample3D(program:PlotProgram,radius:Double=5.0,cancelled:()->Boolean={false}):List<Mesh3D> {
        val meshes=ArrayList<Mesh3D>();val c=program.context
        for(r in program.relations) {
            if(cancelled()) break
            when(r.kind) {
                PlotKind.Z_SURFACE,PlotKind.Y_FUNCTION,PlotKind.X_FUNCTION,PlotKind.PARAMETRIC_SURFACE -> {
                    val out=FloatBuilder();val resolution=64
                    val s=program.settings
                    val xBounds=bounds(s.xMin,s.xMax,radius);val yBounds=bounds(s.yMin,s.yMax,radius);val zBounds=bounds(s.zMin,s.zMax,radius)
                    val uBounds=when(r.kind) {PlotKind.Y_FUNCTION -> xBounds;PlotKind.X_FUNCTION -> yBounds;PlotKind.PARAMETRIC_SURFACE -> bounds(s.uMin,s.uMax,radius);else -> xBounds}
                    val vBounds=when(r.kind) {PlotKind.Y_FUNCTION,PlotKind.X_FUNCTION -> zBounds;PlotKind.PARAMETRIC_SURFACE -> bounds(s.vMin,s.vMax,radius);else -> yBounds}
                    val span=maxOf(uBounds.second-uBounds.first,vBounds.second-vBounds.first,radius)
                    fun insideConfiguredBounds(p:Vec3)=
                        (s.xMin==null || p.x>=s.xMin) && (s.xMax==null || p.x<=s.xMax) &&
                        (s.yMin==null || p.y>=s.yMin) && (s.yMax==null || p.y<=s.yMax) &&
                        (s.zMin==null || p.z>=s.zMin) && (s.zMax==null || p.z<=s.zMax)
                    fun point(u:Double,v:Double):Vec3 {
                        c.x=u;c.y=v;c.z=v;c.locals["u"]=u;c.locals["v"]=v
                        val value=r.expressions[0].evaluate(c)
                        return when(r.kind) {
                            PlotKind.Y_FUNCTION -> Vec3(u,value,v)
                            PlotKind.X_FUNCTION -> {c.y=u;Vec3(r.expressions[0].evaluate(c),u,v)}
                            PlotKind.PARAMETRIC_SURFACE -> Vec3(value,r.expressions[1].evaluate(c),r.expressions[2].evaluate(c))
                            else -> Vec3(u,v,value)
                        }
                    }
                    for(j in 0 until resolution) {
                        if(cancelled()) return meshes
                        val v0=vBounds.first+(vBounds.second-vBounds.first)*j/resolution;val v1=vBounds.first+(vBounds.second-vBounds.first)*(j+1)/resolution
                        for(i in 0 until resolution) {
                            val u0=uBounds.first+(uBounds.second-uBounds.first)*i/resolution;val u1=uBounds.first+(uBounds.second-uBounds.first)*(i+1)/resolution
                            val a=point(u0,v0);val b=point(u1,v0);val d=point(u0,v1);val e=point(u1,v1)
                            if(listOf(a,b,d,e).all {it.finite && it.maxAbs<span*100 && insideConfiguredBounds(it)} && maxOf((a-b).length,(a-d).length,(b-e).length)<span*3) {out.triangle(a,b,d);out.triangle(b,e,d)}
                        }
                    }
                    meshes.add(Mesh3D(r.color,out.array()))
                }
                PlotKind.IMPLICIT -> meshes.add(Mesh3D(r.color,implicitMesh(program,r,radius,cancelled)))
                PlotKind.PARAMETRIC,PlotKind.POLAR -> {
                    var out=FloatBuilder()
                    fun flush() {if(out.size>=12) meshes.add(Mesh3D(r.color,out.array(),Mesh3D.LINE_STRIP));out=FloatBuilder()}
                    var last:Vec3?=null
                    for(i in 0..1200) {
                        if(i%64==0 && cancelled()) return meshes
                        val tb=bounds(program.settings.tMin,program.settings.tMax,10.0)
                        val t=if(r.kind==PlotKind.POLAR) 2*PI*i/1200 else tb.first+(tb.second-tb.first)*i/1200
                        c.t=t
                        val values=r.expressions.map {it.evaluate(c)}
                        val p=if(r.kind==PlotKind.POLAR) Vec3(values[0]*cos(t),values[0]*sin(t),0.0) else Vec3(values[0],values[1],values.getOrElse(2) {0.0})
                        if(!p.finite || (last!=null && (p-last).length>radius)) flush()
                        if(p.finite) out.vertex(p,Vec3(0.0,0.0,1.0))
                        last=if(p.finite) p else null
                    }
                    flush()
                }
                PlotKind.POINT -> {
                    val values=r.expressions.map {it.evaluate(c)}
                    val p=Vec3(values[0],values[1],values.getOrElse(2){0.0})
                    if(p.finite) {val out=FloatBuilder();out.vertex(p,Vec3(0.0,0.0,1.0));meshes.add(Mesh3D(r.color,out.array(),Mesh3D.POINTS))}
                }
            }
        }
        return meshes
    }
    private fun implicitMesh(program:PlotProgram,r:PlotRelation,radius:Double,cancelled:()->Boolean):FloatArray {
        val n=36;val c=program.context;val out=FloatBuilder()
        val values=DoubleArray((n+1)*(n+1)*(n+1))
        fun index(i:Int,j:Int,k:Int)=(k*(n+1)+j)*(n+1)+i
        val s=program.settings
        val xb=bounds(s.xMin,s.xMax,radius);val yb=bounds(s.yMin,s.yMax,radius);val zb=bounds(s.zMin,s.zMax,radius)
        fun coordinate(i:Int,b:Pair<Double,Double>)=b.first+(b.second-b.first)*i/n
        for(k in 0..n) {
            if(cancelled()) return FloatArray(0)
            c.z=coordinate(k,zb)
            for(j in 0..n) {c.y=coordinate(j,yb);for(i in 0..n) {c.x=coordinate(i,xb);values[index(i,j,k)]=r.expressions[0].evaluate(c)}}
        }
        val corners=arrayOf(intArrayOf(0,0,0),intArrayOf(1,0,0),intArrayOf(1,1,0),intArrayOf(0,1,0),intArrayOf(0,0,1),intArrayOf(1,0,1),intArrayOf(1,1,1),intArrayOf(0,1,1))
        val tetrahedra=arrayOf(intArrayOf(0,5,1,6),intArrayOf(0,1,2,6),intArrayOf(0,2,3,6),intArrayOf(0,3,7,6),intArrayOf(0,7,4,6),intArrayOf(0,4,5,6))
        for(k in 0 until n) {
            if(cancelled()) return FloatArray(0)
            for(j in 0 until n) for(i in 0 until n) {
                val v=DoubleArray(8) {val q=corners[it];values[index(i+q[0],j+q[1],k+q[2])]}
                if(v.any {!it.isFinite()} || v.all {it>0} || v.all {it<=0}) continue
                val p=Array(8) {val q=corners[it];Vec3(coordinate(i+q[0],xb),coordinate(j+q[1],yb),coordinate(k+q[2],zb))}
                for(t in tetrahedra) {
                    val inside=t.filter {v[it]<=0};val outside=t.filter {v[it]>0}
                    fun cross(a:Int,b:Int)=p[a]+(p[b]-p[a])*(v[a]/(v[a]-v[b]))
                    if(inside.size==1 || outside.size==1) {
                        val one=if(inside.size==1) inside[0] else outside[0]
                        val rest=if(inside.size==1) outside else inside
                        val a=cross(one,rest[0]);val b=cross(one,rest[1]);val d=cross(one,rest[2])
                        val direction=if(inside.size==1) p[rest[0]]-p[one] else p[one]-p[rest[0]]
                        out.orientedTriangle(a,b,d,direction)
                    } else if(inside.size==2) {
                        val a=cross(inside[0],outside[0]);val b=cross(inside[0],outside[1]);val d=cross(inside[1],outside[0]);val e=cross(inside[1],outside[1])
                        val direction=p[outside[0]]-p[inside[0]]
                        out.orientedTriangle(a,b,d,direction);out.orientedTriangle(b,e,d,direction)
                    }
                }
            }
        }
        return out.array()
    }
    private fun bounds(low:Double?,high:Double?,radius:Double):Pair<Double,Double> {
        val a=low?:-radius;val b=high?:radius
        return if(a.isFinite() && b.isFinite() && a<b) a to b else -radius to radius
    }
}
internal data class Vec3(val x:Double,val y:Double,val z:Double) {
    val finite get()=x.isFinite() && y.isFinite() && z.isFinite()
    val maxAbs get()=maxOf(abs(x),abs(y),abs(z))
    val length get()=sqrt(x*x+y*y+z*z)
    operator fun plus(b:Vec3)=Vec3(x+b.x,y+b.y,z+b.z)
    operator fun minus(b:Vec3)=Vec3(x-b.x,y-b.y,z-b.z)
    operator fun times(f:Double)=Vec3(x*f,y*f,z*f)
    fun cross(b:Vec3)=Vec3(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x)
    fun dot(b:Vec3)=x*b.x+y*b.y+z*b.z
    fun normalized()=this*(1.0/length.coerceAtLeast(1e-20))
}
internal class FloatBuilder {
    private var data=FloatArray(2048)
    var size=0;private set
    private fun add(f:Double) {if(size==data.size)data=data.copyOf(data.size*2);data[size++]=f.toFloat()}
    fun vertex(p:Vec3,n:Vec3) {add(p.x);add(p.y);add(p.z);add(n.x);add(n.y);add(n.z)}
    fun triangle(a:Vec3,b:Vec3,c:Vec3) {
        val cross=(b-a).cross(c-a)
        if(cross.length<1e-14) return
        val n=cross.normalized();vertex(a,n);vertex(b,n);vertex(c,n)
    }
    fun orientedTriangle(a:Vec3,b:Vec3,c:Vec3,direction:Vec3) {if((b-a).cross(c-a).dot(direction)<0)triangle(a,c,b) else triangle(a,b,c)}
    fun array()=data.copyOf(size)
}
