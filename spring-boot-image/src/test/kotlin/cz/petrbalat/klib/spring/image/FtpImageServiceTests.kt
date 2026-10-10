package cz.petrbalat.klib.spring.image

import cz.petrbalat.klib.cdn77.testJpg
import cz.petrbalat.klib.jdk.http.fetchStream
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.junit.jupiter.api.condition.DisabledIfSystemProperty
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest(classes = [KlibImageTestApplication::class])
class FtpImageServiceTests {

    @Autowired
    private lateinit var service: ImageService

    @Test
    @DisabledIfSystemProperty(named = "os.arch", matches = "aarch64|arm64", disabledReason = "webp-imageio nativní knihovna je jen pro x86_64")
    fun tesUploadImage() = runBlocking {
        val imageDto: ImageDto = service.uploadImage(testJpg, "test")

        assertEquals("test.jpg", imageDto.name)
        assertEquals("https://test.cdn77.com/test/test.jpg", imageDto.url)
        assertEquals("test.webp", imageDto.webpName)
        assertEquals("https://test.cdn77.com/test/test.webp", imageDto.webpUrl)
    }


    @Test
    fun testUpload() = runBlocking {
        val url: String = service.upload(testJpg.fetchStream(), name = "tupload.jpg", directory = "test")
        assertEquals("https://test.cdn77.com/test/tupload.jpg", url)
    }


}
