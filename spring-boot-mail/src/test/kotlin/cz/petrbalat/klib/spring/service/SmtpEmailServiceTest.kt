package cz.petrbalat.klib.spring.service

import org.springframework.mail.javamail.JavaMailSenderImpl
import kotlin.test.Test
import kotlin.test.assertNotNull

internal class SmtpEmailServiceTest {

    @Test
    fun send() {
        //todo mock JavaMailSender
        val mailService = SmtpEmailService(JavaMailSenderImpl(), "test@email.cz")

        val dto = EmailDto(subject = "předmět", body = "Hello email", html = false)
        val resultDto = mailService.send(dto, "email1@test", "email2.tx")
        assertNotNull(resultDto)
    }
}
