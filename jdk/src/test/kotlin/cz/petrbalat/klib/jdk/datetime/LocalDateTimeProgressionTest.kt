package cz.petrbalat.klib.jdk.datetime

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Created by Petr Balat
 */
class LocalDateTimeProgressionTest {

    val ted: LocalDateTime = now

    @Test
    fun rangeTo() {
        val days: List<LocalDateTime> = (ted..ted.plusDays(2) stepDays 1).toList()
        assertEquals(3, days.size)
        assertEquals(ted, days.first())
        assertEquals(ted.plusDays(1), days.elementAt(1))
        assertEquals(ted.plusDays(2), days.last())
    }

    @Test
    fun rangeToDays() {
        val days: List<LocalDateTime> = (ted..ted.plusDays(2) stepDays 2).toList()
        assertEquals(2, days.size)
        assertEquals(ted, days.first())
        assertEquals(ted.plusDays(2), days.last())
    }


    @Test
    fun rangeToSeconds() {
        val days: List<LocalDateTime> = (ted..ted.plusDays(1) stepSeconds 1).toList()
        assertEquals(86401, days.size)
        assertEquals(ted, days.first())
        assertEquals(ted.plusSeconds(1), days.elementAt(1))
        assertEquals(ted.plusDays(1), days.last())
    }
}
