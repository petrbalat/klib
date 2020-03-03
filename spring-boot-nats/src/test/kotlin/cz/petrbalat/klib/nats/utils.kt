package cz.petrbalat.klib.nats

import java.time.LocalDateTime

private val testData: ByteArray
    get() = """{
"test": "${LocalDateTime.now()}"
}
""".toByteArray()


data class TestDto(val name: String, val ted: LocalDateTime = LocalDateTime.now())
