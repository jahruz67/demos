package dev.demos.graphing.plot

import android.graphics.*
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLSurface
import android.opengl.GLES20
import dev.demos.graphing.geogebra.NativeText
import java.nio.ByteBuffer

internal fun createGeoGebraGraphics(): GeoGebraGraphics = AndroidGeoGebraGraphics()

private class AndroidGeoGebraGraphics : GeoGebraGraphics {
    private val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private val config: EGLConfig
    private val context: android.opengl.EGLContext
    private var surface: EGLSurface=EGL14.EGL_NO_SURFACE
    override val gl=AndroidGL()
    override val text: NativeText=AndroidGeoGebraText
    private var width=0
    private var height=0
    private var bytes=ByteBuffer.allocateDirect(4)
    init {
        check(EGL14.eglInitialize(display,IntArray(2),0,IntArray(2),1)) { "Could not initialize native OpenGL ES" }
        fun choose(samples: Int): EGLConfig? {
            val attributes=intArrayOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
                EGL14.EGL_DEPTH_SIZE,24,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_SAMPLE_BUFFERS,if(samples>0)1 else 0,EGL14.EGL_SAMPLES,samples,EGL14.EGL_NONE)
            val configs=arrayOfNulls<EGLConfig>(1);val count=IntArray(1)
            return if(EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0) && count[0]>0) configs[0] else null
        }
        config=checkNotNull(choose(4)?:choose(0)) { "No compatible native 3D framebuffer" }
        context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
        check(context!=EGL14.EGL_NO_CONTEXT) { "Could not create the native OpenGL ES context" }
        resize(1,1,1f)
    }
    override fun resize(width: Int,height: Int,density: Float) {
        gl.density=density
        gl.frameHeight=height
        if(this.width==width && this.height==height)return
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
        if(surface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,surface)
        surface=EGL14.eglCreatePbufferSurface(display,config,intArrayOf(EGL14.EGL_WIDTH,width,EGL14.EGL_HEIGHT,height,EGL14.EGL_NONE),0)
        check(surface!=EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(display,surface,surface,context)) { "Could not size the native 3D framebuffer" }
        this.width=width;this.height=height;bytes=ByteBuffer.allocateDirect(width*height*4)
    }
    override fun pixels(reverseContrast: Boolean): IntArray {
        bytes.rewind();GLES20.glReadPixels(0,0,width,height,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,bytes)
        return rgbaToGraphPixels(bytes,width,height,reverseContrast)
    }
    override fun close() {
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display)
    }
}

private object AndroidGeoGebraText : NativeText {
    private val fonts=java.util.concurrent.ConcurrentHashMap<String,Typeface>()
    private fun typeface(family: String?,style: Int): Typeface {
        if(family?.startsWith("retex:")==true) {
            val base=fonts.getOrPut(family) {
                val resource="/com/himamis/retex/renderer/desktop/"+family.removePrefix("retex:")
                val file=java.io.File.createTempFile("ggb-font-",".ttf")
                try {javaClass.getResourceAsStream(resource).use {input->file.outputStream().use {checkNotNull(input){"Missing GeoGebra font: $resource"}.copyTo(it)}};Typeface.createFromFile(file)} finally {file.delete()}
            }
            return Typeface.create(base,style and 3)
        }
        return Typeface.create(if(family.equals("serif",true))"serif" else "sans-serif",style and 3)
    }
    private fun paint(family: String?,style: Int,size: Int)=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {textSize=size.toFloat();typeface=typeface(family,style)}
    override fun measure(text: String,family: String?,style: Int,size: Int): DoubleArray {
        val paint=paint(family,style,size);val bounds=Rect();paint.getTextBounds(text,0,text.length,bounds);val metrics=paint.fontMetrics
        return doubleArrayOf(paint.measureText(text).toDouble(),bounds.left.toDouble(),bounds.top.toDouble(),bounds.width().toDouble(),bounds.height().toDouble(),-metrics.ascent.toDouble(),metrics.descent.toDouble())
    }
    private fun bitmap(bytes: ByteArray,width: Int,height: Int)=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).apply {setPixels(graphLabelPixels(bytes),0,width,0,0,width,height)}
    private inline fun paint(rgba: ByteArray,width: Int,height: Int,matrix: DoubleArray,draw: (Canvas)->Unit) {
        val bitmap=bitmap(rgba,width,height)
        try {
            val transform=Matrix().apply {setValues(floatArrayOf(matrix[0].toFloat(),matrix[2].toFloat(),matrix[4].toFloat(),matrix[1].toFloat(),matrix[3].toFloat(),matrix[5].toFloat(),0f,0f,1f))}
            val canvas=Canvas(bitmap);canvas.concat(transform);draw(canvas)
            val pixels=IntArray(width*height);bitmap.getPixels(pixels,0,width,0,0,width,height);graphLabelBytes(pixels,rgba)
        } finally {bitmap.recycle()}
    }
    override fun draw(rgba: ByteArray,width: Int,height: Int,text: String,family: String?,style: Int,size: Int,argb: Int,matrix: DoubleArray)=paint(rgba,width,height,matrix) {
        it.drawText(text,0f,0f,paint(family,style,size).apply {color=argb})
    }
    override fun path(rgba: ByteArray,width: Int,height: Int,types: IntArray,points: DoubleArray,winding: Int,fill: Boolean,
        stroke: Double,cap: Int,join: Int,miter: Double,dash: DoubleArray?,argb: Int,matrix: DoubleArray)=paint(rgba,width,height,matrix) {canvas->
        val path=Path().apply {fillType=if(winding==0)Path.FillType.EVEN_ODD else Path.FillType.WINDING}
        for(i in types.indices){val n=i*6;when(types[i]){0->path.moveTo(points[n].toFloat(),points[n+1].toFloat());1->path.lineTo(points[n].toFloat(),points[n+1].toFloat());2->path.quadTo(points[n].toFloat(),points[n+1].toFloat(),points[n+2].toFloat(),points[n+3].toFloat());3->path.cubicTo(points[n].toFloat(),points[n+1].toFloat(),points[n+2].toFloat(),points[n+3].toFloat(),points[n+4].toFloat(),points[n+5].toFloat());4->path.close()}}
        canvas.drawPath(path,Paint(Paint.ANTI_ALIAS_FLAG).apply {color=argb;style=if(fill)Paint.Style.FILL else Paint.Style.STROKE;strokeWidth=stroke.toFloat();strokeCap=Paint.Cap.entries[cap];strokeJoin=Paint.Join.entries[join];strokeMiter=miter.toFloat();if(dash!=null)pathEffect=DashPathEffect(dash.map {it.toFloat()}.toFloatArray(),0f)})
    }
    override fun image(rgba: ByteArray,width: Int,height: Int,source: ByteArray,sourceWidth: Int,sourceHeight: Int,matrix: DoubleArray)=paint(rgba,width,height,matrix) {canvas->
        val bitmap=bitmap(source,sourceWidth,sourceHeight);try {canvas.drawBitmap(bitmap,0f,0f,Paint(Paint.ANTI_ALIAS_FLAG))} finally {bitmap.recycle()}
    }
}
