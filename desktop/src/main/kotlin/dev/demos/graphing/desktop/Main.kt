package dev.demos.graphing.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.demos.graphing.CalculatorController
import dev.demos.graphing.CalculatorEffect
import dev.demos.graphing.cas.CasEngine
import dev.demos.graphing.cas.runCasWorker
import dev.demos.graphing.data.GraphRepository
import dev.demos.graphing.platform.WindowsCalculatorPreferences
import dev.demos.graphing.plot.GraphPlot
import dev.demos.graphing.plot.PlotSettings
import dev.demos.graphing.ui.*
import kotlinx.coroutines.*
import java.awt.Dimension
import java.io.File
import javax.imageio.ImageIO

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--cas-worker") {
        require(args.size == 3)
        runCasWorker(File(args[1]), File(args[2]))
        return
    }
    application {
        val scope = rememberCoroutineScope()
        val model = remember { CalculatorController(GraphRepository(WindowsRuntime.dataDirectory), CasEngine(), WindowsCalculatorPreferences(), scope, showKeypadOnInput = false) }
        val current by model.state.collectAsState()
        var closing by remember { mutableStateOf(false) }
        Window(title = "${current.title} — Demos", state = rememberWindowState(width = 1080.dp, height = 760.dp),
            onCloseRequest = {
                if (!closing) {
                    closing = true
                    scope.launch {
                        try { model.flushRecovery(); exitApplication() }
                        catch (failure: Exception) { model.showMessage("Could not save: ${failure.message}"); closing = false }
                    }
                }
            },
            onPreviewKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown) false
                else when {
                    event.isCtrlPressed && event.key == Key.O -> { model.onAction(CalculatorAction.ImportDocument); true }
                    event.isCtrlPressed && event.key == Key.S -> { model.onAction(CalculatorAction.Save(current.title)); true }
                    event.key == Key.Escape && current.keyboardVisible -> { model.onAction(CalculatorAction.SetKeyboardVisible(false)); true }
                    event.key == Key.Escape && current.layout != CalculatorLayout.Split -> { model.onAction(CalculatorAction.SetLayout(CalculatorLayout.Split)); true }
                    else -> false
                }
            }) {
            SideEffect {
                window.minimumSize = Dimension(760, 560)
                System.setProperty("demos.native.dir", WindowsRuntime.nativeDirectory.absolutePath)
            }
            DisposableEffect(model) { onDispose { model.close() } }
            val graphLayer = rememberGraphicsLayer()
            var capturing by remember { mutableStateOf(false) }
            DemosTheme {
                Box(Modifier.fillMaxSize().background(DemosColors.Header)) {
                    CalculatorScreen(current, model::onAction, showGraphControls = !capturing, onOpenLibrary = { model.loadLibrary() }) { is3D, modifier ->
                        GraphPlot(model.plotExpressions(current), is3D, graphSettings(model, current, is3D),
                            modifier.drawWithContent { graphLayer.record { this@drawWithContent.drawContent() }; drawLayer(graphLayer) }, showControls = !capturing)
                    }
                }
            }
            LaunchedEffect(model) {
                val files = WindowsFiles(window, model)
                model.effects.collect { effect ->
                    try {
                        when (effect) {
                            is CalculatorEffect.ExportDocument -> files.saveText(effect.name, effect.contents)
                            is CalculatorEffect.ExportTable -> files.saveText(effect.name, effect.contents)
                            CalculatorEffect.ImportDocument -> files.openText(5_000_000)?.let(model::importDocument)
                            CalculatorEffect.ImportTable -> files.openText(1_000_000)?.let(model::importTable)
                            CalculatorEffect.ImportImage -> files.importImage()
                            CalculatorEffect.ExportImage, is CalculatorEffect.ExportGraph -> {
                                check(model.state.value.layout != CalculatorLayout.Calculator) { "Show the graph before exporting it" }
                                capturing = true
                                try {
                                    withFrameNanos { }; withFrameNanos { }
                                    val image = graphLayer.toImageBitmap().toAwtImage()
                                    val format = (effect as? CalculatorEffect.ExportGraph)?.format ?: GraphExportFormat.PNG
                                    files.saveGraph(image, format)
                                } finally { capturing = false }
                            }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { model.showMessage(failure.message ?: "The file operation failed") }
                }
            }
        }
    }
}

private fun graphSettings(model: CalculatorController, current: CalculatorUiState, is3D: Boolean): PlotSettings {
    val bounds = if (is3D) current.bounds3D else current.bounds
    return PlotSettings(showAxes = if (is3D) current.showAxes3D else current.showAxes,
        showGrid = if (is3D) current.showGrid3D else current.showGrid,
        appearance = if (is3D) current.appearance3D else current.appearance2D,
        radians = !current.degreeMode, parameters = model.plotParameters(current),
        xMin = bounds.xMin, xMax = bounds.xMax, yMin = bounds.yMin, yMax = bounds.yMax,
        zMin = bounds.zMin, zMax = bounds.zMax, tMin = bounds.tMin, tMax = bounds.tMax,
        uMin = bounds.uMin, uMax = bounds.uMax, vMin = bounds.vMin, vMax = bounds.vMax)
}
