package dev.demos.graphing.plot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import dev.demos.graphing.ui.rememberImageLoader
import dev.demos.graphing.ui.parseImageData
import dev.demos.graphing.ui.DemosColors
import dev.demos.graphing.ui.GraphControl
import dev.demos.graphing.ui.GraphBounds
import dev.demos.graphing.ui.interfaceTextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale
import dev.demos.graphing.math.EvaluationContext
import kotlin.math.*

@Composable
fun GraphPlot(expressions:List<PlotExpression>,is3D:Boolean,settings:PlotSettings=PlotSettings(),modifier:Modifier=Modifier,showControls:Boolean=true,
    renderState:GraphRenderState=remember {GraphRenderState()},interactive:Boolean=true,
    on3DSettingsChange:(Viewport3DSettings)->Unit={},camera3D:GeoGebraCamera?=null,show3DSettingsButton:Boolean=true) {
    val views = rememberSaveableStateHolder()
    var hasMounted3D by remember { mutableStateOf(is3D) }
    if (is3D) hasMounted3D = true
    val dormantRendering = remember { GraphRenderState() }
    Box(modifier) {
        if (hasMounted3D) key(camera3D) { views.SaveableStateProvider("3d") {
            GeoGebraViewport3D(expressions, settings,
                Modifier.fillMaxSize().graphicsLayer { alpha = if (is3D) 1f else 0f },
                showControls && is3D, if (is3D) renderState else dormantRendering,
                interactive && is3D, on3DSettingsChange, active = is3D, cameraState = camera3D,showSettingsButton=show3DSettingsButton)
        }
        }
        if (!is3D) views.SaveableStateProvider("2d") {
            NativePlot2D(expressions,settings,Modifier.fillMaxSize(),showControls,renderState,interactive)
        }
    }
}

