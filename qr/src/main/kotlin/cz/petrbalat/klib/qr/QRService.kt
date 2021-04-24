package cz.petrbalat.klib.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.CharacterSetECI
import com.google.zxing.qrcode.QRCodeWriter
import cz.petrbalat.klib.jdk.io.mkdirIfNotExist
import cz.petrbalat.klib.jdk.string.removeNotAlowedInFileName
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * @author Petr Balat
 */
class QRService {

    /**
     * vygeneruje jpg
     */
    @OptIn(ExperimentalPathApi::class)
    fun generateQrImage(
        data: String, directory: Path, fileName: String = "qr",
        width: Int = 640, height: Int = 480,
    ): Path {
        directory.toFile().mkdirIfNotExist()
        assert(directory.exists() && directory.isDirectory())

        val bitMatrix: BitMatrix = bitMatrix(data, width, height)

        //do názvu přidám hash + random kvůli přegenerování a cache obrázků
        val fileName: String = removeNotAlowedInFileName(fileName)
        val path: Path = directory.resolve("$fileName.jpg")
        MatrixToImageWriter.writeToPath(bitMatrix, "JPG", path)
        return path
    }

    /**
     * vygeneruje jpg
     */
    @OptIn(ExperimentalPathApi::class)
    fun generateQrImage(
        data: String,
        width: Int = 640, height: Int = 480,
        outputStream: OutputStream,
    ) {
        val bitMatrix: BitMatrix = bitMatrix(data, width, height)
        //do názvu přidám hash + random kvůli přegenerování a cache obrázků
        MatrixToImageWriter.writeToStream(bitMatrix, "JPG", outputStream)
    }

    private fun bitMatrix(data: String, width: Int, height: Int): BitMatrix {
        val encode = mapOf(EncodeHintType.CHARACTER_SET to CharacterSetECI.UTF8)
        val qrCodeWriter = QRCodeWriter()
        return qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, width, height, encode)
    }

}
