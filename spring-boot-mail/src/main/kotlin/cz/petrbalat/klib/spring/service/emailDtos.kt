package cz.petrbalat.klib.spring.service

import org.springframework.core.io.InputStreamSource

data class EmailDto(
        val subject: String, val body: String,
        val attachments: Iterable<EmailAttachment> = emptyList(),

        val html: Boolean = true, val from: String? = null,
        val internalNote: String? = null,

        val user: Any? = null // for logging
)

data class EmailAttachment(val name: String, val stream: InputStreamSource)

data class EmailResultDto(val success: Boolean, val error: Throwable? = null)
