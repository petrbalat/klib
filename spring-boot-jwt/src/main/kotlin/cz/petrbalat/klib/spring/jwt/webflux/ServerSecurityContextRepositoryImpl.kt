package cz.petrbalat.klib.spring.jwt.webflux

import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.web.server.context.ServerSecurityContextRepository
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

internal class ServerSecurityContextRepositoryImpl(
    private val authenticationManager: ReactiveAuthenticationManager,
    private val authHeaderBearer: String = "Bearer ",
) : ServerSecurityContextRepository {

    override fun save(exchange: ServerWebExchange?, context: SecurityContext?): Mono<Void> = TODO("Not supported yet")

    override fun load(exchange: ServerWebExchange): Mono<SecurityContext> {
        val auth: String? = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        return Mono.justOrEmpty(auth)
            .filter { authHeader -> authHeader.startsWith(authHeaderBearer) }
            .flatMap { authHeader ->
                val authToken: String = authHeader.substringAfter(authHeaderBearer)
                val token = UsernamePasswordAuthenticationToken(authToken, authToken)
                this.authenticationManager.authenticate(token).map { SecurityContextImpl(it) }
            }
    }
}
