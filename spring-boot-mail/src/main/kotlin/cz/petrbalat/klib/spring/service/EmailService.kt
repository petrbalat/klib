package cz.petrbalat.klib.spring.service

import cz.petrbalat.klib.jdk.virtualThreadDispatcher
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper

/**
 * Created by Petr Balat on 20.2.2020
 */
open class SmtpEmailService(
    private val mailSender: JavaMailSender,
    private val defaultFrom: String,
    private val defaultPersonal: String? = null,
) : EmailService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun send(dto: EmailDto, vararg to: String) {
        val to: Array<String> = to.mapNotNull { it.trim().takeIf { str -> str.isNotEmpty() } }.toTypedArray()
        val from = dto.from ?: defaultFrom
        val dto = dto.copy(from = from, personal = dto.personal ?: defaultPersonal)
        val mimeMessage: MimeMessage = mailSender.createMimeMessage()

        MimeMessageHelper(mimeMessage, dto.attachments.any()).apply {
            setFrom(InternetAddress(from, dto.personal))
            setTo(to)
            setSubject(dto.subject)
            setText(dto.body, dto.html)
            dto.attachments.forEach { ea ->
                addAttachment(ea.name, ea.stream)
            }
        }

        val id = dto.hashCode()
        logger.info(
            "Odesílám email($id) '${dto.subject}' od $from k ${to.joinToString()}, " +
                "příloh ${dto.attachments.count()}, user=${dto.user}, poznámka=${dto.internalNote}"
        )
        mailSender.send(mimeMessage)
        logger.info("Email($id) úspěšně odeslán")
    }
}

interface EmailService {

    fun send(dto: EmailDto, vararg to: String): Unit

    /**
     * Suspend varianta [EmailService.send] — blokující odeslání (Jakarta Mail) proběhne na virtuálním vlákně,
     * takže neblokuje volající dispatcher.
     *
     * Vyžaduje na classpath `kotlinx-coroutines-core` (v knihovně je `compileOnly`).
     */
    suspend fun sendSuspend(dto: EmailDto, vararg to: String): Unit =
        withContext(virtualThreadDispatcher) { send(dto, *to) }
}
