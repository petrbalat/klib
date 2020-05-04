package cz.petrbalat.klib.jwt

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import kotlin.test.Test
import kotlin.test.assertEquals

class PasswordTest {

    val passwordEncoder = BCryptPasswordEncoder()

    @Test
    fun encode() {
        val encodePeter = passwordEncoder.encode("222222")
        println(encodePeter)
        assertEquals(1, 1)
    }
}
