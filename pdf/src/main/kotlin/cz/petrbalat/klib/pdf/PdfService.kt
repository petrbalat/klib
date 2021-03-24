package cz.petrbalat.klib.pdf

import com.itextpdf.text.pdf.BaseFont
import org.springframework.http.ResponseEntity
import org.xhtmlrenderer.pdf.ITextRenderer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

/**
 * Pro konverzi html na pdf. A pro stažení volané z controlleru.
 *
 * @author Luděk Křivánek, Petr Balat,
 */
class PdfService(
    private val fontPath: File,
) {

    init {
        assert(fontPath.exists())
    }

    /**
     * pro respon v akci ze spring controllerů
     *
     * @param fileName Název souboru pdf
     * @param baseUrl Název souboru pdf
     * @param htmlContent html k vygenerování
     */
    fun response(fileName: String, baseUrl: String, htmlContent: String): ResponseEntity<ByteArray> {
        val body: ByteArray = pdfAsByteArray(htmlContent, baseUrl)
        return ResponseEntity
            .ok()
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Headers", "Content-Type")
            .header("Content-Disposition", "filename=$fileName")
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("Pragma", "no-cache")
            .header("Expires", "0")
            .body(body)
    }

    fun pdfAsByteArray(html: String, baseUrl: String): ByteArray = ByteArrayOutputStream().use {
        it.writePdf(html, baseUrl)
        it.toByteArray()
    }

    private fun OutputStream.writePdf(html: String, baseUrl: String) {
        val renderer = ITextRenderer().apply {
            // přidání písma kvůli české diakritice
            fontResolver.addFont(fontPath.absolutePath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED)
            // generování
            setDocumentFromString(html, baseUrl)
            layout()
        }
        renderer.createPDF(this)
    }
}
