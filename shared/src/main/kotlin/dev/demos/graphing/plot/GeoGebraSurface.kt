package dev.demos.graphing.plot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.demos.graphing.geogebra.NativeGeoGebraEngine
import dev.demos.graphing.geogebra.NativePointer
import dev.demos.graphing.geogebra.NativeCas
import dev.demos.graphing.ui.createPlotImage
import dev.demos.graphing.ui.LocalAppConfiguration
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

class GeoGebraCamera {
    @Volatile internal var xml=""
    @Volatile internal var scene=""
    internal var cas: NativeCas?=null
    companion object {val Saver=Saver<GeoGebraCamera,List<String>>(save={listOf(it.xml,it.scene)},restore={GeoGebraCamera().apply {xml=it[0];scene=it[1]}})}
}
@Composable internal fun rememberGeoGebraCamera()=rememberSaveable(saver=GeoGebraCamera.Saver){GeoGebraCamera()}
internal class GeoGebraCommands {
    @Volatile var available=false
    private data class Command(val revision: Int,val run: (NativeGeoGebraEngine)->Unit)
    private val queue=ConcurrentLinkedQueue<Command>()
    var revision by mutableIntStateOf(0)
        private set
    var onPending: ()->Unit = {}
    private var applied=0
    fun submit(command: (NativeGeoGebraEngine)->Unit){revision++;queue.add(Command(revision,command));onPending()}
    fun drain(engine: NativeGeoGebraEngine): Int {
        while(true){val command=queue.poll()?:break;command.run(engine);applied=command.revision}
        return applied
    }
}
private data class EngineInput(val expressions: List<PlotExpression>,val settings: PlotSettings,val boundsResetCounter: Int)
private data class EngineFrame(val image: ImageBitmap,val input: EngineInput,val size: IntSize,val animated: Boolean,val revision: Int)

