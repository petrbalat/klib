package cz.petrbalat.klib.spring.service

import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
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

        logger.info("Odesílám email(${dto.hashCode()}) $dto k $to")
        mailSender.send(mimeMessage)
        logger.info("Email(${dto.hashCode()}) úspěšně odeslán ")
    }
}

interface EmailService {

    fun send(dto: EmailDto, vararg to: String): Unit
}
