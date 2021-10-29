package cz.petrbalat.klib.spring.image

import cz.petrbalat.klib.jdk.http.fetchStream
import org.imgscalr.Scalr
import java.io.File
import java.net.URI


suspend fun ImageService.uploadImage(
    uri: URI, // uri of image. can by http(s):// or file:/
    directory: String,// destination directory
    name: String = File(uri).name,
    override: Boolean = true,  //override destination files
    maxPx: Int = 1920,
    mode: Scalr.Mode = Scalr.Mode.FIT_TO_WIDTH,
): ImageDto {
    return uploadImage(uri.fetchStream(), name, directory, override, maxPx, mode)
}