@Composable
internal fun GeoGebraSurface(expressions: List<PlotExpression>,settings: PlotSettings,camera: GeoGebraCamera,
    commands: GeoGebraCommands,renderState: GraphRenderState,interactive: Boolean,active: Boolean) {
    val density=LocalDensity.current.density
    val configuration=LocalAppConfiguration.current
    var left by remember {mutableStateOf(0f)}
    var size by remember {mutableStateOf(IntSize.Zero)}
    val layout=referenceGeoGebraLayout(size,density,left,configuration.screenWidthDp,configuration.screenHeightDp)
    val latestLayout by rememberUpdatedState(layout)
    val input=EngineInput(expressions,settings,renderState.boundsReset3DCounter)
    val latestInput by rememberUpdatedState(input)
    val latestSize by rememberUpdatedState(size)
    val latestDensity by rememberUpdatedState(density)
    val latestActive by rememberUpdatedState(active)
    var frame by remember {mutableStateOf<EngineFrame?>(null)}
    var failure by remember {mutableStateOf<String?>(null)}
    var retry by remember {mutableIntStateOf(0)}
    val focusRequester=remember {FocusRequester()}
    LaunchedEffect(retry) {
        failure=null
        frame=null
        val executor=Executors.newSingleThreadExecutor {job->Thread(job,"Demos GeoGebra 3D").apply {isDaemon=true}}
        val dispatcher=executor.asCoroutineDispatcher()
        try {
            withContext(dispatcher) {
                while(latestSize.width<1 || latestSize.height<1)delay(16)
                createGeoGebraGraphics().use {graphics ->
                    NativeGeoGebraEngine(graphics.text,camera.cas).use {engine ->
                        var surfaceSize=latestSize
                        var surfaceDensity=latestDensity*latestLayout.scale
                        graphics.resize(surfaceSize.width,surfaceSize.height,surfaceDensity)
                        engine.initialize(graphics.gl,latestLayout.width,latestLayout.height)
                        val adapter=GeoGebraSceneAdapter(engine.api())
                        var applied: EngineInput?=null
                        var pendingFrames=3
                        var lastRevision=-1
                        val restored=camera.xml.isNotEmpty()
                        if(camera.scene.isNotEmpty())runCatching {adapter.restoreState(camera.scene)}
                        if(restored)engine.restoreCamera(camera.xml)
                        while(isActive) {
                            if(!latestActive){delay(50);continue}
                            val start=System.nanoTime()
                            val next=latestInput;val nextSize=latestSize;val nextDensity=latestDensity*latestLayout.scale
                            if(nextSize.width<1 || nextSize.height<1){delay(16);continue}
                            var dirty=false
                            if(surfaceSize!=nextSize || surfaceDensity!=nextDensity) {
                                dirty=true
                                graphics.resize(nextSize.width,nextSize.height,nextDensity)
                                engine.resize(latestLayout.width,latestLayout.height)
                                surfaceSize=nextSize;surfaceDensity=nextDensity
                            }
                            if(applied!=next) {
                                dirty=true
                                val display=next.settings.viewport3DSettings()
                                engine.settings(display.showGrid,display.showXAxis,display.showYAxis,display.showZAxis)
                                val appearance=next.settings.appearance
                                engine.axisAppearance(appearance.numbers,appearance.labels,appearance.xLabel,appearance.yLabel,appearance.zLabel)
                                val resetBounds=applied!=null && applied.boundsResetCounter!=next.boundsResetCounter
                                if(resetBounds || (applied?.settings?.viewport3DSettings()?.bounds3D!=display.bounds3D && (applied!=null || !restored))) {
                                    val b=display.bounds3D;engine.bounds(b.xmin,b.xmax,b.ymin,b.ymax,b.zmin,b.zmax)
                                }
                                adapter.sync(next.expressions,next.settings)
                                applied=next
                            }
                            val revision=commands.drain(engine)
                            if(revision!=lastRevision){dirty=true;lastRevision=revision}
                            if(dirty)pendingFrames=3
                            if(pendingFrames==0 && !engine.needsAnimationFrame()){delay(16);continue}
                            engine.draw()
                            if(engine.needsAnimationFrame())pendingFrames=3 else pendingFrames=(pendingFrames-1).coerceAtLeast(0)
                            commands.available=true
                            camera.xml=engine.cameraXml()
                            if(dirty || camera.scene.isEmpty())camera.scene=adapter.saveState(engine.sceneXml())
                            val pixels=graphics.pixels(next.settings.appearance.reverseContrast)
                            frame=EngineFrame(createPlotImage(surfaceSize.width,surfaceSize.height,pixels),next,surfaceSize,engine.isSettling,revision)
                            val remaining=16-(System.nanoTime()-start)/1_000_000
                            if(remaining>0)delay(remaining) else yield()
                        }
                    }
                }
            }
        } catch(cancelled: CancellationException){throw cancelled}
          catch(error: Exception){error.printStackTrace();failure=error.message?:"The 3D view could not open";renderState.invalidate()}
        finally {commands.available=false;dispatcher.close()}
    }
    DisposableEffect(renderState){onDispose {renderState.invalidate()}}
    SideEffect {commands.onPending=renderState::invalidate;if(frame?.input!=input || frame?.size!=size || frame?.animated!=false || frame?.revision!=commands.revision)renderState.invalidate()}
    Canvas(Modifier.fillMaxSize().onGloballyPositioned {coordinates->
        size=coordinates.size;left=coordinates.positionInRoot().x/density
    }.focusRequester(focusRequester).onFocusChanged {state->commands.submit {it.focus(state.isFocused)}}
        .onKeyEvent {event->
            if(!interactive || !active)return@onKeyEvent false
            val name=when(event.key){
                Key.DirectionLeft->"LEFT";Key.DirectionRight->"RIGHT";Key.DirectionUp->"UP";Key.DirectionDown->"DOWN"
                Key.Plus->"PLUS";Key.Minus->"MINUS";Key.Equals->"EQUALS"
                Key.NumPadAdd->"ADD";Key.NumPadSubtract->"SUBTRACT"
                Key.MoveHome->"HOME";Key.MoveEnd->"END";Key.PageUp->"PAGEUP";Key.PageDown->"PAGEDOWN";Key.Insert->"INSERT"
                Key.Backspace->"BACKSPACE";Key.Delete->"DELETE";Key.Escape->"ESCAPE"
                Key.Spacebar->"SPACE";Key.Enter,Key.NumPadEnter->"ENTER";Key.ShiftLeft,Key.ShiftRight->"SHIFT"
                Key.M->if(event.isCtrlPressed || event.isMetaPressed)"M" else null
                else->null
            }?:return@onKeyEvent false
            val modifiers=(if(event.isAltPressed)1 else 0) or (if(event.isShiftPressed)2 else 0) or
                (if(event.isCtrlPressed)4 else 0) or (if(event.isMetaPressed)8 else 0)
            commands.submit {it.key(name,event.type==KeyEventType.KeyDown,modifiers)}
            true
        }.focusable(interactive && active).pointerInput(commands,interactive,density,layout.scale) {
        if(!interactive)return@pointerInput
        awaitPointerEventScope {
            var down=false;var multitouch=false;var button=0
            while(true) {
                val event=awaitPointerEvent()
                val pressed=event.changes.filter {it.pressed}
                val touch=event.changes.firstOrNull()?.type==PointerType.Touch
                val modifiers=event.keyboardModifiers
                val flags=(if(modifiers.isAltPressed)1 else 0) or (if(modifiers.isShiftPressed)2 else 0) or
                    (if(modifiers.isCtrlPressed)4 else 0) or (if(modifiers.isMetaPressed)8 else 0)
                if(pressed.size>=2) {
                    val a=pressed[0].position/(density*layout.scale);val b=pressed[1].position/(density*layout.scale)
                    if(!multitouch)commands.submit {it.twoTouchStart(a.x.toDouble(),a.y.toDouble(),b.x.toDouble(),b.y.toDouble())}
                    else commands.submit {it.twoTouchMove(a.x.toDouble(),a.y.toDouble(),b.x.toDouble(),b.y.toDouble())}
                    multitouch=true;event.changes.forEach {it.consume()};continue
                }
                if(multitouch) {
                    if(pressed.isEmpty()){
                        val last=event.changes.first().position/(density*layout.scale)
                        val release=NativePointer(last.x.roundToInt(),last.y.roundToInt(),button,flags,1,touch,event.changes.first().uptimeMillis)
                        commands.submit {it.twoTouchEnd();it.pointer(2,release)}
                        multitouch=false;down=false
                    }
                    event.changes.forEach {it.consume()};continue
                }
                val change=event.changes.firstOrNull()?:continue
                val position=change.position/(density*layout.scale)
                if(event.type==PointerEventType.Scroll) {
                    // GeoGebra uses the sign of the browser delta; Compose supplies line deltas.
                    val delta=event.changes.sumOf {it.scrollDelta.y.toDouble()}
                    if(delta!=0.0)commands.submit {it.wheel(position.x.roundToInt(),position.y.roundToInt(),delta,flags and 10!=0,flags and 1!=0)}
                    event.changes.forEach {it.consume()};continue
                }
                if(!down && pressed.isNotEmpty()) {
                    focusRequester.requestFocus()
                    button=when {event.buttons.isSecondaryPressed->2;event.buttons.isTertiaryPressed->1;else->0}
                    val pointer=NativePointer(position.x.roundToInt(),position.y.roundToInt(),button,flags,1,touch,change.uptimeMillis)
                    commands.submit {it.pointer(0,pointer)};down=true
                } else if(down && pressed.isEmpty()) {
                    val pointer=NativePointer(position.x.roundToInt(),position.y.roundToInt(),button,flags,1,touch,change.uptimeMillis)
                    commands.submit {it.pointer(2,pointer)};down=false
                } else if(event.type==PointerEventType.Enter || event.changes.any {it.position!=it.previousPosition}) {
                    val pointer=NativePointer(position.x.roundToInt(),position.y.roundToInt(),button,flags,1,touch,change.uptimeMillis)
                    val action=if(down)1 else 3
                    commands.submit {it.pointer(action,pointer)}
                } else if(event.type==PointerEventType.Exit && !down) {
                    commands.submit {it.pointer(4,NativePointer(0,0,button,flags,1,touch))}
                }
                if(down || event.type==PointerEventType.Release)event.changes.forEach {it.consume()}
            }
        }
    }) {
        frame?.let {current ->
            drawImage(current.image,dstSize=IntSize(this.size.width.toInt(),this.size.height.toInt()))
            renderState.ready=current.input==input && current.size==latestSize && !current.animated && current.revision==commands.revision
        }
    }
    if(failure!=null || frame==null)GeoGebraStatusOverlay(failure!=null){retry++}
}
