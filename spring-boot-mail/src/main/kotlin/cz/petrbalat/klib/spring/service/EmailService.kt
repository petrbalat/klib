package cz.petrbalat.klib.spring.service

import cz.petrbalat.klib.jdk.string.emptyToNull
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import java.util.concurrent.CompletableFuture
import javax.mail.internet.MimeMessage

/**
 * Created by Petr Balat on 20.2.2020
 */
open class SmtpEmailService(private val mailSender: JavaMailSender,
                            private val defaultFrom: String) : EmailService {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Async
    override fun send(dto: EmailDto, vararg to: String): CompletableFuture<EmailResultDto> {
        val to: Array<String> = to.mapNotNull { it.trim().emptyToNull() }.toTypedArray()
        try {
            val from = dto.from ?: defaultFrom
            val dto = dto.copy(from = from)
            val mimeMessage: MimeMessage = mailSender.createMimeMessage()

            MimeMessageHelper(mimeMessage, dto.attachments.any()).apply {
                setFrom(from)
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

            return CompletableFuture.completedFuture(EmailResultDto(true))
        } catch (th: Throwable) {
            logger.error("Chyba při odeslání emailu ${to.joinToString()}", th)
            return CompletableFuture.completedFuture(EmailResultDto(false, th))
        }
    }
}

interface EmailService {

    fun send(dto: EmailDto, vararg to: String): CompletableFuture<EmailResultDto>
}
