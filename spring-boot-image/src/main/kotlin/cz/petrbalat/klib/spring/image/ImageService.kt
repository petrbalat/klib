package cz.petrbalat.klib.spring.image

import org.imgscalr.Scalr
import java.net.URI

interface ImageService {

    suspend fun uploadImage(
        uri: URI, // uri of image. can by http(s):// or file:/
        directory: String, // destination directory
        override: Boolean = true, //override destination files
        maxPx: Int = 1920,
        mode: Scalr.Mode = Scalr.Mode.FIT_TO_WIDTH,
    ): ImageDto
}

