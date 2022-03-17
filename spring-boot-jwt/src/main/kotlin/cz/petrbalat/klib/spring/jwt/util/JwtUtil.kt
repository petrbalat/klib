package cz.petrbalat.klib.spring.jwt.util

import com.fasterxml.jackson.databind.ObjectMapper
import cz.petrbalat.klib.jdk.datetime.now
import cz.petrbalat.klib.jdk.datetime.toDate
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.security.Key
import java.time.Duration
import java.util.*


class JwtUtil(
    secret: String,
    private val tokenClass: Class<UserDetails>,
    private val expirationDateTime: Duration?,
    private val mapper: ObjectMapper,
) {

    private var key: Key = Keys.hmacShaKeyFor(secret.toByteArray())

    fun getAllClaimsFromToken(token: String): Claims =
        Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).body

    fun getUserFromToken(token: String): UserDetails {
        val subject = getAllClaimsFromToken(token).subject
        return mapper.readValue(subject, tokenClass)
    }

    fun getExpirationDateFromToken(token: String): Date = getAllClaimsFromToken(token).expiration

    fun generateToken(user: UserDetails, authorities: Collection<GrantedAuthority>): String {
        val claims: MutableMap<String, Any?> = HashMap()
        claims["role"] = authorities
        return doGenerateToken(claims, user)
    }


    private fun isTokenExpired(token: String): Boolean {
        val expiration: Date = getExpirationDateFromToken(token)
        return expiration.before(Date())
    }

    private fun doGenerateToken(claims: Map<String, Any?>, user: UserDetails): String {
        val duration: Duration = expirationDateTime ?: Duration.ofDays(7)
        val expirationDate = now.plus(duration).toDate()
        return Jwts.builder()
            .setClaims(claims)
            .setSubject(mapper.writeValueAsString(user))
            .setIssuedAt(Date())
            .setExpiration(expirationDate)
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Boolean = !isTokenExpired(token)

}
