package cz.petrbalat.klib.pdf

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.nio.file.Paths

internal class PdfServiceTest {

    private val service = PdfService(Paths.get("src/test/resources/fonts/calibri.ttf").toFile().absoluteFile)

    val html = Paths.get("src/test/resources/html/test.html").toFile().readText()

    @Test
    fun response() {
        val html = Paths.get("src/test/resources/html/test.html").toFile().readText()
        val response = service.response("test.pdf", "/orders/test.pdf", htmlContent = html)
        assertNotNull(response)
    }

    @Test
    fun pdfAsByteArray() {
        val response = service.pdfAsByteArray(html = html, "/test")
        assertNotNull(response)
    }
}
