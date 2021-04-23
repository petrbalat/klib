package cz.petrbalat.klib.spring.image.image

import cz.petrbalat.klib.cdn77.testJpgFile
import cz.petrbalat.klib.jdk.http.fetchStream
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageUtils {

    @Test
    fun testReadImage() = runBlocking {
        val image: BufferedImage = testJpgFile.readImage()
        assertEquals(1, image.type)
    }

    @Test
    fun testUriReadImage() = runBlocking {
        val uri = testJpgFile.toURI()
        val dto = uri.fetchStream().readImageDto(uri)
        assertEquals("test.jpg", dto.name)
    }

    @Test
    fun testwriteToStream() = runBlocking {
        val image: BufferedImage = testJpgFile.readImage()

        val file = File("testx.jpg").apply {
            this.delete()
        }
        image.resizeImage(1200).writeToStream(testJpgFile.extension, FileOutputStream(file))

        assertTrue(file.exists())
    }


}
