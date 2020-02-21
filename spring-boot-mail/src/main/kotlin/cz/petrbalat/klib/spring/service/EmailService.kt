package cz.petrbalat.klib.spring.service

import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.AsyncResult
import java.util.concurrent.Future
import javax.mail.internet.MimeMessage

/**
 * Created by Petr Balat on 20.2.2020
 */
open class SmtpEmailService(private val mailSender: JavaMailSender,
                            private val defaultFrom: String) : EmailService {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Async
    override fun send(dto: EmailDto, vararg to: String): Future<EmailResultDto> {
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

            return AsyncResult(EmailResultDto(true))
        } catch (th: Throwable) {
            logger.error("Chyba při odeslání emailu", th)
            return AsyncResult(EmailResultDto(false, th))
        }
    }
}

interface EmailService {

    fun send(dto: EmailDto, vararg to: String): Future<EmailResultDto>
}
