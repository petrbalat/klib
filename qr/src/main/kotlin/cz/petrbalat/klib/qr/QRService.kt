package cz.petrbalat.klib.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.CharacterSetECI
import com.google.zxing.qrcode.QRCodeWriter
import cz.petrbalat.klib.jdk.io.mkdirIfNotExist
import cz.petrbalat.klib.jdk.string.removeNotAlowedInFileName
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

        val encode = mapOf(EncodeHintType.CHARACTER_SET to CharacterSetECI.UTF8)
        val qrCodeWriter = QRCodeWriter()
        val bitMatrix: BitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, width, height, encode)

        //do názvu přidám hash + random kvůli přegenerování a cache obrázků
        val fileName: String = removeNotAlowedInFileName(fileName)
        val path: Path = directory.resolve("$fileName.jpg")
        MatrixToImageWriter.writeToPath(bitMatrix, "JPG", path)
        return path
    }

}
