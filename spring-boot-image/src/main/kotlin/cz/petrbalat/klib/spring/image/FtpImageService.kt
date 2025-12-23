package cz.petrbalat.klib.spring.image

import cz.petrbalat.klib.ftp.use
import cz.petrbalat.klib.jdk.tryOrNull
import cz.petrbalat.klib.spring.image.cwebp.convertToWebP
import cz.petrbalat.klib.spring.image.image.ReadImageDto
import cz.petrbalat.klib.spring.image.image.readImageDto
import cz.petrbalat.klib.spring.image.image.resizeImageIfGreaterThan
import cz.petrbalat.klib.spring.image.image.toByteArray
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.imgscalr.Scalr
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.InputStream

class FtpImageService(
    private val host: String,
    private val user: String,
    private val password: String,
    override val baseUrl: String,
) : ImageService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun uploadImage(
        stream: InputStream,
        name: String,
        directory: String,
        override: Boolean,
        maxPx: Int,
        mode: Scalr.Mode
    ): ImageDto {
        val bytes = stream.readBytes()
        val dto: ReadImageDto = ByteArrayInputStream(bytes).readImageDto(name)

        val fileName = dto.name
        val webFileName = "${dto.nameWithoutExtension}.webp"

        //convert to webp
        val image: BufferedImage? = dto.image.resizeImageIfGreaterThan(maxPx, mode)
        val imageArray = image?.toByteArray(dto.extension) ?: bytes
        val webpByteArray = dto.image.convertToWebP()

        // upload origin
        useFtpClient(directory) { client ->
            if (override) {
                tryOrNull {
                    client.deleteFile(fileName)
                }
                tryOrNull {
                    client.deleteFile(webFileName)
                }
            }

            //upload origin image
            client.storeFile(fileName, ByteArrayInputStream(imageArray))
            client.storeFile(webFileName, ByteArrayInputStream(webpByteArray))
        }

        val baseUrl = "$baseUrl/$directory"
        return ImageDto("$baseUrl/$fileName", webpUrl = "$baseUrl/$webFileName")
    }

    override fun upload(stream: InputStream, name: String, directory: String, override: Boolean): String {
        useFtpClient(directory) { client ->
            directory.split("/").forEach { dir ->
                logger.info("makeDirectory ${client.makeDirectory(dir)}")

                val changeDir = client.changeWorkingDirectory(dir)
                logger.info("Change directory $dir $changeDir")
            }

            if (override) {
                tryOrNull {
                    client.deleteFile(name)
                }
            }

            stream.use {
                client.storeFile(name, it)
            }
        }

        return "$baseUrl/$directory/$name"
    }

    override fun toWebpAndUploadImage(
        stream: InputStream,
        name: String,
        directory: String,
        override: Boolean
    ): String {
        val dto: ReadImageDto = stream.readImageDto(name)

        val webFileName = "${dto.nameWithoutExtension}.webp"

        //convert to webp
        val webpByteArray = dto.image.convertToWebP()

        // upload origin
        useFtpClient(directory) { client ->
            if (override) {
                tryOrNull {
                    client.deleteFile(webFileName)
                }
            }

            //upload origin image
            client.storeFile(webFileName, ByteArrayInputStream(webpByteArray))
        }

        return upload(stream, name, directory, override)
    }

    private fun useFtpClient(directory: String? = null, block: (FTPClient) -> Unit) {
        FTPClient().use(logger, hostname = host, user = user, password = password, fileType = FTP.BINARY_FILE_TYPE) {
            it.changeWorkingDirectory("www")

            if (directory != null) {
                logger.info("makeDirectory ${it.makeDirectory(directory)}")
                val dir = it.changeWorkingDirectory(directory)
                logger.info("Change directory $directory $dir")
            }

            block(it)
        }
    }

}
