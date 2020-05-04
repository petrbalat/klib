package cz.petrbalat.klib.spring.jwt

import org.springframework.security.core.context.SecurityContextHolder

const val TOKEN_PREFIX = "Bearer "
const val HEADER_STRING = "Authorization"

inline fun <reified T> getCurrentUser(): T? = SecurityContextHolder.getContext()?.authentication?.principal as? T
