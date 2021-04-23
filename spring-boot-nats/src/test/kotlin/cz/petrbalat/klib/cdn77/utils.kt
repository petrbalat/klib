package cz.petrbalat.klib.cdn77

import java.time.LocalDateTime

private val testData: ByteArray
    get() = """{
"test": "${LocalDateTime.now()}"
}
""".toByteArray()


data class TestDto(val name: String, val ted: LocalDateTime = LocalDateTime.now())
