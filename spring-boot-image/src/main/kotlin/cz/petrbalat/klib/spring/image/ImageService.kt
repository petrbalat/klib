package cz.petrbalat.klib.spring.image

import org.imgscalr.Scalr
import java.io.InputStream

interface ImageService {

    val baseUrl: String

    suspend fun uploadImage(
        stream: InputStream, // uri of image. can by http(s):// or file:/
        name: String,
        directory: String, // destination directory
        override: Boolean = true, //override destination files
        maxPx: Int = 1920,
        mode: Scalr.Mode = Scalr.Mode.FIT_TO_WIDTH,
    ): ImageDto

    /**
     * return url
     */
    suspend fun upload(
        stream: InputStream,
        name: String,
        directory: String, // destination directory
        override: Boolean = true, //override destination files
    ): String

    /**
     * without resize
     */
    suspend fun toWebpAndUploadImage(
        stream: InputStream,
        name: String, // file name
        directory: String, // destination directory
        override: Boolean = true,  //override destination files
    ): String
}
