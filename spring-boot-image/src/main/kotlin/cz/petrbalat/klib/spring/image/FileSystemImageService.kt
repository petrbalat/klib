package cz.petrbalat.klib.spring.image

import cz.petrbalat.klib.jdk.tryOrNull
import cz.petrbalat.klib.spring.image.cwebp.convertToWebP
import cz.petrbalat.klib.spring.image.image.ReadImageDto
import cz.petrbalat.klib.spring.image.image.readImageDto
import cz.petrbalat.klib.spring.image.image.resizeImageIfGreaterThan
import cz.petrbalat.klib.spring.image.image.writeToStream
import org.imgscalr.Scalr
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.*

class FileSystemImageService(
    private val path: Path,
    override val baseUrl: String,
) : ImageService {

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

        val destination: Path = createDestinationDirIfNotExist(directory)
        val file: Path = destination / dto.name
        val webpFile: Path = destination / "${dto.nameWithoutExtension}.webp"
        if (override) {
            tryOrNull {
                file.deleteIfExists()
            }
            tryOrNull {
                webpFile.deleteIfExists()
            }
        }

        //write origin image
        val image = dto.image.resizeImageIfGreaterThan(maxPx, mode)
        if (image == null) { // write origin
            file.outputStream().use {
                it.write(bytes)
                it.flush()
            }
        } else {
            image.writeToStream(dto.extension, file.outputStream())
        }

        //write webp
        dto.image.convertToWebP(webpFile.outputStream())

        val baseUrl = "$baseUrl/$directory"
        return ImageDto("$baseUrl/${dto.name}", webpUrl = "$baseUrl/${webpFile.name}")
    }

    override suspend fun upload(stream: InputStream, name: String, directory: String, override: Boolean): String {
        val destination: Path = createDestinationDirIfNotExist(directory)
        val file: Path = destination / name

        stream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return "$baseUrl/$directory/$name"
    }

    private fun createDestinationDirIfNotExist(directory: String): Path  {
        val destination: Path = path / directory
        if (!destination.exists()) {
            destination.createDirectory()
        }
        return destination
    }

}
