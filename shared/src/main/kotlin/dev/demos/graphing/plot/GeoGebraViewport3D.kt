package dev.demos.graphing.plot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.demos.graphing.ui.interfaceTextStyle

private val ControlBorder = Color(0xFFD4D4D4)
private val ControlInk = Color(0xFF404040)
private val ControlBlue = Color(0xFF155DFC)
private fun viewportTextStyle(size: androidx.compose.ui.unit.TextUnit) = interfaceTextStyle(size).copy(
    fontFamily=referenceViewportFontFamily(),lineHeight=when(size.value){12f->16.sp;14f->20.sp;else->size*1.5f})

/** Native port of geographer's GraphViewport wrapper; the engine draws every 3D primitive. */
@Composable
internal fun GeoGebraViewport3D(
    expressions: List<PlotExpression>, settings: PlotSettings, modifier: Modifier,
    showControls: Boolean, renderState: GraphRenderState, interactive: Boolean,
    onSettingsChange: (Viewport3DSettings) -> Unit, active: Boolean = true, cameraState: GeoGebraCamera? = null,
) {
    val saved = cameraState ?: rememberGeoGebraCamera()
    val commands = remember { GeoGebraCommands() }
    var settingsOpen by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(settings.viewport3DSettings())
    fun setView(factor: Double = 1.0) {
        if(!commands.available)return
        val b=current.bounds3D
        val cx=(b.xmin+b.xmax)/2; val cy=(b.ymin+b.ymax)/2; val cz=(b.zmin+b.zmax)/2
        val hw=(b.xmax-b.xmin)*factor/2; val hh=(b.ymax-b.ymin)*factor/2; val hd=(b.zmax-b.zmin)*factor/2
        commands.submit { it.bounds(cx-hw,cx+hw,cy-hh,cy+hh,cz-hd,cz+hd) }
    }
    BoxWithConstraints(modifier.background(if(settings.appearance.reverseContrast)Color.Black else Color.White)
        .clipToBounds().semantics { contentDescription="Native 3D viewport" }) {
        GeoGebraSurface(expressions,settings,saved,commands,renderState,interactive,active)
        if(showControls) {
            Surface(Modifier.align(Alignment.BottomStart).padding(12.dp),shape=RoundedCornerShape(4.dp),
                color=Color.White.copy(alpha=.9f),border=BorderStroke(1.dp,Color(0xFFE5E5E5))) {
                Text("Drag to orbit · Scroll to zoom",Modifier.padding(horizontal=11.dp,vertical=5.dp),
                    style=viewportTextStyle(11.sp).copy(color=Color(0xFF525252)))
            }
            Column(Modifier.align(Alignment.TopEnd).padding(12.dp),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                ViewportButton(ViewportIcon.Settings,"Graph settings",selected=settingsOpen){settingsOpen=!settingsOpen}
                Surface(Modifier.width(38.dp),shape=RoundedCornerShape(8.dp),color=Color.White.copy(alpha=.95f),border=BorderStroke(1.dp,ControlBorder),shadowElevation=3.dp) {
                    Column(Modifier.padding(1.dp)) {
                        ViewportButton(ViewportIcon.Plus,"Zoom in",grouped=true){setView(.75)}
                        HorizontalDivider(color=Color(0xFFE5E5E5))
                        ViewportButton(ViewportIcon.Minus,"Zoom out",grouped=true){setView(1.333)}
                        HorizontalDivider(color=Color(0xFFE5E5E5))
                        ViewportButton(ViewportIcon.Home,"Reset view",grouped=true){setView()}
                    }
                }
            }
            if(settingsOpen) {
                Native3DSettingsCard(current,Modifier.align(Alignment.TopEnd).padding(top=174.dp,end=12.dp)
                    .requiredWidth(320.dp)
                    .heightIn(max=((maxHeight+48.dp)*.82f).coerceAtLeast(80.dp)),onChange=onSettingsChange,onClose={settingsOpen=false})
            }
        }
    }
}

