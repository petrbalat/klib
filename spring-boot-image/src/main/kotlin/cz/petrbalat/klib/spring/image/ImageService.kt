package cz.petrbalat.klib.spring.image

import cz.petrbalat.klib.jdk.http.fetchStream
import org.imgscalr.Scalr
import java.io.File
import java.io.InputStream
import java.net.URI

interface ImageService {

    suspend fun uploadImage(
        uri: URI, // uri of image. can by http(s):// or file:/
        directory: String,// destination directory
        override: Boolean = true,  //override destination files
        maxPx: Int = 1920,
        mode: Scalr.Mode = Scalr.Mode.FIT_TO_WIDTH,
    ): ImageDto = uploadImage(uri.fetchStream(), File(uri).name, directory, override, maxPx, mode)

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
        stream: InputStream, // uri of image. can by http(s):// or file:/
        name: String,
        directory: String, // destination directory
        override: Boolean = true, //override destination files
    ): String
}

