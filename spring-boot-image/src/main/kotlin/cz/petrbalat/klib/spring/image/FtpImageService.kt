package cz.petrbalat.klib.spring.image

import cz.petrbalat.klib.ftp.use
import cz.petrbalat.klib.jdk.tryOrNull
import cz.petrbalat.klib.spring.image.cwebp.convertToWebP
import cz.petrbalat.klib.spring.image.image.ReadImageDto
import cz.petrbalat.klib.spring.image.image.readImageDto
import cz.petrbalat.klib.spring.image.image.resizeImageIfGreaterThan
import cz.petrbalat.klib.spring.image.image.toByteArray
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
    private val baseUrl: String,
) : ImageService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun uploadImage(
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
        useFtpClient { client ->
            logger.info("makeDirectory ${client.makeDirectory(directory)}")

            val dir = client.changeWorkingDirectory(directory)
            logger.info("Change directory $directory $dir")

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

    override suspend fun upload(stream: InputStream, name: String, directory: String, override: Boolean): String {
        useFtpClient { client ->
            logger.info("makeDirectory ${client.makeDirectory(directory)}")

            val dir = client.changeWorkingDirectory(directory)
            logger.info("Change directory $directory $dir")

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

    private fun useFtpClient(block: (FTPClient) -> Unit) {
        FTPClient().use(logger, hostname = host, user = user, password = password) {
            it.changeWorkingDirectory("www")
            block(it)
        }
    }

}
