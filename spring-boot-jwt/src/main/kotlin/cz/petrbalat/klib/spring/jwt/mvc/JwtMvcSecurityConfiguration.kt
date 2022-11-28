package cz.petrbalat.klib.spring.jwt.mvc

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.core.userdetails.UserDetails
import java.time.Duration
import jakarta.servlet.Servlet
import org.springframework.boot.autoconfigure.AutoConfiguration


/**
 * @author Petr Balat
 *
 */
@AutoConfiguration
@ConditionalOnClass(Servlet::class)
class JwtMvcSecurityConfiguration(
    private @Value("\${jwt.token.secret}") val secret: String,
    private val mapper: ObjectMapper,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        logger.info("creating jwt filters")
    }

    @Bean
    @ConditionalOnMissingBean
    fun jwtAuthenticationFilter(
        authenticationManagerBuilder: AuthenticationManagerBuilder,
        @Value("\${jwt.token.expirationDateTime:#{null}}") expirationDateTime: Duration?,
        @Autowired(required = false) userPrepare: PrepareUserToJson<UserDetails>?
    ): JWTAuthenticationFilter {
        logger.info("creating default JWTAuthenticationFilter")
        val duration: Duration = expirationDateTime ?: Duration.ofDays(7)
        return JWTAuthenticationFilter(secret, mapper, authenticationManagerBuilder, duration, userPrepare)
    }

    @Bean
    @ConditionalOnMissingBean
    fun jwtAuthorizationFilter(@Value("\${jwt.token.class}") clazz: Class<UserDetails>): JWTAuthorizationFilter {
        logger.info("creating default JWTAuthorizationFilter")
        return JWTAuthorizationFilter(secret, mapper, clazz)
    }

}

