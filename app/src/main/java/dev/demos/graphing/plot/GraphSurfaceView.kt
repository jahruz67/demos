package dev.demos.graphing.plot

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/** GLES 3 renderer, with meshing done by PlotGeometry off the render/UI threads. */
internal class GraphCameraState {
    @Volatile var yaw=38f
    @Volatile var pitch=28f
    @Volatile var distance=19f
    @Volatile var panX=0f
    @Volatile var panY=0f
    @Volatile var panZ=0f
    var configuredBounds:List<Double?>?=null
}
internal class GraphSurfaceView(context:Context,camera:GraphCameraState=GraphCameraState()):GLSurfaceView(context) {
    private val graphRenderer=SurfaceRenderer(camera)
    private var lastX=0f;private var lastY=0f;private var previousDistance=0f
    private var downX=0f;private var downY=0f;private var downTime=0L;private var transformed=false
    private var lastMeshes:List<Mesh3D>?=null
    private var lastReset=0
    var onInspect:((String)->Unit)?=null
    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8,8,8,8,24,0)
        preserveEGLContextOnPause=true
        setRenderer(graphRenderer)
        renderMode=RENDERMODE_WHEN_DIRTY
        contentDescription="Three-dimensional graph. Drag to rotate. Pinch to zoom. Drag two fingers to pan. Tap a surface to inspect coordinates."
    }
    fun update(meshes:List<Mesh3D>,settings:PlotSettings,reset:Int) {
        if(lastMeshes!==meshes) {lastMeshes=meshes;graphRenderer.setMeshes(meshes)}
        graphRenderer.axes=settings.showAxes;graphRenderer.grid=settings.showGrid
        val bounds=listOf(settings.xMin,settings.xMax,settings.yMin,settings.yMax,settings.zMin,settings.zMax)
        if(reset!=lastReset || graphRenderer.configuredBounds!=bounds) {lastReset=reset;graphRenderer.fitBounds(settings,bounds)}
        requestRender()
    }
    fun zoomBy(scale:Float) {graphRenderer.distance=(graphRenderer.distance/scale).coerceIn(0.001f,1e9f);requestRender()}
    fun release() {onPause()}
    override fun onTouchEvent(event:MotionEvent):Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX=event.x;downY=event.y;downTime=event.eventTime;transformed=false
                lastX=event.x;lastY=event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> if(event.pointerCount>=2) {
                previousDistance=distance(event);lastX=(event.getX(0)+event.getX(1))/2;lastY=(event.getY(0)+event.getY(1))/2;transformed=true
            }
            MotionEvent.ACTION_MOVE -> {
                if(event.pointerCount>=2) {
                    val distance=distance(event)
                    if(previousDistance>1) zoomBy(distance/previousDistance)
                    previousDistance=distance
                    val x=(event.getX(0)+event.getX(1))/2;val y=(event.getY(0)+event.getY(1))/2
                    val scale=graphRenderer.distance/max(1,height)*0.7f
                    val yaw=graphRenderer.yaw*PI.toFloat()/180
                    val pitch=graphRenderer.pitch*PI.toFloat()/180
                    val dx=x-lastX;val dy=y-lastY
                    // Move the camera target opposite screen-space dragging so
                    // the surface follows the fingers at every orbit angle.
                    graphRenderer.panX+=(-cos(yaw)*dx-sin(yaw)*sin(pitch)*dy)*scale
                    graphRenderer.panY+=(-sin(yaw)*dx+cos(yaw)*sin(pitch)*dy)*scale
                    graphRenderer.panZ+=cos(pitch)*dy*scale
                    lastX=x;lastY=y
                } else {
                    val dx=event.x-lastX;val dy=event.y-lastY
                    graphRenderer.yaw-=dx*0.45f
                    graphRenderer.pitch=(graphRenderer.pitch+dy*0.4f).coerceIn(-85f,85f)
                    lastX=event.x;lastY=event.y
                    if(hypot(event.x-downX,event.y-downY)>8) transformed=true
                }
                requestRender()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val remaining=if(event.actionIndex==0) 1 else 0
                if(remaining<event.pointerCount) {lastX=event.getX(remaining);lastY=event.getY(remaining)}
                previousDistance=0f
            }
            MotionEvent.ACTION_UP -> {
                if(!transformed && event.eventTime-downTime<450) {performClick();graphRenderer.inspect(event.x,event.y,width,height)?.let {onInspect?.invoke(it)}}
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
    override fun performClick():Boolean {super.performClick();return true}
    private fun distance(event:MotionEvent)=hypot(event.getX(1)-event.getX(0),event.getY(1)-event.getY(0))
}

