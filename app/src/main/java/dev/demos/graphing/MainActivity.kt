package dev.demos.graphing

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.android.awaitFrame
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.demos.graphing.plot.GraphPlot
import dev.demos.graphing.plot.PlotSettings
import dev.demos.graphing.ui.CalculatorScreen
import dev.demos.graphing.ui.CalculatorLayout
import dev.demos.graphing.ui.GraphExportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val model by viewModels<CalculatorViewModel>()
    private var pendingExport: String? = null
    private var graphBounds = Rect()
    private var capturing by mutableStateOf(false)
    private var pendingFile: File? = null
    private var exportKind = "Graph"

    private val exportFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val file = pendingFile
        val kind = exportKind
        pendingFile = null
        val uri = result.data?.data.takeIf { result.resultCode == RESULT_OK }
        lifecycleScope.launch {
            try {
                if (uri != null) {
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri)?.use { output ->
                            requireNotNull(file) { "The export was interrupted. Please export again." }.inputStream().use { it.copyTo(output) }
                        } ?: error("The selected file could not be opened")
                    }
                    model.showMessage("$kind exported")
                }
            } catch (failure: Exception) { model.showMessage("Export failed: ${failure.message}") }
            finally { withContext(Dispatchers.IO) { file?.delete() } }
        }
    }

    private val importTable = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { readDocument(it, 1_000_000) }
                    ?: error("The selected file could not be opened")
            } }.onSuccess(model::importTable).onFailure { model.showMessage("Import failed: ${it.message}") }
        }
    }

    private fun chooseExport(name: String, mimeType: String) {
        exportFile.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, name)
        })
    }

    private val exportDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val contents = pendingExport
        pendingExport = null
        if (uri != null && contents != null) lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(uri)?.use { it.write(contents.toByteArray()) }
                    ?: error("The selected file could not be opened")
            } }.onSuccess { model.showMessage("Graph exported") }
                .onFailure { model.showMessage("Export failed: ${it.message}") }
        }
    }

    private val importDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { readDocument(it) }
                    ?: error("The selected file could not be opened")
            } }.onSuccess(model::importDocument).onFailure { model.showMessage("Import failed: ${it.message}") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString("export-file")?.let { name ->
            val directory = File(cacheDir, "exports").canonicalFile
            val file = File(directory, name).canonicalFile
            if (file.parentFile == directory && file.isFile) pendingFile = file
        }
        exportKind = savedInstanceState?.getString("export-kind") ?: "Graph"
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            val current by model.state.collectAsStateWithLifecycle()
            LaunchedEffect(current.layout) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (current.layout == CalculatorLayout.Split) show(WindowInsetsCompat.Type.systemBars())
                    else hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF2D70D9), background = Color.White, surface = Color.White)) {
                Box(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.systemBars)) {
                    CalculatorScreen(current, model::onAction, showGraphControls = !capturing, onOpenLibrary = { model.loadLibrary() }) { is3D, modifier ->
                        GraphPlot(
                            expressions = model.plotExpressions(current),
                            is3D = is3D,
                            showControls = !capturing,
                            settings = PlotSettings(
                                showAxes = current.showAxes,
                                showGrid = current.showGrid,
                                radians = !current.degreeMode,
                                parameters = model.plotParameters(current),
                                xMin = current.bounds.xMin, xMax = current.bounds.xMax,
                                yMin = current.bounds.yMin, yMax = current.bounds.yMax,
                                zMin = current.bounds.zMin, zMax = current.bounds.zMax,
                                tMin = current.bounds.tMin, tMax = current.bounds.tMax,
                                uMin = current.bounds.uMin, uMax = current.bounds.uMax,
                                vMin = current.bounds.vMin, vMax = current.bounds.vMax,
                            ),
                            modifier = modifier.onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInWindow()
                                graphBounds = Rect(bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.right.roundToInt(), bounds.bottom.roundToInt())
                            },
                        )
                    }
                }
            }
            LaunchedEffect(Unit) {
                model.effects.collect { effect ->
                    when (effect) {
                        is CalculatorEffect.ExportDocument -> { pendingExport = effect.contents; exportDocument.launch(effect.name) }
                        CalculatorEffect.ImportDocument -> importDocument.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                        CalculatorEffect.ExportImage -> captureGraph(GraphExportFormat.PNG, share = true)
                        is CalculatorEffect.ExportGraph -> captureGraph(effect.format)
                        CalculatorEffect.ImportTable -> importTable.launch(arrayOf("text/*", "application/json", "application/vnd.ms-excel", "application/octet-stream"))
                        is CalculatorEffect.ExportTable -> {
                            if (pendingFile != null || capturing) model.showMessage("Finish the current export first")
                            else {
                                // Serialize before opening the picker; keep large tables out of
                                // activity state and recover the pending file after rotation.
                                capturing = true
                                lifecycleScope.launch {
                                    try {
                                        pendingFile = withContext(Dispatchers.IO) {
                                            val directory = File(cacheDir, "exports").apply { mkdirs() }
                                            File.createTempFile("demos-table-", ".${effect.format.extension}", directory).also {
                                                it.writeText(effect.contents, Charsets.UTF_8)
                                            }
                                        }
                                        exportKind = "Table"
                                        chooseExport(effect.name, effect.format.mimeType)
                                    } catch (failure: Exception) {
                                        pendingFile?.delete(); pendingFile = null
                                        model.showMessage("Table export failed: ${failure.message}")
                                    } finally { capturing = false }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingFile?.let { outState.putString("export-file", it.name) }
        outState.putString("export-kind", exportKind)
        super.onSaveInstanceState(outState)
    }

    private fun captureGraph(format: GraphExportFormat, share: Boolean = false) {
        if (capturing || pendingFile != null) { model.showMessage("Finish the current export first"); return }
        if (model.state.value.layout == CalculatorLayout.Calculator) { model.showMessage("Show the graph before exporting it"); return }
        capturing = true
        lifecycleScope.launch {
            // Wait for graph controls to leave the captured frame.
            awaitFrame(); awaitFrame()
            captureGraphFrame(format, share)
        }
    }

    private fun captureGraphFrame(format: GraphExportFormat, share: Boolean) {
        val bounds = graphBounds
        if (bounds.width() <= 0 || bounds.height() <= 0) { capturing = false; model.showMessage("The graph is not ready to export"); return }
        val surface = if (model.state.value.is3D) findGlSurface(window.decorView) else null
        val width = surface?.width ?: bounds.width()
        val height = surface?.height ?: bounds.height()
        if (width <= 0 || height <= 0) { capturing = false; model.showMessage("The graph is not ready to export"); return }
        val scale = minOf(1.0, kotlin.math.sqrt(4_000_000.0 / (width.toDouble() * height)))
        val bitmap = runCatching { Bitmap.createBitmap(maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()), Bitmap.Config.ARGB_8888) }
            .getOrElse { capturing = false; model.showMessage("Could not allocate the graph image"); return }
        val listener = PixelCopy.OnPixelCopyFinishedListener { result ->
            if (result != PixelCopy.SUCCESS) { capturing = false; bitmap.recycle(); model.showMessage("Could not capture graph ($result)"); return@OnPixelCopyFinishedListener }
            lifecycleScope.launch {
                var exported: File? = null
                try {
                    val file = withContext(Dispatchers.IO) {
                        val directory = File(cacheDir, "exports").apply { mkdirs() }
                        // Bound share-cache growth across repeated exports.
                        directory.listFiles().orEmpty().filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }.forEach { it.delete() }
                        File(directory, "demos-${System.currentTimeMillis()}.${format.extension}").also { target ->
                            exported = target
                            target.outputStream().use { output ->
                                if (format == GraphExportFormat.PNG) check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                                else PdfDocument().use { pdf ->
                                    val width = if (bitmap.width >= bitmap.height) 842 else 595
                                    val height = if (bitmap.width >= bitmap.height) 595 else 842
                                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
                                    page.canvas.drawColor(android.graphics.Color.WHITE)
                                    val scale = minOf((width - 36f) / bitmap.width, (height - 36f) / bitmap.height)
                                    val left = (width - bitmap.width * scale) / 2
                                    val top = (height - bitmap.height * scale) / 2
                                    page.canvas.drawBitmap(bitmap, null, RectF(left, top, left + bitmap.width * scale, top + bitmap.height * scale), Paint(Paint.FILTER_BITMAP_FLAG))
                                    pdf.finishPage(page)
                                    pdf.writeTo(output)
                                }
                            }
                        }
                    }
                    if (share) {
                        val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = format.mimeType; putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Share graph"))
                    } else {
                        pendingFile = file
                        exportKind = "Graph"
                        val name = model.state.value.title.replace(Regex("[^\\p{L}\\p{N} _.-]"), "_").take(80).ifBlank { "graph" }
                        chooseExport("$name.${format.extension}", format.mimeType)
                    }
                } catch (failure: Exception) {
                    pendingFile = null
                    exported?.delete()
                    model.showMessage("Graph export failed: ${failure.message}")
                } finally {
                    bitmap.recycle()
                    capturing = false
                }
            }
        }
        runCatching {
            if (surface != null) PixelCopy.request(surface, bitmap, listener, Handler(Looper.getMainLooper()))
            else PixelCopy.request(window, bounds, bitmap, listener, Handler(Looper.getMainLooper()))
        }.onFailure { capturing = false; bitmap.recycle(); model.showMessage("Graph export failed: ${it.message}") }
    }

    private fun findGlSurface(view: View): GLSurfaceView? {
        if (view is GLSurfaceView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findGlSurface(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun readDocument(stream: InputStream, limit: Int = 5_000_000): String = stream.bufferedReader().use { reader ->
        val contents = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            require(contents.length + count <= limit) { "The selected file is too large (limit ${limit / 1_000_000} MB)" }
            contents.append(buffer, 0, count)
        }
        contents.toString()
    }
}
