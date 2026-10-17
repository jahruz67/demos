package dev.demos.graphing

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.demos.graphing.plot.GraphPlot
import dev.demos.graphing.plot.PlotSettings
import dev.demos.graphing.ui.CalculatorScreen
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            val current by model.state.collectAsStateWithLifecycle()
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF2D70D9), background = Color.White, surface = Color.White)) {
                Box(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.systemBars)) {
                    CalculatorScreen(current, model::onAction) { is3D, modifier ->
                        GraphPlot(
                            expressions = model.plotExpressions(current),
                            is3D = is3D,
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
                        CalculatorEffect.ExportImage -> captureGraph()
                    }
                }
            }
        }
    }

    private fun captureGraph() {
        val bounds = graphBounds
        if (bounds.width() <= 0 || bounds.height() <= 0) { model.showMessage("The graph is not ready to export"); return }
        val surface = if (model.state.value.is3D) findGlSurface(window.decorView) else null
        val bitmap = Bitmap.createBitmap(surface?.width ?: bounds.width(), surface?.height ?: bounds.height(), Bitmap.Config.ARGB_8888)
        val listener = PixelCopy.OnPixelCopyFinishedListener { result ->
            if (result != PixelCopy.SUCCESS) { bitmap.recycle(); model.showMessage("Could not capture graph ($result)"); return@OnPixelCopyFinishedListener }
            lifecycleScope.launch {
                runCatching {
                    val file = withContext(Dispatchers.IO) {
                        val directory = File(cacheDir, "exports").apply { mkdirs() }
                        File(directory, "demos-${System.currentTimeMillis()}.png").also { target ->
                            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            bitmap.recycle()
                        }
                    }
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(share, "Share graph image"))
                }.onFailure { model.showMessage("Image export failed: ${it.message}") }
            }
        }
        runCatching {
            if (surface != null) PixelCopy.request(surface, bitmap, listener, Handler(Looper.getMainLooper()))
            else PixelCopy.request(window, bounds, bitmap, listener, Handler(Looper.getMainLooper()))
        }.onFailure { bitmap.recycle(); model.showMessage("Image export failed: ${it.message}") }
    }

    private fun findGlSurface(view: View): GLSurfaceView? {
        if (view is GLSurfaceView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findGlSurface(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun readDocument(stream: InputStream): String = stream.bufferedReader().use { reader ->
        val contents = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            require(contents.length + count <= 5_000_000) { "The selected file exceeds 5 MB" }
            contents.append(buffer, 0, count)
        }
        contents.toString()
    }
}
