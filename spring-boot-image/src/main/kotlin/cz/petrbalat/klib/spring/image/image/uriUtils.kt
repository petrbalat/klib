package cz.petrbalat.klib.spring.image.image

import cz.petrbalat.klib.jdk.string.removeDiakritiku
import cz.petrbalat.klib.jdk.string.removeNotAlowedInFileName
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import javax.imageio.ImageIO

/**
 * donwload image form uri and transform to BufferedImage
 */
fun InputStream.readImageDto(name: String): ReadImageDto = use { stream ->
    val image: BufferedImage = stream.readImage()
    val nameWithoutExtension = removeNotAlowedInFileName(name.substringBeforeLast(".").lowercase()).removeDiakritiku()
    val extension = name.substringAfterLast(".")
    ReadImageDto(nameWithoutExtension = nameWithoutExtension, extension = extension, image)
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
