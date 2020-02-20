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
            val message = MimeMessageHelper(mimeMessage, dto.attachments.any())
            message.setFrom(from)
            dto.attachments.forEach {
                message.addAttachment(it.name, it.stream)
            }
            message.setTo(to)
            message.setSubject(dto.subject)

            message.setText(dto.body, dto.html)

            logger.info("Odesílám $dto k $to")
            mailSender.send(mimeMessage)

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
