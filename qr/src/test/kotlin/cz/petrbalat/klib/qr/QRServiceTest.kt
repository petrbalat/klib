package cz.petrbalat.klib.qr

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path

internal class QRServiceTest {

    private val service = QRService()

    @Test
    fun generateQrImage() {
        val directory = Path.of("c:\\Users\\balat\\Downloads\\meety\\")
        service.generateQrImage("https://www.easyrent.cz", directory)
    }
}
