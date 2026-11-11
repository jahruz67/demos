package dev.demos.graphing.desktop

import dev.demos.graphing.CalculatorController
import dev.demos.graphing.ui.GraphExportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.image.BufferedImage
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.JOptionPane

internal class WindowsFiles(private val parent: Component, private val model: CalculatorController) {
    private fun choose(saveName: String? = null): File? {
        val chooser = JFileChooser().apply { if (saveName != null) selectedFile = File(saveName) }
        val selected = if (saveName == null) chooser.showOpenDialog(parent) else chooser.showSaveDialog(parent)
        if (selected != JFileChooser.APPROVE_OPTION) return null
        val file = chooser.selectedFile
        if (saveName != null && file.exists() && JOptionPane.showConfirmDialog(parent,
                "Replace ${file.name}?", "Replace file", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return null
        return file
    }

    suspend fun openText(limit: Int): String? {
        val file = choose() ?: return null
        return withContext(Dispatchers.IO) {
            require(file.length() <= limit) { "This file is too large" }
            file.inputStream().use { input ->
                val bytes = input.readNBytes(limit + 1)
                require(bytes.size <= limit) { "This file is too large" }
                bytes.toString(Charsets.UTF_8)
            }
        }
    }

    suspend fun saveText(name: String, contents: String) {
        val file = choose(name) ?: return
        withContext(Dispatchers.IO) { file.writeText(contents, Charsets.UTF_8) }
        model.showMessage("File exported")
    }

    suspend fun importImage() {
        val file = choose() ?: return
        val imported = withContext(Dispatchers.IO) {
            val image = WindowsImages.read(file.toURI().toString()) ?: error("Choose a supported image")
            val directory = File(WindowsRuntime.dataDirectory, "graph-images").apply { check(mkdirs() || isDirectory) }
            val destination = File(directory, "${UUID.randomUUID()}.png")
            check(ImageIO.write(image, "png", destination))
            destination.toURI().toString() to image.width.toDouble() / image.height
        }
        model.importImage(imported.first, file.nameWithoutExtension, imported.second)
    }

    suspend fun saveGraph(image: BufferedImage, format: GraphExportFormat) {
        val name = model.state.value.title.replace(Regex("[^\\p{L}\\p{N} _.-]"), "_").take(80).ifBlank { "graph" }
        val file = choose("$name.${format.extension}") ?: return
        withContext(Dispatchers.IO) {
            if (format == GraphExportFormat.PNG) check(ImageIO.write(image, "png", file))
            else writeGraphPdf(image, file)
        }
        model.showMessage("Graph exported")
    }
}
