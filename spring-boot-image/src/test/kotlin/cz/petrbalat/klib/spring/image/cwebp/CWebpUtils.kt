package cz.petrbalat.klib.spring.image.cwebp

import cz.petrbalat.klib.cdn77.testJpgFile
import cz.petrbalat.klib.spring.image.image.readImage
import kotlinx.coroutines.runBlocking
import java.io.FileOutputStream
import org.junit.jupiter.api.condition.DisabledIfSystemProperty
import kotlin.test.Test
import kotlin.test.assertEquals

class CWebpUtils {

    @Test
    @DisabledIfSystemProperty(named = "os.arch", matches = "aarch64|arm64", disabledReason = "webp-imageio nativní knihovna je jen pro x86_64")
    fun testConvertToWebp() = runBlocking {
        val webp: ByteArray = testJpgFile.readImage().convertToWebP()
        assertEquals(211632, webp.size)

        FileOutputStream("test.webp").use {
            it.write(webp)
        }
    }


}
