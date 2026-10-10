package cz.petrbalat.klib.spring.service

import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ByteArrayResource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Posílá emaily na lokálně spuštěný mailpit (viz `spring.mail.*` v test `application.properties`),
 * doručení ověřuje přes jeho REST API. Výsledek je vidět i na http://localhost:8025.
 */
@SpringBootTest
internal class SmtpEmailServiceTest(@Autowired private val mailSender: JavaMailSender) {

    private val mailService = SmtpEmailService(mailSender, "test@email.cz", "Klib test")

    private val mailpit = RestClient.create("http://localhost:8025/api/v1")

    @Test
    fun send() {
        val subject = "předmět ${UUID.randomUUID()}"
        mailService.send(EmailDto(subject = subject, body = "Hello email", html = false), "email1@test.cz", "email2@test.cz")

        val message = findMessage(subject)
        assertEquals("test@email.cz", (message["From"] as Map<*, *>)["Address"])
        assertEquals("Klib test", (message["From"] as Map<*, *>)["Name"])
        assertEquals(
            listOf("email1@test.cz", "email2@test.cz"),
            (message["To"] as List<*>).map { (it as Map<*, *>)["Address"] },
        )
    }

    @Test
    fun sendHtmlWithAttachment() {
        val subject = "html s přílohou ${UUID.randomUUID()}"
        val dto = EmailDto(
            subject = subject, body = "<h1>Příliš žluťoučký kůň</h1>",
            attachments = listOf(EmailAttachment("soubor.txt", ByteArrayResource("obsah přílohy".toByteArray()))),
        )
        mailService.send(dto, "email1@test.cz")

        val message = findMessage(subject)
        assertEquals(1, message["Attachments"])
    }

    @Test
    fun sendSuspend(): Unit = runBlocking {
        var sentOnVirtualThread: Boolean? = null
        val service = object : SmtpEmailService(mailSender, "test@email.cz") {
            override fun send(dto: EmailDto, vararg to: String) {
                sentOnVirtualThread = Thread.currentThread().isVirtual
                super.send(dto, *to)
            }
        }

        val subject = "suspend ${UUID.randomUUID()}"
        service.sendSuspend(EmailDto(subject = subject, body = "Hello suspend", html = false), "email1@test.cz")

        assertEquals(true, sentOnVirtualThread)
        findMessage(subject)
    }

    /** Najde v mailpitu doručenou zprávu podle předmětu (ověří, že je právě jedna). */
    private fun findMessage(subject: String): Map<*, *> {
        val result = mailpit.get()
            .uri("/search?query={q}", "subject:\"$subject\"")
            .retrieve().body<Map<String, Any>>()!!
        val messages = result["messages"] as List<*>
        assertEquals(1, messages.size, "zpráva '$subject' není v mailpitu")
        return messages.single() as Map<*, *>
    }
}
