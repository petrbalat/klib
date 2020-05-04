package cz.petrbalat.klib.spring.jwt

import com.fasterxml.jackson.databind.ObjectMapper
import cz.petrbalat.klib.spring.jwt.mvc.JWTAuthenticationFilter
import cz.petrbalat.klib.spring.jwt.mvc.JWTAuthorizationFilter
import cz.petrbalat.klib.spring.jwt.mvc.PrepareUserToJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.core.userdetails.UserDetails


/**
 * @author Petr Balat
 *
 */
@Configuration(proxyBeanMethods = false)
class JwtSecurityConfiguration(private @Value("\${jwt.token.secret}") val secret: String,
                               private val mapper: ObjectMapper) {

    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        logger.info("creating jwt filters")
    }

    @Bean
    @ConditionalOnMissingBean
    fun jwtAuthenticationFilter(authenticationManagerBuilder: AuthenticationManagerBuilder,
                                @Autowired(required = false) userPrepare: PrepareUserToJson<UserDetails>?): JWTAuthenticationFilter {
        logger.info("creating default JWTAuthenticationFilter")
        return JWTAuthenticationFilter(secret, mapper, authenticationManagerBuilder, userPrepare)
    }

    @Bean
    @ConditionalOnMissingBean
    fun jwtAuthorizationFilter(@Value("\${jwt.token.class}") clazz: Class<UserDetails>): JWTAuthorizationFilter {
        logger.info("creating default JWTAuthorizationFilter")
        return JWTAuthorizationFilter(secret, mapper, clazz)
    }
}

