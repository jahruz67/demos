package dev.demos.graphing.plot

import android.graphics.Paint
import android.opengl.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun PlotSurface3D(meshes: List<Mesh3D>, settings: PlotSettings, resetCounter: Int,
    cameraState: GraphCameraState, zoom: Float, onInspect: (String) -> Unit) {
    var glView by remember { mutableStateOf<GraphSurfaceView?>(null) }
    var projection3D by remember { mutableStateOf<FloatArray?>(null) }
    val density = LocalDensity.current.density
    var previousZoom by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(zoom) { glView?.zoomBy(zoom / previousZoom); previousZoom = zoom }
            AndroidView(
                factory={context -> GraphSurfaceView(context,cameraState).also {surface ->
                    glView=surface
                    surface.onInspect=onInspect
                    surface.onProjection={projection3D=it}
                }},
                modifier=Modifier.fillMaxSize(),
                update={it.update(meshes,settings,resetCounter)},
                onRelease={ if (glView === it) glView = null; it.release() },
            )
            if(settings.showAxes) Canvas(Modifier.fillMaxSize()) {
                val matrix=projection3D ?: return@Canvas
                val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.DKGRAY; textSize=13f*density }
                fun label(text:String,x:Float,y:Float,z:Float,axis:Int) {
                    val point=FloatArray(4)
                    Matrix.multiplyMV(point,0,matrix,0,floatArrayOf(x,y,z,1f),0)
                    if(point[3]<=0f) return
                    val px=(point[0]/point[3]+1f)*size.width/2f
                    val py=(1f-point[1]/point[3])*size.height/2f
                    if(px in 0f..size.width.toFloat() && py in 0f..size.height.toFloat())
                        drawContext.canvas.nativeCanvas.drawTextWithHalo(text,px+4f*density,py-4f*density,paint,density,graphAxisColors[axis])
                }
                val ranges=listOf((settings.xMin ?: -5.0) to (settings.xMax ?: 5.0),
                    (settings.yMin ?: -5.0) to (settings.yMax ?: 5.0), (settings.zMin ?: -5.0) to (settings.zMax ?: 5.0))
                if(settings.appearance.labels) {
                    label(settings.appearance.xLabel,ranges[0].second.toFloat(),0f,0f,0)
                    label(settings.appearance.yLabel,0f,ranges[1].second.toFloat(),0f,1)
                    label(settings.appearance.zLabel,0f,0f,ranges[2].second.toFloat(),2)
                }
                if(settings.appearance.numbers) ranges.forEachIndexed { axis, range ->
                    for(i in 1..4) {
                        val value=range.first+(range.second-range.first)*i/5
                        if(abs(value)>1e-9) {
                            val text=String.format(Locale.US,"%.3g",value)
                            label(text,if(axis==0)value.toFloat() else 0f,if(axis==1)value.toFloat() else 0f,if(axis==2)value.toFloat() else 0f,axis)
                        }
                    }
                }
            }

}
private fun android.graphics.Canvas.drawTextWithHalo(text:String,x:Float,y:Float,paint:Paint,density:Float,textColor:Int=android.graphics.Color.BLACK) {
    paint.style=Paint.Style.STROKE;paint.strokeWidth=3*density;paint.color=android.graphics.Color.WHITE
    drawText(text,x,y,paint)
    paint.style=Paint.Style.FILL;paint.color=textColor
    drawText(text,x,y,paint)
}
