package dev.demos.graphing.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import javax.imageio.ImageIO

internal object WindowsImages {
    fun read(source: String): BufferedImage? = runCatching {
        val uri = URI(source)
        require(uri.scheme == "file") { "Choose a local image file" }
        ImageIO.createImageInputStream(File(uri)).use { input ->
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return@use null
            try {
                reader.input = input
                val sample = ((maxOf(reader.getWidth(0), reader.getHeight(0)) + 1599) / 1600).coerceAtLeast(1)
                val parameters = reader.defaultReadParam.apply { setSourceSubsampling(sample, sample, 0, 0) }
                reader.read(0, parameters)
            } finally { reader.dispose() }
        }
    }.getOrNull()

    fun decode(source: String): ImageBitmap? = read(source)?.toComposeImageBitmap()
}
