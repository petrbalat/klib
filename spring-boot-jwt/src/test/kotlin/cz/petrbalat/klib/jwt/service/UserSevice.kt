package cz.petrbalat.klib.jwt.service

import cz.petrbalat.klib.jwt.controller.TestUser
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class UserService : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        return users.firstOrNull { it.username == username }
                ?: throw UsernameNotFoundException("Uživatel $username nenalezen")
    }
}

//password 111, 222
private val users = listOf(
        TestUser(1, "Peter", "\$2a\$10\$erSZOAdu5.XAoLM0lTDUEuq1yzx9NakqY8qb8mYLNReIjf0tVyIPy"),
        TestUser(2, "Jane", "\$2a\$10\$XZB70gk.BAwUfo8pL4B0A.uyTgg46idD8SUEJKZs5pHFNdaqJT/X6")
)