private enum class ViewportIcon { Settings, Plus, Minus, Home, Reset, Close,Loader,Alert }

@Composable
private fun ViewportButton(icon: ViewportIcon, description: String, selected: Boolean = false,
    grouped: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.size(36.dp,if(grouped && icon!=ViewportIcon.Home)35.dp else 36.dp).semantics { contentDescription = description },
        shape = if (grouped) RoundedCornerShape(0.dp) else RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFF262626) else Color.White.copy(alpha=.95f),
        border = if (grouped) null else BorderStroke(1.dp, if (selected) Color(0xFF262626) else ControlBorder),
        shadowElevation = if (grouped) 0.dp else 3.dp) {
        Box(contentAlignment = Alignment.Center) {
            ViewportGlyph(icon, if (selected) Color.White else ControlInk)
        }
    }
}

// Exact lucide-react 0.546.0 paths from the reference (ISC license).
private val viewportPaths = mapOf(
    ViewportIcon.Settings to listOf("M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.106-3.105c.32-.322.863-.22.983.218a6 6 0 0 1-8.259 7.057l-7.91 7.91a1 1 0 0 1-2.999-3l7.91-7.91a6 6 0 0 1 7.057-8.259c.438.12.54.662.219.984z"),
    ViewportIcon.Home to listOf("M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8", "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"),
    ViewportIcon.Plus to listOf("M5 12h14", "M12 5v14"),
    ViewportIcon.Minus to listOf("M5 12h14"),
    ViewportIcon.Close to listOf("M18 6 6 18", "m6 6 12 12"),
    ViewportIcon.Reset to listOf("M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5"),
    ViewportIcon.Loader to listOf("M21 12a9 9 0 1 1-6.219-8.56"),
    ViewportIcon.Alert to listOf("M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0", "M12 8v4", "M12 16h.01"),
).mapValues { (_,paths)->paths.map {androidx.compose.ui.graphics.vector.PathParser().parsePathString(it).toPath()} }

