package cz.petrbalat.klib.spring.jwt.mvc

import org.springframework.security.core.userdetails.UserDetails

interface PrepareUserToJson<T> where T : UserDetails {

    fun prepare(user: T): T
}
