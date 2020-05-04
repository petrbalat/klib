
import cz.petrbalat.klib.spring.jwt.mvc.JWTAuthenticationFilter
import cz.petrbalat.klib.spring.jwt.mvc.JWTAuthorizationFilter
import io.jsonwebtoken.security.Keys
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import java.util.*
import javax.crypto.SecretKey
import javax.servlet.http.HttpServletResponse

internal fun String.toKey(): SecretKey {
    val toByteArray = Base64.getEncoder().encode(toByteArray())
    return Keys.hmacShaKeyFor(toByteArray)
}

fun HttpSecurity.addExceptionHandling() = exceptionHandling().authenticationEntryPoint { _, response, _ ->
    response?.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
}


fun HttpSecurity.addFilters(authenticationFilter: JWTAuthenticationFilter, authorizationFilter: JWTAuthorizationFilter): HttpSecurity {
    return addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(authorizationFilter, JWTAuthenticationFilter::class.java)
}