private data class BufferedMesh(val color:Int,val buffer:FloatBuffer,val count:Int,val primitive:Int)
private class SurfaceRenderer(private val camera:GraphCameraState):GLSurfaceView.Renderer {
    var yaw:Float
        get()=camera.yaw
        set(value){camera.yaw=value}
    var pitch:Float
        get()=camera.pitch
        set(value){camera.pitch=value}
    var distance:Float
        get()=camera.distance
        set(value){camera.distance=value}
    var panX:Float
        get()=camera.panX
        set(value){camera.panX=value}
    var panY:Float
        get()=camera.panY
        set(value){camera.panY=value}
    var panZ:Float
        get()=camera.panZ
        set(value){camera.panZ=value}
    val configuredBounds get()=camera.configuredBounds
    @Volatile var axes=true
    @Volatile var grid=true
    @Volatile private var pending:List<Mesh3D>?=emptyList()
    @Volatile private var inspectionMeshes:List<Mesh3D> = emptyList()
    private var buffered=emptyList<BufferedMesh>()
    private var axisMeshes=emptyList<BufferedMesh>()
    private var gridMesh:BufferedMesh?=null
    private var program=0;private var mvpLocation=0;private var colorLocation=0;private var lightLocation=0
    private val projection=FloatArray(16);private val view=FloatArray(16)
    private var aspect=1f
    @Volatile private var latestMvp=FloatArray(16)
    fun fitBounds(settings:PlotSettings,bounds:List<Double?>) {
        fun range(a:Double?,b:Double?):Pair<Double,Double> = if(a!=null && b!=null && a.isFinite() && b.isFinite() && a<b) a to b else -5.0 to 5.0
        val x=range(settings.xMin,settings.xMax);val y=range(settings.yMin,settings.yMax);val z=range(settings.zMin,settings.zMax)
        yaw=38f;pitch=28f
        distance=(maxOf(x.second-x.first,y.second-y.first,z.second-z.first)*1.9).toFloat().coerceIn(0.001f,1e9f)
        panX=((x.first+x.second)/2).toFloat();panY=((y.first+y.second)/2).toFloat();panZ=((z.first+z.second)/2).toFloat()
        camera.configuredBounds=bounds
    }
    fun setMeshes(meshes:List<Mesh3D>) {inspectionMeshes=meshes;pending=meshes}
    override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?) {
        GLES30.glClearColor(1f,1f,1f,1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        val vertex="""#version 300 es
            layout(location=0) in vec3 a_position;
            layout(location=1) in vec3 a_normal;
            uniform mat4 u_mvp;
            out vec3 v_normal;
            void main() {gl_Position=u_mvp*vec4(a_position,1.0); gl_PointSize=13.0;v_normal=a_normal;}
        """.trimIndent()
        val fragment="""#version 300 es
            precision mediump float;
            in vec3 v_normal;
            uniform vec4 u_color;
            uniform float u_light;
            out vec4 outColor;
            void main() {
              float diffuse=0.45+0.55*abs(dot(normalize(v_normal),normalize(vec3(0.4,-0.6,0.9))));
              outColor=vec4(u_color.rgb*mix(1.0,diffuse,u_light),u_color.a);
            }
        """.trimIndent()
        program=GLES30.glCreateProgram()
        GLES30.glAttachShader(program,shader(GLES30.GL_VERTEX_SHADER,vertex))
        GLES30.glAttachShader(program,shader(GLES30.GL_FRAGMENT_SHADER,fragment))
        GLES30.glLinkProgram(program)
        val status=IntArray(1);GLES30.glGetProgramiv(program,GLES30.GL_LINK_STATUS,status,0)
        check(status[0]!=0) {"Could not link 3D graph shader: ${GLES30.glGetProgramInfoLog(program)}"}
        mvpLocation=GLES30.glGetUniformLocation(program,"u_mvp")
        colorLocation=GLES30.glGetUniformLocation(program,"u_color")
        lightLocation=GLES30.glGetUniformLocation(program,"u_light")
        axisMeshes=listOf(
            buffer(Mesh3D(0xFFB7473F.toInt(),line(-6f,0f,0f,6f,0f,0f),Mesh3D.LINES)),
            buffer(Mesh3D(0xFF43813F.toInt(),line(0f,-6f,0f,0f,6f,0f),Mesh3D.LINES)),
            buffer(Mesh3D(0xFF3275B8.toInt(),line(0f,0f,-6f,0f,0f,6f),Mesh3D.LINES)),
        )
        val gridVertices=FloatBuilder()
        for(i in -5..5) {
            gridVertices.vertex(Vec3(i.toDouble(),-5.0,0.0),Vec3(0.0,0.0,1.0));gridVertices.vertex(Vec3(i.toDouble(),5.0,0.0),Vec3(0.0,0.0,1.0))
            gridVertices.vertex(Vec3(-5.0,i.toDouble(),0.0),Vec3(0.0,0.0,1.0));gridVertices.vertex(Vec3(5.0,i.toDouble(),0.0),Vec3(0.0,0.0,1.0))
        }
        gridMesh=buffer(Mesh3D(0xFFD5D9DD.toInt(),gridVertices.array(),Mesh3D.LINES))
        pending=inspectionMeshes
    }
    override fun onSurfaceChanged(gl:GL10?,width:Int,height:Int) {
        GLES30.glViewport(0,0,width,height)
        aspect=width.toFloat()/max(1,height)
    }
    override fun onDrawFrame(gl:GL10?) {
        pending?.let {next -> buffered=next.filter {it.vertices.isNotEmpty()}.map(::buffer);if(pending===next)pending=null}
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(program)
        val az=yaw*PI.toFloat()/180;val el=pitch*PI.toFloat()/180
        Matrix.perspectiveM(projection,0,42f,aspect,(distance/10000).coerceAtLeast(0.000001f),distance*30+100)
        Matrix.setLookAtM(view,0,panX+distance*cos(el)*sin(az),panY-distance*cos(el)*cos(az),panZ+distance*sin(el),panX,panY,panZ,0f,0f,1f)
        val mvp=FloatArray(16);Matrix.multiplyMM(mvp,0,projection,0,view,0);latestMvp=mvp
        GLES30.glUniformMatrix4fv(mvpLocation,1,false,mvp,0)
        GLES30.glLineWidth(1.5f)
        if(grid)gridMesh?.let {draw(it)}
        for(mesh in buffered)draw(mesh)
        if(axes) {GLES30.glLineWidth(2.5f);for(axis in axisMeshes)draw(axis)}
    }
    private fun draw(mesh:BufferedMesh) {
        GLES30.glUniform4f(colorLocation,((mesh.color shr 16) and 255)/255f,((mesh.color shr 8) and 255)/255f,(mesh.color and 255)/255f,1f)
        GLES30.glUniform1f(lightLocation,if(mesh.primitive==Mesh3D.TRIANGLES)1f else 0f)
        mesh.buffer.position(0)
        GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,24,mesh.buffer)
        mesh.buffer.position(3)
        GLES30.glVertexAttribPointer(1,3,GLES30.GL_FLOAT,false,24,mesh.buffer)
        GLES30.glEnableVertexAttribArray(0);GLES30.glEnableVertexAttribArray(1)
        GLES30.glDrawArrays(mesh.primitive,0,mesh.count)
    }
    fun inspect(x:Float,y:Float,width:Int,height:Int):String? {
        val matrix=latestMvp;val input=floatArrayOf(0f,0f,0f,1f);val projected=FloatArray(4)
        var best=30.0;var result:String?=null
        for(mesh in inspectionMeshes) {
            val vertices=mesh.vertices
            for(i in vertices.indices step 6) {
                input[0]=vertices[i];input[1]=vertices[i+1];input[2]=vertices[i+2]
                Matrix.multiplyMV(projected,0,matrix,0,input,0)
                if(projected[3]<=0)continue
                val px=(projected[0]/projected[3]+1)*width/2
                val py=(1-projected[1]/projected[3])*height/2
                val delta=hypot((px-x).toDouble(),(py-y).toDouble())
                if(delta<best) {best=delta;result=String.format(java.util.Locale.US,"(%.3g, %.3g, %.3g)",input[0],input[1],input[2])}
            }
        }
        return result
    }
    private fun shader(type:Int,source:String):Int {
        val shader=GLES30.glCreateShader(type);GLES30.glShaderSource(shader,source);GLES30.glCompileShader(shader)
        val status=IntArray(1);GLES30.glGetShaderiv(shader,GLES30.GL_COMPILE_STATUS,status,0)
        check(status[0]!=0) {"Could not compile 3D graph shader: ${GLES30.glGetShaderInfoLog(shader)}"}
        return shader
    }
    private fun buffer(mesh:Mesh3D):BufferedMesh {
        val buffer=ByteBuffer.allocateDirect(mesh.vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(mesh.vertices).position(0)
        return BufferedMesh(mesh.color,buffer,mesh.vertices.size/6,mesh.primitive)
    }
    private fun line(x0:Float,y0:Float,z0:Float,x1:Float,y1:Float,z1:Float)=floatArrayOf(x0,y0,z0,0f,0f,1f,x1,y1,z1,0f,0f,1f)
}
