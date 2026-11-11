package dev.demos.graphing.desktop

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO

/** A single image page, matching Android's graph PDF export. */
internal fun writeGraphPdf(image: BufferedImage, file: File) {
    val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
    val graphics = rgb.createGraphics()
    try { graphics.color = Color.WHITE; graphics.fillRect(0, 0, rgb.width, rgb.height); graphics.drawImage(image, 0, 0, null) }
    finally { graphics.dispose() }
    val jpeg = ByteArrayOutputStream().also { check(ImageIO.write(rgb, "jpeg", it)) }.toByteArray()
    val width = if (image.width >= image.height) 842 else 595
    val height = if (image.width >= image.height) 595 else 842
    val scale = minOf((width - 36.0) / image.width, (height - 36.0) / image.height)
    val w = image.width * scale; val h = image.height * scale
    val contents = String.format(Locale.US, "q %.4f 0 0 %.4f %.4f %.4f cm /Graph Do Q\n", w, h, (width - w) / 2, (height - h) / 2).toByteArray(Charsets.US_ASCII)
    val output = ByteArrayOutputStream()
    fun text(value: String) { output.write(value.toByteArray(Charsets.US_ASCII)) }
    val offsets = mutableListOf(0)
    fun objectAt(number: Int, body: () -> Unit) {
        offsets += output.size()
        text("$number 0 obj\n"); body(); text("\nendobj\n")
    }
    text("%PDF-1.4\n")
    objectAt(1) { text("<< /Type /Catalog /Pages 2 0 R >>") }
    objectAt(2) { text("<< /Type /Pages /Kids [3 0 R] /Count 1 >>") }
    objectAt(3) { text("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $width $height] /Resources << /XObject << /Graph 4 0 R >> >> /Contents 5 0 R >>") }
    objectAt(4) {
        text("<< /Type /XObject /Subtype /Image /Width ${image.width} /Height ${image.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n")
        output.write(jpeg); text("\nendstream")
    }
    objectAt(5) { text("<< /Length ${contents.size} >>\nstream\n"); output.write(contents); text("endstream") }
    val xref = output.size()
    text("xref\n0 6\n0000000000 65535 f \n")
    offsets.drop(1).forEach { text(String.format(Locale.US, "%010d 00000 n \n", it)) }
    text("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
    file.outputStream().use { output.writeTo(it) }
}
