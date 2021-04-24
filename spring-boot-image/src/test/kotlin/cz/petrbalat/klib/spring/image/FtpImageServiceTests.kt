package cz.petrbalat.klib.spring.image

import cz.petrbalat.klib.cdn77.testJpg
import cz.petrbalat.klib.jdk.http.fetchStream
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest(classes = [KlibImageTestApplication::class])
class FtpImageServiceTests {

    @Autowired
    private lateinit var service: ImageService

    @Test
    fun tesUploadImage() = runBlocking {
        val imageDto: ImageDto = service.uploadImage(testJpg, "test")

        assertEquals("test.jpg", imageDto.name)
        assertEquals("https://cdn.easyrent.cz/test/test.jpg", imageDto.url)
        assertEquals("test.webp", imageDto.webpName)
        assertEquals("https://cdn.easyrent.cz/test/test.webp", imageDto.webpUrl)
    }


    @Test
    fun testUpload() = runBlocking {
        val url: String = service.upload(testJpg.fetchStream(), name = "tupload.jpg", directory = "test")
        assertEquals("https://cdn.easyrent.cz/test/tupload.jpg", url)
    }


}
