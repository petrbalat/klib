package cz.petrbalat.klib.spring.image.cwebp

import cz.petrbalat.klib.cdn77.testJpgFile
import cz.petrbalat.klib.spring.image.image.readImage
import kotlinx.coroutines.runBlocking
import java.io.FileOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class CWebpUtils {

    @Test
    fun testConvertToWebp() = runBlocking {
        val webp: ByteArray = testJpgFile.readImage().convertToWebP()
        assertEquals(211632, webp.size)

        FileOutputStream("test.webp").use {
            it.write(webp)
        }
    }


}
