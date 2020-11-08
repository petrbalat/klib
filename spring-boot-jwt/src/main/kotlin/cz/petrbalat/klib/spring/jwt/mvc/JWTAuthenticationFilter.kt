package cz.petrbalat.klib.spring.jwt.mvc

/**
 * @see https://auth0.com/blog/implementing-jwt-authentication-on-spring-boot/
 */
import com.fasterxml.jackson.databind.ObjectMapper
import cz.petrbalat.klib.jdk.datetime.now
import cz.petrbalat.klib.jdk.datetime.toDate
import cz.petrbalat.klib.spring.jwt.HEADER_STRING
import cz.petrbalat.klib.spring.jwt.TOKEN_PREFIX
import io.jsonwebtoken.Jwts
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import toKey
import java.time.Duration
import java.util.*
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Přihlášení
 *
 * security config
 * {
 * @code
 *  //JWT
 * .addFilter(JWTAuthenticationFilter(authenticationManager()))
 *  .addFilter(JWTAuthorizationFilter(authenticationManager()))
 *  // this disables session creation on Spring Security
 *  .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
 * }
 */
open class JWTAuthenticationFilter(private @Value("\${jwt.token.secret}") val secret: String,
                                   private val mapper: ObjectMapper,
                                   private val managerBulder: AuthenticationManagerBuilder,
                                   val expirationDateTime: Duration,
                                   private val userPrepare: PrepareUserToJson<UserDetails>?) : UsernamePasswordAuthenticationFilter() {

    protected val serializer = JacksonSerializer(mapper)

    override fun afterPropertiesSet() {
        //HACK
    }

    override fun attemptAuthentication(req: HttpServletRequest, res: HttpServletResponse?): Authentication {
        val (username: String, password: String) = mapper.readValue(req.inputStream, LoginUserDto::class.java)
        val token = UsernamePasswordAuthenticationToken(username, password)
        if (authenticationManager == null) {
            authenticationManager = managerBulder.orBuild
        }
        return authenticationManager.authenticate(token)
    }

    override fun successfulAuthentication(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain?,
                                          auth: Authentication) {

        val user = auth.principal as UserDetails
        val (json, token) = token(user)
        res.addHeader(HEADER_STRING, "$TOKEN_PREFIX$token")
        res.addHeader("Content-Type", "application/json; charset=utf-8")

        res.writer.write(json)
    }

    /**
     * userJson to jsonToken
     */
    open fun token(user_: UserDetails): Pair<String, String> {
        val user = userPrepare?.prepare(user_) ?: user_
        val userJson: String = mapper.writeValueAsString(user)
        val compact: String = Jwts.builder()
                .setSubject(userJson)
                .setIssuedAt(Date())
                .setExpiration(now.plus(expirationDateTime).toDate())
                .signWith(secret.toKey())
                .serializeToJsonWith(serializer)
                .compact()
        return userJson to compact
    }
}


internal data class LoginUserDto(val username: String, val password: String)
