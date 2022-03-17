package cz.petrbalat.klib.spring.jwt.webflux

import com.fasterxml.jackson.databind.ObjectMapper
import cz.petrbalat.klib.spring.jwt.util.JwtUtil
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.web.server.context.ServerSecurityContextRepository
import org.springframework.web.reactive.config.WebFluxConfigurer
import java.time.Duration


/**
 * @author Petr Balat
 *
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(WebFluxConfigurer::class)
class JwtWebFluxSecurityConfiguration {

    @Bean
    fun jwtUtil(
        @Value("\${jwt.token.secret}") secret: String,
        @Value("\${jwt.token.class}") tokenClass: Class<UserDetails>,
        @Value("\${jwt.token.expirationDateTime:#{null}}") expirationDateTime: Duration?,
        mapper: ObjectMapper,
    ) = JwtUtil(secret, tokenClass, expirationDateTime, mapper)

    @Bean
    fun reactiveAuthenticationManager(jwtUtil: JwtUtil): ReactiveAuthenticationManager = KlibAuthenticationManager(jwtUtil)

    @Bean
    fun serverRepository(authenticationManager: ReactiveAuthenticationManager): ServerSecurityContextRepository
    = ServerSecurityContextRepositoryImpl(authenticationManager)

}
