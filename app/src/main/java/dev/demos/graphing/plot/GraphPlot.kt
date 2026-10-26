package dev.demos.graphing.plot

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import dev.demos.graphing.data.GraphImages
import dev.demos.graphing.ui.parseImageData
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale
import dev.demos.graphing.math.EvaluationContext
import kotlin.math.*

@Composable
fun GraphPlot(expressions:List<PlotExpression>,is3D:Boolean,settings:PlotSettings=PlotSettings(),modifier:Modifier=Modifier,showControls:Boolean=true) {
    val imageSources = expressions.mapNotNull { parseImageData(it.text) }
    val imageContext = LocalContext.current.applicationContext
    val imageUris = imageSources.map { it.uri }.distinct()
    val images by produceState<Map<String, android.graphics.Bitmap>>(emptyMap(), imageUris) {
        value = withContext(Dispatchers.IO) { imageUris.mapNotNull { source -> GraphImages.decode(imageContext, source)?.let { source to it } }.toMap() }
    }
    var center by rememberSaveable(stateSaver=Saver<Offset,List<Float>>(save={listOf(it.x,it.y)},restore={Offset(it[0],it[1])})) {mutableStateOf(Offset.Zero)}
    var viewWidth by rememberSaveable {mutableDoubleStateOf(20.0)}
    var viewHeightOverride by rememberSaveable {mutableStateOf<Double?>(null)}
    var appliedBounds by rememberSaveable {mutableStateOf<List<Double?>?>(null)}
    var size by remember {mutableStateOf(IntSize(1,1))}
    var trace by remember {mutableStateOf<Offset?>(null)}
    fun current2DView()=Viewport(center.x.toDouble(),center.y.toDouble(),viewWidth,viewHeightOverride?: (viewWidth*max(1,size.height)/max(1,size.width)))
    val view=current2DView()
    fun apply2DTransform(transformed:Viewport,axes:PlotZoomAxes) {
        center=Offset(transformed.centerX.toFloat(),transformed.centerY.toFloat())
        viewWidth=transformed.width
        if(viewHeightOverride!=null || axes!=PlotZoomAxes.Both) viewHeightOverride=transformed.height
    }
    var geometry by remember {mutableStateOf(Geometry2D())}
    var meshes by remember {mutableStateOf(emptyList<Mesh3D>())}
    var glView by remember {mutableStateOf<GraphSurfaceView?>(null)}
    val cameraState=rememberSaveable(saver=Saver<GraphCameraState,List<Double?>>(
        save={listOf(it.yaw.toDouble(),it.pitch.toDouble(),it.distance.toDouble(),it.panX.toDouble(),it.panY.toDouble(),it.panZ.toDouble()) + (it.configuredBounds?:emptyList())},
        restore={ values -> GraphCameraState().apply {
            yaw=values[0]!!.toFloat();pitch=values[1]!!.toFloat();distance=values[2]!!.toFloat();panX=values[3]!!.toFloat();panY=values[4]!!.toFloat();panZ=values[5]!!.toFloat()
            configuredBounds=if(values.size==12) values.drop(6) else null
        } },
    )) {GraphCameraState()}
    var inspectedPoint by remember {mutableStateOf<String?>(null)}
    var resetCounter by remember {mutableIntStateOf(0)}
    fun reset2D() {
        val xb=validBounds(settings.xMin,settings.xMax)
        val yb=validBounds(settings.yMin,settings.yMax)
        center=Offset((xb?.let {(it.first+it.second)/2}?:0.0).toFloat(),(yb?.let {(it.first+it.second)/2}?:0.0).toFloat())
        viewWidth=xb?.let {it.second-it.first}?:20.0
        viewHeightOverride=yb?.let {it.second-it.first}
    }
    LaunchedEffect(settings.xMin,settings.xMax,settings.yMin,settings.yMax) {
        val bounds=listOf(settings.xMin,settings.xMax,settings.yMin,settings.yMax)
        if(appliedBounds!=bounds) {reset2D();appliedBounds=bounds}
    }
    val program by produceState<PlotProgram?>(null,expressions,settings) {
        value=withContext(Dispatchers.Default) {PlotProgram.compile(expressions.filter { parseImageData(it.text) == null },settings)}
    }
    LaunchedEffect(program,is3D,view,size.width) {
        val compiled=program?:return@LaunchedEffect
        // Sampling mutates its context. Each pass owns a context even when a
        // cancelled pass is still winding down on a different worker thread.
        val sampling=compiled.copy(context=EvaluationContext(compiled.context.constants,compiled.context.functions,compiled.context.radians))
        if(is3D) {
            geometry=Geometry2D()
            meshes=withContext(Dispatchers.Default) {
                val context=currentCoroutineContext()
                PlotGeometry.sample3D(sampling,cancelled={!context.isActive})
            }
        } else {
            meshes=emptyList()
            geometry=withContext(Dispatchers.Default) {
                val context=currentCoroutineContext()
                PlotGeometry.sample2D(sampling,view,size.width.coerceIn(400,1800),cancelled={!context.isActive})
            }
        }
    }
    val latestGeometry by rememberUpdatedState(geometry)
    val latestView by rememberUpdatedState(view)
    val latestShowAxes by rememberUpdatedState(settings.showAxes)
    val density=LocalDensity.current.density
    val labelPaint=remember {Paint(Paint.ANTI_ALIAS_FLAG).apply {color=android.graphics.Color.BLACK}}
    Box(modifier.background(Color.White).onSizeChanged {size=it}) {
        if(is3D) {
            AndroidView(
                factory={context -> GraphSurfaceView(context,cameraState).also {surface -> glView=surface;surface.onInspect={inspectedPoint=it}}},
                modifier=Modifier.fillMaxSize(),
                update={it.update(meshes,settings,resetCounter)},
                onRelease={it.release()},
            )
            Row(Modifier.align(Alignment.BottomStart).padding(10.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                BasicText("x",style=TextStyle(color=Color(0xFFBD4942),fontSize=12.sp))
                BasicText("y",style=TextStyle(color=Color(0xFF43813F),fontSize=12.sp))
                BasicText("z",style=TextStyle(color=Color(0xFF3275B8),fontSize=12.sp))
            }
            if(showControls) BasicText("Drag to rotate · Pinch to zoom",Modifier.align(Alignment.BottomCenter).padding(8.dp),style=TextStyle(color=Color(0xFF666666),fontSize=10.sp))
            inspectedPoint?.let {BasicText(it,Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.White,RoundedCornerShape(5.dp)).border(1.dp,Color(0xFFD4D4D4),RoundedCornerShape(5.dp)).padding(8.dp),style=TextStyle(color=Color(0xFF333333),fontSize=16.sp))}
        } else {
            Canvas(Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    // Keep a single recognizer alive throughout a drag/pinch.
                    // Viewport and sampled geometry updates must not cancel it.
                    awaitEachGesture {
                        val down=awaitFirstDown(requireUnconsumed=false)
                        var pastSlop=false
                        var cancelled=false
                        var multiplePointers=false
                        var zoomAxes:PlotZoomAxes?=null
                        var accumulatedPan=Offset.Zero
                        var accumulatedZoom=1f
                        var initialCentroid=Offset.Zero
                        var previousPointerCount=0
                        var pressed=true
                        while(pressed) {
                            val event=awaitPointerEvent()
                            pressed=event.changes.any {it.pressed}
                            if(event.changes.any {it.isConsumed}) {cancelled=true;break}
                            multiplePointers=multiplePointers || event.changes.count {it.pressed || it.previousPressed}>1
                            val pointers=event.changes.filter {it.pressed && it.previousPressed}
                            if(zoomAxes==null && event.changes.count {it.pressed}>=2) {
                                zoomAxes=if(latestShowAxes) plotZoomAxes(event.changes.filter {it.pressed}.map {it.position},current2DView(),size,28*density) else PlotZoomAxes.Both
                            }
                            val axes=if(pointers.size>=2) zoomAxes?:PlotZoomAxes.Both else PlotZoomAxes.Both
                            val zoom=if(axes==PlotZoomAxes.Both) event.calculateZoom() else plotAxisZoom(pointers.map {it.previousPosition},pointers.map {it.position},axes)
                            val pan=event.calculatePan()
                            val centroid=event.calculateCentroid(useCurrent=false)
                            if(!pastSlop && pointers.size!=previousPointerCount) {
                                accumulatedPan=Offset.Zero
                                accumulatedZoom=1f
                                initialCentroid=centroid
                            }
                            previousPointerCount=pointers.size
                            accumulatedPan+=pan
                            accumulatedZoom*=zoom
                            val wasPastSlop=pastSlop
                            if(!pastSlop) {
                                val zoomMotion=abs(1-accumulatedZoom)*event.calculateCentroidSize(useCurrent=false)
                                pastSlop=zoomMotion>viewConfiguration.touchSlop || accumulatedPan.getDistance()>viewConfiguration.touchSlop
                            }
                            if(pastSlop && (zoom!=1f || pan!=Offset.Zero)) {
                                // Apply the initial movement too: crossing touch slop must
                                // not drop part of the pinch and break one-to-one scaling.
                                val transformed=if(wasPastSlop) current2DView().transformPlot(zoom,pan,centroid,size,axes)
                                    else current2DView().transformPlot(accumulatedZoom,accumulatedPan,initialCentroid,size,axes)
                                apply2DTransform(transformed,axes)
                                trace=null
                            }
                            if(pastSlop) event.changes.forEach {if(it.positionChanged()) it.consume()}
                        }
                        if(!pastSlop && !cancelled && !multiplePointers) {
                            val currentView=latestView
                            var best=Double.POSITIVE_INFINITY
                            var point:Offset?=null
                            for(line in latestGeometry.strokes) {
                                val coordinates=line.coordinates
                                for(i in coordinates.indices step 2) {
                                    val px=(coordinates[i]-currentView.left)/currentView.width*size.width
                                    val py=(currentView.top-coordinates[i+1])/currentView.height*size.height
                                    val distance=hypot(px-down.position.x,py-down.position.y)
                                    if(distance<best && distance<36*density) {best=distance;point=Offset(coordinates[i],coordinates[i+1])}
                                }
                            }
                            trace=point
                        }
                    }
                }
            ) {
                // Use this frame's canvas bounds, rather than the remembered sampling
                // size, which still contains the 1x1 placeholder on the first draw.
                val size=this.size
                // Launch, rotation and split-screen resizing can produce an empty frame.
                // Do not calculate grid spacing or label positions until it has an area.
                if(size.width<=0f || size.height<=0f) return@Canvas
                fun sx(x:Double)=((x-view.left)/view.width*size.width).toFloat()
                fun sy(y:Double)=((view.top-y)/view.height*size.height).toFloat()
                val scale=size.width/view.width
                for (image in imageSources) {
                    val bitmap = images[image.uri] ?: continue
                    val canvas = drawContext.canvas.nativeCanvas
                    val save = canvas.save()
                    canvas.rotate((-image.angle).toFloat(), sx(image.x), sy(image.y))
                    val destination = android.graphics.RectF(sx(image.x - image.width / 2), sy(image.y + image.height / 2), sx(image.x + image.width / 2), sy(image.y - image.height / 2))
                    val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = (image.opacity.coerceIn(0.0, 1.0) * 255).toInt() }
                    canvas.drawBitmap(bitmap, null, destination, imagePaint)
                    canvas.restoreToCount(save)
                }
                val major=niceStep(90*density/scale)
                val minor=major/5
                val majorY=niceStep(90*density/(size.height/view.height))
                fun grid(stepX:Double,stepY:Double,color:Color,width:Float) {
                    var x=ceil(view.left/stepX)*stepX
                    var guard=0
                    while(x<=view.right && guard++<1000) {drawLine(color,Offset(sx(x),0f),Offset(sx(x),size.height.toFloat()),width);x+=stepX}
                    var y=ceil(view.bottom/stepY)*stepY
                    guard=0
                    while(y<=view.top && guard++<1000) {drawLine(color,Offset(0f,sy(y)),Offset(size.width.toFloat(),sy(y)),width);y+=stepY}
                }
                if(settings.showGrid) {grid(minor,majorY/5,Color(0xFFDDDDDD),0.75f*density);grid(major,majorY,Color(0xFFAAAAAA),1f*density)}
                for(fill in geometry.fills) {
                    val path=Path()
                    for(i in fill.coordinates.indices step 2) {val p=Offset(sx(fill.coordinates[i].toDouble()),sy(fill.coordinates[i+1].toDouble()));if(i==0)path.moveTo(p.x,p.y) else path.lineTo(p.x,p.y)}
                    path.close();drawPath(path,Color(fill.color).copy(alpha=0.14f))
                }
                if(settings.showAxes) {
                    if(0.0 in view.left..view.right) drawLine(Color(0xFF222222),Offset(sx(0.0),0f),Offset(sx(0.0),size.height.toFloat()),1.5f*density)
                    if(0.0 in view.bottom..view.top) drawLine(Color(0xFF222222),Offset(0f,sy(0.0)),Offset(size.width.toFloat(),sy(0.0)),1.5f*density)
                    labelPaint.textSize=13*density
                    val baseline=sy(0.0).coerceIn(2*density,max(2*density,size.height-20*density))+16*density
                    var x=ceil(view.left/major)*major
                    var guard=0
                    while(x<=view.right && guard++<100) {
                        if(abs(x)>major*1e-5) {
                            val text=number(x);val textWidth=labelPaint.measureText(text)
                            val px=(sx(x)-textWidth/2).coerceIn(2*density,max(2*density,size.width-textWidth-2*density))
                            drawContext.canvas.nativeCanvas.apply {drawTextWithHalo(text,px,baseline,labelPaint,density)}
                        };x+=major
                    }
                    var y=ceil(view.bottom/majorY)*majorY
                    val xPosition=sx(0.0).coerceIn(35*density,max(35*density,size.width-2*density))-5*density
                    guard=0
                    while(y<=view.top && guard++<100) {
                        if(abs(y)>majorY*1e-5) {
                            val text=number(y)
                            drawContext.canvas.nativeCanvas.apply {drawTextWithHalo(text,xPosition-labelPaint.measureText(text),sy(y)+4*density,labelPaint,density)}
                        };y+=majorY
                    }
                    if(0.0 in view.left..view.right && 0.0 in view.bottom..view.top) drawContext.canvas.nativeCanvas.drawTextWithHalo("0",sx(0.0)-13*density,baseline,labelPaint,density)
                }
                for(line in geometry.strokes) {
                    val a=line.coordinates
                    if(line.points) {for(i in a.indices step 2)drawCircle(Color(line.color),4.5f*density,Offset(sx(a[i].toDouble()),sy(a[i+1].toDouble())))}
                    else {
                        val path=Path()
                        for(i in a.indices step 2) {val x=sx(a[i].toDouble());val y=sy(a[i+1].toDouble());if(i==0)path.moveTo(x,y) else path.lineTo(x,y)}
                        drawPath(path,Color(line.color),style=Stroke(width=2.6f*density))
                    }
                }
                trace?.let {p ->
                    val x=sx(p.x.toDouble());val y=sy(p.y.toDouble())
                    drawCircle(Color.Black,4.5f*density,Offset(x,y))
                    val text="(${number(p.x.toDouble())}, ${number(p.y.toDouble())})"
                    labelPaint.textSize=17*density
                    val width=labelPaint.measureText(text)+18*density
                    val left=(x-width-8*density).coerceIn(4*density,max(4*density,size.width-width-4*density))
                    val top=(y-42*density).coerceIn(4*density,max(4*density,size.height-38*density))
                    drawRoundRect(Color(0x22000000),Offset(left-2*density,top-2*density),androidx.compose.ui.geometry.Size(width+4*density,37*density),androidx.compose.ui.geometry.CornerRadius(5*density))
                    drawRoundRect(Color.White,Offset(left,top),androidx.compose.ui.geometry.Size(width,33*density),androidx.compose.ui.geometry.CornerRadius(4*density))
                    drawContext.canvas.nativeCanvas.drawText(text,left+9*density,top+23*density,labelPaint)
                }
            }
        }
        if(showControls) Column(Modifier.align(Alignment.TopEnd).padding(top=60.dp,end=6.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            if(is3D || center!=Offset.Zero || abs(viewWidth-20.0)>1e-9 || viewHeightOverride!=null) {
                PlotButton("⌂","Reset graph view") {reset2D();trace=null;inspectedPoint=null;resetCounter++}
            }
            if(is3D) {
                PlotButton("+","Zoom in") {glView?.zoomBy(1.3f)}
                PlotButton("−","Zoom out") {glView?.zoomBy(1/1.3f)}
            } else {
                PlotButton("+","Zoom in") {apply2DTransform(current2DView().transformPlot(1.3f,Offset.Zero,Offset(size.width/2f,size.height/2f),size,PlotZoomAxes.Both),PlotZoomAxes.Both);trace=null}
                PlotButton("−","Zoom out") {apply2DTransform(current2DView().transformPlot(1/1.3f,Offset.Zero,Offset(size.width/2f,size.height/2f),size,PlotZoomAxes.Both),PlotZoomAxes.Both);trace=null}
            }
        }
    }
}
@Composable private fun PlotButton(label:String,description:String,onClick:()->Unit) {
    Box(Modifier.size(48.dp).background(Color(0xFFF0F0F0),RoundedCornerShape(5.dp)).border(1.dp,Color(0xFFD4D4D4),RoundedCornerShape(5.dp)).semantics {contentDescription=description}.clickable(onClick=onClick),contentAlignment=Alignment.Center) {
        BasicText(label,style=TextStyle(color=Color(0xFF626262),fontSize=25.sp))
    }
}
private fun niceStep(target:Double):Double {
    val magnitude=10.0.pow(floor(log10(target)))
    val normalized=target/magnitude
    return magnitude*when {normalized<1.4 -> 1.0;normalized<3.2 -> 2.0;normalized<7 -> 5.0;else -> 10.0}
}
private fun validBounds(low:Double?,high:Double?):Pair<Double,Double>? =
    if(low!=null && high!=null && low.isFinite() && high.isFinite() && low<high) low to high else null
private fun number(v:Double):String {
    if(abs(v)<1e-10) return "0"
    return if(abs(v)>=1e7 || abs(v)<1e-4) String.format(Locale.US,"%.3g",v)
    else if(abs(v-round(v))<1e-7) String.format(Locale.US,"%.0f",v)
    else String.format(Locale.US,"%.4f",v).trimEnd('0').trimEnd('.')
}
private fun android.graphics.Canvas.drawTextWithHalo(text:String,x:Float,y:Float,paint:Paint,density:Float) {
    paint.style=Paint.Style.STROKE;paint.strokeWidth=3*density;paint.color=android.graphics.Color.WHITE
    drawText(text,x,y,paint)
    paint.style=Paint.Style.FILL;paint.color=android.graphics.Color.BLACK
    drawText(text,x,y,paint)
}
