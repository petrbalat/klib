package cz.petrbalat.klib.spring.image.image

import cz.petrbalat.klib.jdk.tryOrNull
import org.imgscalr.Scalr
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import javax.imageio.ImageIO
import javax.imageio.ImageWriter
import javax.imageio.stream.MemoryCacheImageOutputStream

fun BufferedImage.resizeImage(
    targetSize: Int,
    mode: Scalr.Mode = Scalr.Mode.FIT_TO_WIDTH,
    method: Scalr.Method = Scalr.Method.QUALITY
): BufferedImage {
    return Scalr.resize(this, method, mode, targetSize)
}

fun BufferedImage.writeToStream(extension: String) = ByteArrayOutputStream().also { stream ->
    val extension = if (extension.lowercase() == "jpg") "jpeg" else extension
    fun findOrNull(type: String) = tryOrNull {
        ImageIO.getImageWritersByMIMEType(type).next()
    }

    val writer: ImageWriter = findOrNull("image/$extension") ?: findOrNull("image/png")!!
    writer.output = MemoryCacheImageOutputStream(stream)
    writer.write(this)
}

fun BufferedImage.toByteArray(extension: String):ByteArray = this.writeToStream(extension).use {
    it.toByteArray()
}

fun BufferedImage.writeToStream(extension: String, out: OutputStream) {
    this.writeToStream(extension).use { outputStream ->
        outputStream.writeTo(out)
    }
}

fun BufferedImage.resizeImageIfGreaterThan(maxPx: Int, mode: Scalr.Mode = Scalr.Mode.FIT_TO_WIDTH): BufferedImage? {
    if (mode == Scalr.Mode.FIT_TO_WIDTH && width > maxPx) {
        return resizeImage(maxPx, mode)
    } else if (mode == Scalr.Mode.FIT_TO_HEIGHT && height > maxPx) {
        return resizeImage(maxPx, mode)
    }

    return null
}
