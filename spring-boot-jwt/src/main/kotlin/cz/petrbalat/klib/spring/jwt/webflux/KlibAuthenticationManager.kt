package cz.petrbalat.klib.spring.jwt.webflux

import cz.petrbalat.klib.spring.jwt.util.JwtUtil
import io.jsonwebtoken.Claims
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import reactor.core.publisher.Mono

class KlibAuthenticationManager(private val jwtUtil: JwtUtil) : ReactiveAuthenticationManager {

    override fun authenticate(authentication: Authentication): Mono<Authentication> {
        val authToken: String = authentication.credentials.toString()
        return Mono.fromSupplier { jwtUtil.validateToken(authToken) }
            .onErrorReturn(false)
            .filter { it }
            .switchIfEmpty(Mono.empty())
            .map {
                val claims: Claims = jwtUtil.getAllClaimsFromToken(authToken)
                val rolesMap = claims["role"] as List<Map<String,String>>
                val authorities: List<SimpleGrantedAuthority> = rolesMap.map { it["authority"] }.map {  SimpleGrantedAuthority(it) }
                val user: UserDetails = jwtUtil.getUserFromToken(authToken)
                UsernamePasswordAuthenticationToken(user, null, authorities)
            }
    }
}
