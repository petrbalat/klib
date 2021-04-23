package cz.petrbalat.klib.spring.image

import cz.petrbalat.klib.cdn77.testJpg
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
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
}
