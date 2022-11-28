package cz.petrbalat.klib.spring.image

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.io.path.Path

/**
 * @author Petr Balat
 *
 */
@AutoConfiguration
@ConditionalOnProperty("klib.image.host")
@kotlin.ExperimentalStdlibApi
class ImageConfig(
    @Value("\${klib.image.host}") private val host: String,
    @Value("\${klib.image.baseUrl}") private val baseUrl: String,

    @Value("\${klib.image.user}") private val user: String?,
    @Value("\${klib.image.password}") private val password: String?,
) {

    @Bean
    fun template(): ImageService {
        if (user.isNullOrBlank() || password.isNullOrBlank()) {
            val path = Path(host)
            return FileSystemImageService(path, baseUrl = baseUrl)
        }

        return FtpImageService(host, user = user, password = password, baseUrl = baseUrl)
    }

}

