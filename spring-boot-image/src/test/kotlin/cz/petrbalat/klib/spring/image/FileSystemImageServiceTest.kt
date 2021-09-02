package cz.petrbalat.klib.spring.image

import cz.petrbalat.klib.cdn77.testJpg
import cz.petrbalat.klib.jdk.http.fetchStream
import kotlinx.coroutines.runBlocking
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals

internal class FileSystemImageServiceTest {

    private var service = FileSystemImageService(Path("c:\\home\\easyrent\\resources"), "/resources")

    @Test
    fun uploadImage() = runBlocking {
        val imageDto: ImageDto = service.uploadImage(testJpg, "test")

        assertEquals("test.jpg", imageDto.name)
        assertEquals("/resources/test/test.jpg", imageDto.url)
        assertEquals("test.webp", imageDto.webpName)
        assertEquals("/resources/test/test.webp", imageDto.webpUrl)
    }

    @Test
    fun toWebpAndUploadImage() = runBlocking {
        val url = service.toWebpAndUploadImage(testJpg.fetchStream(), "test.jpg", "test-dir")

        assertEquals("/resources/test-dir/test.webp", url)
    }

    @Test
    fun testUpload() = runBlocking {
        val url: String = service.upload(testJpg.fetchStream(), name = "tupload.jpg", directory = "test")
        assertEquals("/resources/test/tupload.jpg", url)
    }
}
