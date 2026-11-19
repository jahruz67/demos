package dev.demos.graphing.plot

import dev.demos.graphing.geogebra.NativeGL
import dev.demos.graphing.geogebra.NativeText

/** Offscreen native GL surface. Compose presents its framebuffer and draws the native controls. */
internal interface GeoGebraGraphics : AutoCloseable {
    val gl: NativeGL
    val text: NativeText
    fun resize(width: Int, height: Int, density: Float)
    fun pixels(reverseContrast: Boolean): IntArray
}

internal fun rgbaToGraphPixels(bytes: java.nio.ByteBuffer, width: Int, height: Int, reverse: Boolean): IntArray {
    val pixels=IntArray(width*height)
    for(y in 0 until height) for(x in 0 until width) {
        val i=((height-y-1)*width+x)*4
        val r=bytes.get(i).toInt() and 255
        val g=bytes.get(i+1).toInt() and 255
        val b=bytes.get(i+2).toInt() and 255
        val color=(0xff shl 24) or (r shl 16) or (g shl 8) or b
        pixels[y*width+x]=if(reverse) reversedGraphColor(color) else color
    }
    return pixels
}

internal data class ReferenceGeoGebraLayout(val width: Int,val height: Int,val scale: Float)

/** deployggb.js getWidthHeight/calcScale, including its right-hand scale margin. */
internal fun referenceGeoGebraLayout(size: androidx.compose.ui.unit.IntSize,density: Float,left: Float,
    windowWidth: Int,windowHeight: Int): ReferenceGeoGebraLayout {
    val width=maxOf(320,kotlin.math.round(size.width/density).toInt())
    val height=maxOf(300,kotlin.math.round(size.height/density).toInt())
    val border=if(left>0 && left<=windowWidth)left+(if(windowWidth<=480)10 else 30) else 0f
    val availableWidth=if(width+border<windowWidth)width.toFloat() else windowWidth-border
    val scale=minOf(1f,availableWidth/width,windowHeight.toFloat()/height).coerceAtLeast(.001f)
    return ReferenceGeoGebraLayout(width-2,height-2,scale)
}

internal fun graphLabelPixels(rgba: ByteArray)=IntArray(rgba.size/4) {i->
    ((rgba[4*i+3].toInt() and 255) shl 24) or ((rgba[4*i].toInt() and 255) shl 16) or
        ((rgba[4*i+1].toInt() and 255) shl 8) or (rgba[4*i+2].toInt() and 255)
}
internal fun graphLabelBytes(pixels: IntArray,rgba: ByteArray) {
    for(i in pixels.indices){rgba[4*i]=(pixels[i] ushr 16).toByte();rgba[4*i+1]=(pixels[i] ushr 8).toByte();rgba[4*i+2]=pixels[i].toByte();rgba[4*i+3]=(pixels[i] ushr 24).toByte()}
}