@Composable
private fun NativePlot2D(expressions:List<PlotExpression>,settings:PlotSettings,modifier:Modifier,
    showControls:Boolean,renderState:GraphRenderState,interactive:Boolean) {
    val imageSources = expressions.mapNotNull { parseImageData(it.text) }
    val loadImage = rememberImageLoader()
    val imageUris = imageSources.map { it.uri }.distinct()
    val loadedImages by produceState<PlotImages?>(null, imageUris, loadImage) {
        value = withContext(Dispatchers.IO) { PlotImages(imageUris,imageUris.mapNotNull { source -> loadImage(source)?.let { source to it } }.toMap()) }
    }
    val images=loadedImages?.bitmaps.orEmpty()
    var center by rememberSaveable(stateSaver=Saver<Offset,List<Float>>(save={listOf(it.x,it.y)},restore={Offset(it[0],it[1])})) {mutableStateOf(Offset.Zero)}
    var viewWidth by rememberSaveable {mutableDoubleStateOf(20.0)}
    var viewHeightOverride by rememberSaveable {mutableStateOf<Double?>(null)}
    var appliedBounds by rememberSaveable {mutableStateOf<List<Double?>?>(null)}
    var appliedResetCounter by rememberSaveable {mutableIntStateOf(renderState.boundsResetCounter)}
    var size by remember {mutableStateOf(IntSize(1,1))}
    val previousBounds = remember { arrayOfNulls<Rect>(1) }
    var trace by remember {mutableStateOf<Offset?>(null)}
    var zoom2DTarget by remember {mutableStateOf<Viewport?>(null)}
    fun current2DView()=Viewport(center.x.toDouble(),center.y.toDouble(),viewWidth,viewHeightOverride?: (viewWidth*max(1,size.height)/max(1,size.width)))
    val view=current2DView()
    fun apply2DTransform(transformed:Viewport,axes:PlotZoomAxes) {
        center=Offset(transformed.centerX.toFloat(),transformed.centerY.toFloat())
        viewWidth=transformed.width
        if(viewHeightOverride!=null || axes!=PlotZoomAxes.Both) viewHeightOverride=transformed.height
    }
    fun zoom2D(factor:Float,anchor:Offset) {
        zoom2DTarget=(zoom2DTarget?:current2DView()).transformPlot(factor,Offset.Zero,anchor,size,PlotZoomAxes.Both)
        trace=null
    }
    LaunchedEffect(zoom2DTarget) {
        val end=zoom2DTarget?:return@LaunchedEffect
        val start=current2DView()
        animatePlotZoom { fraction ->
            apply2DTransform(if(fraction==1f) end else Viewport(
                start.centerX+(end.centerX-start.centerX)*fraction,
                start.centerY+(end.centerY-start.centerY)*fraction,
                start.width+(end.width-start.width)*fraction,
                start.height+(end.height-start.height)*fraction,
            ),PlotZoomAxes.Both)
        }
        zoom2DTarget=null
    }
    var geometry by remember {mutableStateOf(Geometry2D())}
    var sampledInput by remember {mutableStateOf<PlotSamplingInput?>(null)}
    fun reset2D() {
        zoom2DTarget=null
        trace=null
        fun scaledBounds(low:Double?,high:Double?,log:Boolean):Pair<Double,Double>? {
            val bounds=validBounds(low,high)
            if(!log) return bounds
            return if(bounds!=null && bounds.first>0.0) log10(bounds.first) to log10(bounds.second) else -1.0 to 3.0
        }
        val xb=scaledBounds(settings.xMin,settings.xMax,settings.appearance.logX)
        val yb=scaledBounds(settings.yMin,settings.yMax,settings.appearance.logY)
        center=Offset((xb?.let {(it.first+it.second)/2}?:0.0).toFloat(),(yb?.let {(it.first+it.second)/2}?:0.0).toFloat())
        viewWidth=xb?.let {it.second-it.first}?:20.0
        viewHeightOverride=yb?.let {it.second-it.first}
    }
    val configuredBounds2D=listOf(settings.xMin,settings.xMax,settings.yMin,settings.yMax,if(settings.appearance.logX) 1.0 else 0.0,if(settings.appearance.logY) 1.0 else 0.0)
    val boundsResetCounter=renderState.boundsResetCounter
    LaunchedEffect(configuredBounds2D,boundsResetCounter) {
        if(appliedBounds!=configuredBounds2D || appliedResetCounter!=boundsResetCounter) {
            reset2D();appliedBounds=configuredBounds2D;appliedResetCounter=boundsResetCounter
        }
    }
    val plotInput=PlotInput(expressions,settings)
    val compiledPlot by produceState<CompiledPlot?>(null,plotInput) {
        value=withContext(Dispatchers.Default) {CompiledPlot(plotInput,PlotProgram.compile(expressions.filter { parseImageData(it.text) == null },settings))}
    }
    val samplingInput=compiledPlot?.let {PlotSamplingInput(it,view,size.width.coerceIn(400,1800))}
    LaunchedEffect(samplingInput) {
        val input=samplingInput?:return@LaunchedEffect
        val compiled=input.compiled.program
        // Sampling mutates its context. Each pass owns a context even when a
        // cancelled pass is still winding down on a different worker thread.
        val sampling=compiled.copy(context=EvaluationContext(compiled.context.constants,compiled.context.functions,compiled.context.radians))
        geometry=withContext(Dispatchers.Default) {
            val context=currentCoroutineContext()
            PlotGeometry.sample2D(sampling,input.view,input.width,cancelled={!context.isActive})
        }
        sampledInput=input
    }
    val ready=compiledPlot?.input==plotInput && samplingInput!=null && sampledInput==samplingInput &&
        loadedImages?.uris==imageUris && zoom2DTarget==null && size.width>1 && size.height>1
    SideEffect {
        renderState.ready=ready
        if(size.width>1 && size.height>1 && appliedBounds==configuredBounds2D && appliedResetCounter==boundsResetCounter) {
            renderState.visibleBounds2D=GraphBounds(
                xMin=axisValue(view.left,settings.appearance.logX),xMax=axisValue(view.right,settings.appearance.logX),
                yMin=axisValue(view.bottom,settings.appearance.logY),yMax=axisValue(view.top,settings.appearance.logY))
        }
    }
    DisposableEffect(renderState) {onDispose {renderState.invalidate()}}
    val latestGeometry by rememberUpdatedState(geometry)
    val latestView by rememberUpdatedState(view)
    val latestShowAxes by rememberUpdatedState(settings.showAxes)
    val latestAppearance by rememberUpdatedState(settings.appearance)
    val plotDensity=LocalDensity.current
    val density=plotDensity.density
    val measurer=rememberTextMeasurer()
    val labelPaint=remember(measurer,plotDensity) {PlotLabels(measurer,plotDensity)}
    Box(modifier.background(Color.White).clipToBounds().onGloballyPositioned { coordinates ->
        val nextSize = coordinates.size
        if (nextSize.width > 0 && nextSize.height > 0) {
            val origin = coordinates.positionInRoot()
            val next = Rect(origin.x, origin.y, origin.x + nextSize.width, origin.y + nextSize.height)
            val previous = previousBounds[0]
            if (previous != null && previous != next) {
                zoom2DTarget=null
                val oldView = current2DView()
                val unitsX = oldView.width / previous.width
                val unitsY = oldView.height / previous.height
                center = Offset(
                    (oldView.centerX + (next.center.x - previous.center.x) * unitsX).toFloat(),
                    (oldView.centerY - (next.center.y - previous.center.y) * unitsY).toFloat(),
                )
                viewWidth = unitsX * next.width
                if (viewHeightOverride != null) viewHeightOverride = unitsY * next.height
            }
            previousBounds[0] = next
            size = nextSize
        }
    }) {
            Canvas(Modifier.fillMaxSize()
                .pointerInput(interactive) {
                    if(!interactive) return@pointerInput
                    awaitPointerEventScope {
                        while(true) {
                            val event=awaitPointerEvent()
                            if(event.type==androidx.compose.ui.input.pointer.PointerEventType.Scroll) {
                                val change=event.changes.firstOrNull() ?: continue
                                val delta=event.changes.sumOf {it.scrollDelta.y.toDouble()}.toFloat()
                                if(delta!=0f) {
                                    zoom2D(plotScrollZoom(delta),change.position)
                                    event.changes.forEach {it.consume()}
                                }
                            }
                        }
                    }
                }
                .pointerInput(interactive) {
                    if(!interactive) return@pointerInput
                    // Keep a single recognizer alive throughout a drag/pinch.
                    // Viewport and sampled geometry updates must not cancel it.
                    awaitEachGesture {
                        val down=awaitFirstDown(requireUnconsumed=false)
                        zoom2DTarget=null
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
                                val current=current2DView()
                                zoomAxes=if(latestShowAxes) plotZoomAxes(event.changes.filter {it.pressed}.map {it.position},current,size,28*density,
                                    horizontal=if(latestAppearance.logY) current.bottom else 0.0,
                                    vertical=if(latestAppearance.logX) current.left else 0.0) else PlotZoomAxes.Both
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
                            fun screenPoint(point:Offset)=Offset(
                                ((point.x-currentView.left)/currentView.width*size.width).toFloat(),
                                ((currentView.top-point.y)/currentView.height*size.height).toFloat(),
                            )
                            val intersection=latestGeometry.interests
                                .filter {it.kind==InterestKind.INTERSECTION}
                                .map {Offset(it.x.toFloat(),it.y.toFloat())}
                                .minByOrNull {screenPoint(it).minus(down.position).getDistance()}
                                ?.takeIf {screenPoint(it).minus(down.position).getDistance()<=48f*density}
                            if(intersection!=null) {
                                trace=intersection
                            } else {
                                var best=42f*density
                                var point:Offset?=null
                                latestGeometry.interests
                                    .filter {it.kind!=InterestKind.INTERSECTION}
                                    .map {Offset(it.x.toFloat(),it.y.toFloat())}
                                    .forEach {
                                        val distance=screenPoint(it).minus(down.position).getDistance()
                                        if(distance<best) {best=distance;point=it}
                                    }
                                latestGeometry.strokes.forEach {stroke ->
                                    val coordinates=stroke.coordinates
                                    for(i in 0 until coordinates.size-2 step 2) {
                                        val a=screenPoint(Offset(coordinates[i].toDouble().toFloat(),coordinates[i+1].toDouble().toFloat()))
                                        val b=screenPoint(Offset(coordinates[i+2].toDouble().toFloat(),coordinates[i+3].toDouble().toFloat()))
                                        val delta=b-a
                                        val lengthSquared=delta.x*delta.x+delta.y*delta.y
                                        val amount=if(lengthSquared>0f) {
                                            ((down.position-a).x*delta.x+(down.position-a).y*delta.y)/lengthSquared
                                        } else 0f
                                        val candidate=a+delta*amount.coerceIn(0f,1f)
                                        val distance=candidate.minus(down.position).getDistance()
                                        if(distance<best) {
                                            best=distance
                                            point=Offset(
                                                (currentView.left+candidate.x/size.width*currentView.width).toFloat(),
                                                (currentView.top-candidate.y/size.height*currentView.height).toFloat(),
                                            )
                                        }
                                    }
                                }
                                trace=point
                            }
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
                    fun imageX(x:Double)=sx(axisCoordinate(x,settings.appearance.logX))
                    fun imageY(y:Double)=sy(axisCoordinate(y,settings.appearance.logY))
                    val left=imageX(image.x-image.width/2)
                    val top=imageY(image.y+image.height/2)
                    val right=imageX(image.x+image.width/2)
                    val bottom=imageY(image.y-image.height/2)
                    if(!listOf(left,top,right,bottom).all {it.isFinite()} || right<=left || bottom<=top) continue
                    rotate((-image.angle).toFloat(),Offset(imageX(image.x),imageY(image.y))) {
                        translate(left,top) {
                            scale((right-left)/bitmap.width,(bottom-top)/bitmap.height,pivot=Offset.Zero) {
                                drawImage(bitmap,alpha=image.opacity.coerceIn(0.0,1.0).toFloat())
                            }
                        }
                    }
                }
                val logX=settings.appearance.logX;val logY=settings.appearance.logY
                val major=if(logX) max(1.0,ceil(90*density/scale)) else niceStep(90*density/scale)
                val minor=major/5
                val majorY=if(logY) max(1.0,ceil(90*density/(size.height/view.height))) else niceStep(90*density/(size.height/view.height))
                fun grid(stepX:Double,stepY:Double,color:Color,width:Float,minorGrid:Boolean=false) {
                    var x=ceil(view.left/stepX)*stepX
                    var guard=0
                    if(logX && minorGrid) {
                        var decade=floor(view.left)
                        while(decade<=view.right && guard++<1000) {
                            for(m in 2..9) {val p=decade+log10(m.toDouble());if(p in view.left..view.right) drawLine(color,Offset(sx(p),0f),Offset(sx(p),size.height),width)}
                            decade++
                        }
                    } else while(x<=view.right && guard++<1000) {drawLine(color,Offset(sx(x),0f),Offset(sx(x),size.height.toFloat()),width);x+=stepX}
                    var y=ceil(view.bottom/stepY)*stepY
                    guard=0
                    if(logY && minorGrid) {
                        var decade=floor(view.bottom)
                        while(decade<=view.top && guard++<1000) {
                            for(m in 2..9) {val p=decade+log10(m.toDouble());if(p in view.bottom..view.top) drawLine(color,Offset(0f,sy(p)),Offset(size.width,sy(p)),width)}
                            decade++
                        }
                    } else while(y<=view.top && guard++<1000) {drawLine(color,Offset(0f,sy(y)),Offset(size.width.toFloat(),sy(y)),width);y+=stepY}
                }
                if(settings.showGrid) {grid(minor,majorY/5,Color(0xFFDDDDDD),0.75f*density,true);grid(major,majorY,Color(0xFFAAAAAA),1f*density)}
                for(fill in geometry.fills) {
                    val path=Path()
                    for(i in fill.coordinates.indices step 2) {val p=Offset(sx(fill.coordinates[i].toDouble()),sy(fill.coordinates[i+1].toDouble()));if(i==0)path.moveTo(p.x,p.y) else path.lineTo(p.x,p.y)}
                    path.close();drawPath(path,Color(fill.color).copy(alpha=0.14f))
                }
                if(settings.showAxes) {
                    val axisX=if(logX) view.left else 0.0
                    val axisY=if(logY) view.bottom else 0.0
                    if(axisX in view.left..view.right) drawLine(Color(0xFF222222),Offset(sx(axisX),0f),Offset(sx(axisX),size.height.toFloat()),1.5f*density)
                    if(axisY in view.bottom..view.top) drawLine(Color(0xFF222222),Offset(0f,sy(axisY)),Offset(size.width.toFloat(),sy(axisY)),1.5f*density)
                    labelPaint.textSize=13*density
                    val baseline=sy(axisY).coerceIn(2*density,max(2*density,size.height-20*density))+16*density
                    if(settings.appearance.numbers) {
                    var x=ceil(view.left/major)*major
                    var guard=0
                    while(x<=view.right && guard++<100) {
                        if(logX || abs(x)>major*1e-5) {
                            val text=number(axisValue(x,logX));val textWidth=labelPaint.measureText(text)
                            val px=(sx(x)-textWidth/2).coerceIn(2*density,max(2*density,size.width-textWidth-2*density))
                            drawTextWithHalo(text,px,baseline,labelPaint,density)
                        };x+=major
                    }
                    var y=ceil(view.bottom/majorY)*majorY
                    val xPosition=sx(axisX).coerceIn(35*density,max(35*density,size.width-2*density))-5*density
                    guard=0
                    while(y<=view.top && guard++<100) {
                        if(logY || abs(y)>majorY*1e-5) {
                            val text=number(axisValue(y,logY))
                            drawTextWithHalo(text,xPosition-labelPaint.measureText(text),sy(y)+4*density,labelPaint,density)
                        };y+=majorY
                    }
                    if(!logX && !logY && 0.0 in view.left..view.right && 0.0 in view.bottom..view.top) drawTextWithHalo("0",sx(0.0)-13*density,baseline,labelPaint,density)
                    }
                    if(settings.appearance.labels) {
                        val xLabel=settings.appearance.xLabel;val yLabel=settings.appearance.yLabel
                        drawTextWithHalo(xLabel,max(4*density,size.width-labelPaint.measureText(xLabel)-8*density),max(16*density,sy(axisY)-8*density),labelPaint,density)
                        drawTextWithHalo(yLabel,sx(axisX).coerceIn(4*density,max(4*density,size.width-labelPaint.measureText(yLabel)-4*density)),16*density,labelPaint,density)
                    }
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
                    val text="(${number(axisValue(p.x.toDouble(),logX))}, ${number(axisValue(p.y.toDouble(),logY))})"
                    labelPaint.textSize=17*density
                    val width=labelPaint.measureText(text)+18*density
                    val left=(x-width-8*density).coerceIn(4*density,max(4*density,size.width-width-4*density))
                    val top=(y-42*density).coerceIn(4*density,max(4*density,size.height-38*density))
                    drawRoundRect(Color(0x22000000),Offset(left-2*density,top-2*density),androidx.compose.ui.geometry.Size(width+4*density,37*density),androidx.compose.ui.geometry.CornerRadius(5*density))
                    drawRoundRect(Color.White,Offset(left,top),androidx.compose.ui.geometry.Size(width,33*density),androidx.compose.ui.geometry.CornerRadius(4*density))
                    drawTextWithHalo(text,left+9*density,top+23*density,labelPaint,density)
                }
            }
        if(showControls) Column(Modifier.align(Alignment.TopEnd).padding(top=60.dp,end=6.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            if(center!=Offset.Zero || abs(viewWidth-20.0)>1e-9 || viewHeightOverride!=null) {
                PlotButton("⌂","Reset graph view") {reset2D();trace=null}
            }
            PlotButton("+","Zoom in") {zoom2D(1.3f,Offset(size.width/2f,size.height/2f))}
            PlotButton("−","Zoom out") {zoom2D(1/1.3f,Offset(size.width/2f,size.height/2f))}
        }
    }
}
@Composable private fun PlotButton(label:String,description:String,onClick:()->Unit) {
    GraphControl(description, onClick = onClick) {
        Canvas(Modifier.size(22.dp)) {
            val color = DemosColors.Muted
            val width = 1.8.dp.toPx()
            if (label == "⌂") {
                val home = Path().apply {
                    moveTo(size.width * .12f, size.height * .45f)
                    lineTo(size.width * .5f, size.height * .12f)
                    lineTo(size.width * .88f, size.height * .45f)
                    moveTo(size.width * .24f, size.height * .38f)
                    lineTo(size.width * .24f, size.height * .85f)
                    lineTo(size.width * .76f, size.height * .85f)
                    lineTo(size.width * .76f, size.height * .38f)
                }
                drawPath(home, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawLine(color, Offset(size.width * .18f, size.height * .5f), Offset(size.width * .82f, size.height * .5f), width, StrokeCap.Round)
                if (label == "+") drawLine(color, Offset(size.width * .5f, size.height * .18f), Offset(size.width * .5f, size.height * .82f), width, StrokeCap.Round)
            }
        }
    }
}
private data class PlotImages(val uris:List<String>,val bitmaps:Map<String,ImageBitmap>)
private data class PlotInput(val expressions:List<PlotExpression>,val settings:PlotSettings)
private data class CompiledPlot(val input:PlotInput,val program:PlotProgram)
private data class PlotSamplingInput(val compiled:CompiledPlot,val view:Viewport,val width:Int)

private fun niceStep(target:Double):Double {
    if(!target.isFinite() || target<=0.0) return 1.0
    val magnitude=10.0.pow(floor(log10(target)))
    val normalized=target/magnitude
    return magnitude*when {normalized<1.4 -> 1.0;normalized<3.2 -> 2.0;normalized<7 -> 5.0;else -> 10.0}
}
private fun validBounds(low:Double?,high:Double?):Pair<Double,Double>? =
    if(low!=null && high!=null && low.isFinite() && high.isFinite() && low<high) low to high else null
private fun number(v:Double):String {
    if(v==0.0) return "0"
    return if(abs(v)>=1e7 || abs(v)<1e-4) String.format(Locale.US,"%.3g",v)
    else if(abs(v-round(v))<1e-7) round(v).toLong().toString()
    else String.format(Locale.US,"%.4f",v).trimEnd('0').trimEnd('.')
}
