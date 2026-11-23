package dev.demos.graphing.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
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
import dev.demos.graphing.plot.GraphRenderState
import dev.demos.graphing.ui.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--cas-worker") {
        require(args.size == 3)
        runCasWorker(File(args[1]), File(args[2]))
        return
    }
    StartupTrace.mark("main")
    System.setProperty("demos.native.dir", WindowsRuntime.nativeDirectory.absolutePath)
    application {
        remember {
            // Enumerate Windows fonts while the model and window are created.
            thread(name = "demos-font-init", isDaemon = true) {
                org.jetbrains.skia.FontMgr.default.matchFamilyStyle("Arial", org.jetbrains.skia.FontStyle.NORMAL)?.close()
            }
        }
        val scope = rememberCoroutineScope()
        val model = remember {
            CalculatorController(GraphRepository(WindowsRuntime.dataDirectory), CasEngine(), WindowsCalculatorPreferences(), scope)
                .also { StartupTrace.mark("model") }
        }
        val current by model.state.collectAsState()
        var closing by remember { mutableStateOf(false) }
        Window(title = "${current.title} — Demos", icon = painterResource("demos.png"),
            state = rememberWindowState(width = 1080.dp, height = 760.dp),
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
            DisposableEffect(model) { onDispose { model.close() } }
            LaunchedEffect(Unit) { StartupTrace.mark("content") }
            val graphLayer = rememberGraphicsLayer()
            var capturing by remember { mutableStateOf(false) }
            val graphRendering = remember { GraphRenderState() }
            CalculatorApp(current, model, showGraphControls = !capturing, renderState = graphRendering,
                modifier = Modifier.drawWithContent {
                    drawContent()
                    StartupTrace.frameDrawn()
                },
                graphModifier = Modifier.drawWithContent {
                    if (capturing) {
                        graphLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphLayer)
                    } else {
                        drawContent()
                    }
                })
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
                                graphRendering.invalidate()
                                capturing = true
                                try {
                                    graphRendering.awaitReady { withFrameNanos { } }
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