@Composable
private fun ViewportGlyph(icon: ViewportIcon,color: Color,size: androidx.compose.ui.unit.Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val factor=this.size.width/24f
        withTransform({scale(factor,factor,pivot=Offset.Zero)}) {
            for(path in viewportPaths.getValue(icon))drawPath(path,color,style=Stroke(2f,cap=StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

@Composable
internal fun GeoGebraStatusOverlay(failed: Boolean,onRetry: ()->Unit) {
    Column(Modifier.fillMaxSize().background(Color.White.copy(alpha=if(failed)1f else .9f)).pointerInput(Unit) {
        awaitPointerEventScope {while(true)awaitPointerEvent().changes.forEach {it.consume()}}
    }.padding(24.dp),
        verticalArrangement=Arrangement.spacedBy(if(failed)12.dp else 8.dp,Alignment.CenterVertically),
        horizontalAlignment=Alignment.CenterHorizontally) {
        if(failed) {
            ViewportGlyph(ViewportIcon.Alert,Color(0xFFD08700),32.dp)
            Column(Modifier.widthIn(max=384.dp),verticalArrangement=Arrangement.spacedBy(4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Text("GeoGebra Engine Failed to Load",style=viewportTextStyle(14.sp).copy(fontWeight=FontWeight.SemiBold,color=Color(0xFF171717)))
                Text("Close and reopen this graph, then retry.",style=viewportTextStyle(12.sp).copy(color=Color(0xFF525252)))
            }
            Surface(onClick=onRetry,modifier=Modifier.height(28.dp),shape=RoundedCornerShape(4.dp),color=ControlBlue) {
                Text("Retry Engine",Modifier.padding(horizontal=12.dp,vertical=6.dp),style=viewportTextStyle(12.sp).copy(color=Color.White,fontWeight=FontWeight.SemiBold))
            }
        }else{
            val transition=rememberInfiniteTransition()
            val angle by transition.animateFloat(0f,360f,infiniteRepeatable(tween(1000,easing=LinearEasing)))
            Box(Modifier.rotate(angle)){ViewportGlyph(ViewportIcon.Loader,ControlBlue,24.dp)}
            Text("Initializing GeoGebra 3D Engine...",style=viewportTextStyle(14.sp).copy(fontWeight=FontWeight.Medium,color=Color(0xFF525252)))
        }
    }
}

@Composable
private fun Native3DSettingsCard(settings: Viewport3DSettings, modifier: Modifier,
    onChange: (Viewport3DSettings) -> Unit, onClose: () -> Unit) {
    Surface(modifier.semantics { contentDescription = "3D graph settings" }, shape = RoundedCornerShape(8.dp),
        color = Color.White, border = BorderStroke(1.dp, Color(0xFFE5E5E5)), shadowElevation = 8.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(17.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Graph Settings", Modifier.weight(1f), style = viewportTextStyle(14.sp).copy(fontWeight = FontWeight.SemiBold, color = Color(0xFF171717)))
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(22.dp).semantics { contentDescription = "Reset graph settings" }.clickable { onChange(Viewport3DSettings()) }, contentAlignment = Alignment.Center) { ViewportGlyph(ViewportIcon.Reset, Color(0xFF737373),14.dp) }
                        Box(Modifier.size(24.dp).semantics { contentDescription = "Close graph settings" }.clickable(onClick = onClose), contentAlignment = Alignment.Center) { ViewportGlyph(ViewportIcon.Close, Color(0xFF737373)) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFFF5F5F5))
            }
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                ViewportCheck("Grid", settings.showGrid) { onChange(settings.copy(showGrid = it)) }
                ViewportCheck("Reverse Contrast", settings.reverseContrast,Modifier.padding(start=4.dp),medium=false) { onChange(settings.copy(reverseContrast = it)) }
            }
            Column {
                HorizontalDivider(color=Color(0xFFF5F5F5))
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Axes & Bounds", style = viewportTextStyle(12.sp).copy(fontWeight = FontWeight.SemiBold, color = ControlInk))
                    repeat(3) { axis ->
                        val visible = listOf(settings.showXAxis,settings.showYAxis,settings.showZAxis)[axis]
                        val label = listOf(settings.xAxisLabel,settings.yAxisLabel,settings.zAxisLabel)[axis]
                        val b=settings.bounds3D
                        fun change(value: Double,minimum: Boolean) {
                            val bounds=when(axis) {
                                0->if(minimum)b.copy(xmin=value) else b.copy(xmax=value)
                                1->if(minimum)b.copy(ymin=value) else b.copy(ymax=value)
                                else->if(minimum)b.copy(zmin=value) else b.copy(zmax=value)
                            }
                            if(bounds.isValid())onChange(settings.copy(bounds3D=bounds))
                        }
                        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                ViewportCheck("${"XYZ"[axis]}-Axis",visible,Modifier.weight(1f),gap=6.dp) {next->onChange(when(axis){
                                    0->settings.copy(showXAxis=next);1->settings.copy(showYAxis=next);else->settings.copy(showZAxis=next)
                                })}
                                ViewportTextField(label,"${"XYZ"[axis]}-axis label",Modifier.width(112.dp)) {next->onChange(when(axis){
                                    0->settings.copy(xAxisLabel=next);1->settings.copy(yAxisLabel=next);else->settings.copy(zAxisLabel=next)
                                })}
                            }
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                ViewportNumberField(listOf(b.xmin,b.ymin,b.zmin)[axis],"3D ${"XYZ"[axis]} min",Modifier.width(80.dp)){change(it,true)}
                                Text("≤ ${"xyz"[axis]} ≤",style=viewportTextStyle(11.sp).copy(lineHeight=(11f*4/3).sp,color=Color(0xFF737373)))
                                ViewportNumberField(listOf(b.xmax,b.ymax,b.zmax)[axis],"3D ${"XYZ"[axis]} max",Modifier.width(80.dp)){change(it,false)}
                            }
                        }
                    }
                }
            }
            Column {
                HorizontalDivider(color=Color(0xFFF5F5F5))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Angle Unit", Modifier.weight(1f), style = viewportTextStyle(12.sp).copy(fontWeight = FontWeight.SemiBold, color = ControlInk))
                    Surface(shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Color(0xFFE5E5E5))) {
                        Row(Modifier.padding(1.dp)) {
                            listOf("rad" to "Radians", "deg" to "Degrees").forEach { (value,label) ->
                                Text(label, Modifier.background(if(settings.angleMode==value) ControlBlue else Color.White)
                                    .clickable { onChange(settings.copy(angleMode=value)) }.padding(horizontal=12.dp,vertical=4.dp),
                                    style = viewportTextStyle(11.sp).copy(lineHeight=(11f*4/3).sp,fontWeight=FontWeight.Medium,color = if(settings.angleMode==value) Color.White else ControlInk))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewportCheck(label: String, checked: Boolean, modifier: Modifier = Modifier,medium: Boolean=true,
    gap: androidx.compose.ui.unit.Dp=8.dp,onChange: (Boolean) -> Unit) {
    Row(modifier.toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
        Canvas(Modifier.size(13.dp)) {
            drawRoundRect(if(checked) Color(0xFF0075FF) else Color.White, cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                style = if(checked) androidx.compose.ui.graphics.drawscope.Fill else Stroke(1.dp.toPx()))
            if(!checked) drawRoundRect(Color(0xFF9CA3AF), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = Stroke(1.dp.toPx()))
            if(checked) {
                val p=Path().apply { moveTo(size.width*.18f,size.height*.5f);lineTo(size.width*.4f,size.height*.75f);lineTo(size.width*.84f,size.height*.24f) }
                drawPath(p,Color.White,style=Stroke(1.5.dp.toPx(),cap=StrokeCap.Round))
            }
        }
        Text(label, style = viewportTextStyle(12.sp).copy(color = ControlInk,fontWeight=if(medium)FontWeight.Medium else FontWeight.Normal))
    }
}

@Composable
private fun ViewportTextField(value: String, description: String, modifier: Modifier, onChange: (String) -> Unit) {
    BasicTextField(value, onChange, singleLine = true, textStyle = viewportTextStyle(12.sp).copy(color = ControlInk),
        cursorBrush = SolidColor(ControlBlue), modifier = modifier.semantics { contentDescription = description },
        decorationBox = { inner -> Surface(shape = RoundedCornerShape(4.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE5E5E5))) {
            Box(Modifier.height(26.dp).padding(horizontal=9.dp,vertical=5.dp),contentAlignment=Alignment.CenterStart) {
                if(value.isEmpty())Text("Label",style=viewportTextStyle(12.sp).copy(color=Color(0xFF909090)))
                inner()
            }
        } })
}

@Composable
private fun ViewportNumberField(value: Double, description: String, modifier: Modifier, onChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(if(value==value.toLong().toDouble()) value.toLong().toString() else value.toString()) }
    BasicTextField(text, { next -> text=next; next.toDoubleOrNull()?.takeIf { it.isFinite() }?.let(onChange) },
        singleLine = true, textStyle = viewportTextStyle(11.sp).copy(lineHeight=(11f*4/3).sp,color=ControlInk,textAlign=androidx.compose.ui.text.style.TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType=KeyboardType.Decimal), cursorBrush=SolidColor(ControlBlue),
        modifier = modifier.semantics { contentDescription = description }, decorationBox = { inner ->
            Surface(shape=RoundedCornerShape(4.dp),color=Color.White,border=BorderStroke(1.dp,Color(0xFFE5E5E5))) {
                Box(Modifier.height((10f+11f*4/3).dp).padding(horizontal=7.dp,vertical=5.dp),contentAlignment=Alignment.Center) { inner() }
            }
        })
}
