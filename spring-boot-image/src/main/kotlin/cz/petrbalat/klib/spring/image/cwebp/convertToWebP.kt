package cz.petrbalat.klib.spring.image.cwebp

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriter
import javax.imageio.stream.MemoryCacheImageOutputStream


/**
 * convert image to webp
 */
fun BufferedImage.convertToWebP(outputStream: OutputStream) {
    // Obtain a WebP ImageWriter instance
    val writer: ImageWriter = ImageIO.getImageWritersByMIMEType("image/webp").next()

// Configure encoding parameters
//    val writeParam = WebPWriteParam(writer.locale).apply {
//        compressionMode = ImageWriteParam.MODE_DISABLED
//        compressionType = "Lossy"
//        compressionQuality = quality
//    alphaQuality = (quality * 100).roundToInt()
//        this.method = method
//    }

    MemoryCacheImageOutputStream(outputStream).use {
        writer.output = it
// Encode
        val iioImage = IIOImage(this, null, null)
        writer.write(null, iioImage, null /* writeParam */)
    }

}

/**
 * convert to webp in ByteArray
 */
fun BufferedImage.convertToWebP(): ByteArray = ByteArrayOutputStream().use {
    convertToWebP(it)
    it.toByteArray()
}
