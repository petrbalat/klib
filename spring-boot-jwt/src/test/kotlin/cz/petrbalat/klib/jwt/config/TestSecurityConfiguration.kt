package cz.petrbalat.klib.jwt.config

import addExceptionHandling
import addFilters
import cz.petrbalat.klib.spring.jwt.mvc.EmptyAuthenticationSuccessHandler
import cz.petrbalat.klib.spring.jwt.mvc.JWTAuthenticationFilter
import cz.petrbalat.klib.spring.jwt.mvc.JWTAuthorizationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder


/**
 * @author Petr Balat
 *
 */
@Configuration(proxyBeanMethods = false)
class TestSecurityConfiguration(private val authenticationFilter: JWTAuthenticationFilter,
                                private val authorizationFilter: JWTAuthorizationFilter) : WebSecurityConfigurerAdapter() {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    public override fun configure(http: HttpSecurity) {
        http.cors()
                .and().csrf().disable().formLogin().loginPage("/login").successHandler(EmptyAuthenticationSuccessHandler())
                .and().addFilters(authenticationFilter, authorizationFilter)
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and().addExceptionHandling()
                .and().headers().frameOptions().disable()

        println("test security config")
    }
}

