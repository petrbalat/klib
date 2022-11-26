package cz.petrbalat.klib.spring.jwt.mvc

import com.fasterxml.jackson.databind.ObjectMapper
import cz.petrbalat.klib.spring.jwt.HEADER_STRING
import cz.petrbalat.klib.spring.jwt.TOKEN_PREFIX
import io.jsonwebtoken.Jwts
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.filter.OncePerRequestFilter
import toKey
import java.net.URLDecoder
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * validate requests containing JWTS
 *
 * @see https://auth0.com/blog/implementing-jwt-authentication-on-spring-boot/
 */
open class JWTAuthorizationFilter(
    private val secret: String,
    private val mapper: ObjectMapper,
    private val clazz: Class<UserDetails>
) : OncePerRequestFilter() {

    private val deserializer = JacksonDeserializer(mapper)

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        fun readFromCookie() =
            req.cookies?.firstOrNull { it.name?.equals(HEADER_STRING, ignoreCase = true) == true }?.value?.let {
                URLDecoder.decode(it)
            }

        val token: String? = (req.getHeader(HEADER_STRING) ?: readFromCookie())
            ?.takeIf { it.startsWith(TOKEN_PREFIX) }
            ?.substringAfter(TOKEN_PREFIX)
        if (token == null) {
            SecurityContextHolder.getContext().authentication = null
            chain.doFilter(req, res)
            return
        }

        try {
            SecurityContextHolder.getContext().authentication = getAuthentication(token)
        } catch (th: Throwable) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
            return
        }

        chain.doFilter(req, res)
    }

    private fun getAuthentication(claimJws: String): Authentication? {
        // parse the token.
        val subject: String = Jwts.parserBuilder().setSigningKey(secret.toKey())
            .deserializeJsonWith(deserializer)
            .build()
            .parseClaimsJws(claimJws)
            .body?.subject ?: return null

        val user: UserDetails = mapper.readValue(subject, clazz)
        return UsernamePasswordAuthenticationToken(user, null, user.authorities)
    }

}
