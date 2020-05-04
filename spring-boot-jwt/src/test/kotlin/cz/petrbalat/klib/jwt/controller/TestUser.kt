package cz.petrbalat.klib.jwt.controller

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.time.LocalDate

class TestUser(
        val id: Int,
        val email: String,
        @JsonIgnore val passwd: String? = null,
        val birth: LocalDate = LocalDate.of(1982, 1, 1)
) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_ADMIN"))

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun getPassword(): String = passwd.orEmpty()

    override fun isEnabled(): Boolean = true

    override fun getUsername(): String = email
}
