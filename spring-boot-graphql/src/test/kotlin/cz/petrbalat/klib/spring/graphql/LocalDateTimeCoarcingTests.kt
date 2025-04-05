package cz.petrbalat.klib.spring.graphql

import cz.petrbalat.klib.spring.graphql.LocalDateTimeCoarcing
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class LocalDateTimeCoarcingTests {

    @Test
    fun testPublish1() {
        val ldtc = LocalDateTimeCoarcing()
        assertEquals(LocalDateTime.of(2025,4,5, 18, 58, 57), ldtc.parseValue("2025-04-05T16:58:57.000Z"))

        assertEquals(LocalDateTime.of(2025,4,5, 18, 53, 34), ldtc.parseValue("2025-04-05T18:53:34.00"))
    }


}
