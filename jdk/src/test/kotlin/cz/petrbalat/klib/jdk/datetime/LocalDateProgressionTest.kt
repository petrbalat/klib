package cz.petrbalat.klib.jdk.datetime

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Created by Petr Balat
 */
class LocalDateProgressionTest {

    @Test
    fun rangeTo() {
        val days: List<LocalDate> = (today..today.plusDays(2)).toList()
        assertEquals(3, days.size)
        assertEquals(today, days.first())
        assertEquals(today.plusDays(1), days.elementAt(1))
        assertEquals(today.plusDays(2), days.last())
    }

    @Test
    fun rangeToStep() {
        val days: List<LocalDate> = (today..today.plusDays(2) stepDays 2).toList()
        assertEquals(2, days.size)
        assertEquals(today, days.first())
        assertEquals(today.plusDays(2), days.last())
    }

    @Test
    fun rangeToStepMinutes() {
        val minutes: List<LocalDateTime> = (now..now.plusMinutes(6) stepMinutes 2).toList()
        assertEquals(4, minutes.size)
        assertEquals(now.removeSec(), minutes.first().removeSec())
    }
}
