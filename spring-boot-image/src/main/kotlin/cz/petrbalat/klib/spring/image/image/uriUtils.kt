package cz.petrbalat.klib.spring.image.image

import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import java.net.URI
import javax.imageio.ImageIO

/**
 * donwload image form uri and transform to BufferedImage
 */
fun InputStream.readImageDto(uri: URI) = use { stream ->
    val file = File(uri)
    val image: BufferedImage = stream.readImage()
    ReadImageDto(nameWithoutExtension = file.nameWithoutExtension, extension = file.extension, image)
}

data class ReadImageDto(val nameWithoutExtension: String, val extension: String, val image: BufferedImage) {
    val name: String get() = "$nameWithoutExtension.$extension"
}

fun File.readImage(): BufferedImage = this.inputStream().use {
    it.readImage()
}

fun InputStream.readImage(): BufferedImage = use {
    ImageIO.read(it)
}
