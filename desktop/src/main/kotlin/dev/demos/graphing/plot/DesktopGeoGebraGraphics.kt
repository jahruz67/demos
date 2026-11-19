package dev.demos.graphing.plot

import com.jogamp.opengl.*
import dev.demos.graphing.geogebra.NativeText
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.nio.ByteBuffer

internal fun createGeoGebraGraphics(): GeoGebraGraphics = DesktopGeoGebraGraphics()

private class DesktopGeoGebraGraphics : GeoGebraGraphics {
    private val drawable: GLOffscreenAutoDrawable
    override val gl: DesktopGL
    override val text: NativeText = DesktopGeoGebraText
    private var width=1
    private var height=1
    private var bytes=ByteBuffer.allocateDirect(4)
    init {
        val profile=GLProfile.get(GLProfile.GL2ES2)
        val caps=GLCapabilities(profile).apply {
            setOnscreen(false)
            setPBuffer(true)
            setFBO(false)
            doubleBuffered=false
            redBits=8;greenBits=8;blueBits=8;alphaBits=8;depthBits=24
            sampleBuffers=true;numSamples=4
        }
        drawable=GLDrawableFactory.getFactory(profile).createOffscreenAutoDrawable(null,caps,null,1,1)
        drawable.display()
        check(drawable.context.makeCurrent()!=GLContext.CONTEXT_NOT_CURRENT) { "Could not activate the native 3D context" }
        gl=DesktopGL(drawable.gl.gL2ES2)
    }
    override fun resize(width: Int, height: Int, density: Float) {
        gl.density=density
        gl.frameHeight=height
        if(this.width==width && this.height==height)return
        this.width=width;this.height=height
        drawable.context.release()
        drawable.setSurfaceSize(width,height)
        drawable.display()
        check(drawable.context.makeCurrent()!=GLContext.CONTEXT_NOT_CURRENT)
        bytes=ByteBuffer.allocateDirect(width*height*4)
    }
    override fun pixels(reverseContrast: Boolean): IntArray {
        bytes.rewind()
        drawable.gl.glReadPixels(0,0,width,height,GL.GL_RGBA,GL.GL_UNSIGNED_BYTE,bytes)
        return rgbaToGraphPixels(bytes,width,height,reverseContrast)
    }
    override fun close() {drawable.context.release();drawable.destroy()}
}

internal object DesktopGeoGebraText : NativeText {
    private val context=FontRenderContext(AffineTransform(),true,true)
    private val mathFonts=java.util.concurrent.ConcurrentHashMap<String,Font>()
    private fun font(family: String?,style: Int,size: Int): Font {
        if(family?.startsWith("retex:")==true) {
            val base=mathFonts.getOrPut(family) {
                val resource="/com/himamis/retex/renderer/desktop/"+family.removePrefix("retex:")
                javaClass.getResourceAsStream(resource).use {Font.createFont(Font.TRUETYPE_FONT,checkNotNull(it){"Missing GeoGebra font: $resource"})}
            }
            return base.deriveFont(style,size.toFloat())
        }
        return Font(if(family.equals("serif",true) || family?.contains("Times")==true) "Liberation Serif" else "Liberation Sans",style,size)
    }
    override fun measure(text: String,family: String?,style: Int,size: Int): DoubleArray {
        val font=font(family,style,size);val metrics=font.getLineMetrics(text,context);val bounds=font.createGlyphVector(context,text).visualBounds
        return doubleArrayOf(font.getStringBounds(text,context).width,bounds.x,bounds.y,bounds.width,bounds.height,metrics.ascent.toDouble(),metrics.descent.toDouble())
    }
    private fun bitmap(bytes: ByteArray,width: Int,height: Int)=BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB).apply {setRGB(0,0,width,height,graphLabelPixels(bytes),0,width)}
    private inline fun paint(rgba: ByteArray,width: Int,height: Int,matrix: DoubleArray,draw: (java.awt.Graphics2D)->Unit) {
        val image=bitmap(rgba,width,height);val graphics=image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            graphics.transform=AffineTransform(matrix);draw(graphics)
            graphLabelBytes(image.getRGB(0,0,width,height,null,0,width),rgba)
        } finally {graphics.dispose()}
    }
    override fun draw(rgba: ByteArray,width: Int,height: Int,text: String,family: String?,style: Int,size: Int,argb: Int,matrix: DoubleArray)=paint(rgba,width,height,matrix) {
        it.font=font(family,style,size);it.color=java.awt.Color(argb,true);it.drawString(text,0f,0f)
    }
    override fun path(rgba: ByteArray,width: Int,height: Int,types: IntArray,points: DoubleArray,winding: Int,fill: Boolean,
        stroke: Double,cap: Int,join: Int,miter: Double,dash: DoubleArray?,argb: Int,matrix: DoubleArray)=paint(rgba,width,height,matrix) {graphics->
        val path=java.awt.geom.Path2D.Double(winding)
        for(i in types.indices){val n=i*6;when(types[i]){0->path.moveTo(points[n],points[n+1]);1->path.lineTo(points[n],points[n+1]);2->path.quadTo(points[n],points[n+1],points[n+2],points[n+3]);3->path.curveTo(points[n],points[n+1],points[n+2],points[n+3],points[n+4],points[n+5]);4->path.closePath()}}
        graphics.color=java.awt.Color(argb,true)
        if(fill)graphics.fill(path) else {graphics.stroke=java.awt.BasicStroke(stroke.toFloat(),cap,join,miter.toFloat(),dash?.map {it.toFloat()}?.toFloatArray(),0f);graphics.draw(path)}
    }
    override fun image(rgba: ByteArray,width: Int,height: Int,source: ByteArray,sourceWidth: Int,sourceHeight: Int,matrix: DoubleArray)=paint(rgba,width,height,matrix) {it.drawImage(bitmap(source,sourceWidth,sourceHeight),0,0,null);Unit}
}
