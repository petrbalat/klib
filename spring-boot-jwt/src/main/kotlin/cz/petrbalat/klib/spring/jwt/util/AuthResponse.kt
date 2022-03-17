package cz.petrbalat.klib.spring.jwt.util

import org.springframework.security.core.userdetails.UserDetails

data class AuthResponse(val token: String, val user: UserDetails)
