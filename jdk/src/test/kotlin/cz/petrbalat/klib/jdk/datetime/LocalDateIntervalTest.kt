package cz.petrbalat.klib.jdk.datetime

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Created by Petr Balat
 */
class LocalDateIntervalTest {

    private val yesterday = today.minusDays(1)
    private val tomorrow = today.plusDays(1)

    @Test
    fun visibleNowInterval() {
        assertTrue(LDI(yesterday, tomorrow) isIn today)
        assertTrue(LDI(LocalDate.MIN, LocalDate.MAX) isIn today)
        assertTrue(LDI(LocalDate.MIN, tomorrow) isIn today)
        assertTrue(LDI(yesterday, LocalDate.MAX) isIn today)
        assertTrue(actual = LDI(today, tomorrow) isIn today)
        assertTrue(LDI(today.plusDays(-2), tomorrow) isIn today)
        //
        assertFalse(LDI(today.plusDays(-3), today.plusDays(-1)) isIn today)
        assertFalse(LDI(LocalDate.MIN, today.plusDays(-1)) isIn today)
        assertFalse(LDI(today.plusDays(1), LocalDate.MAX) isIn today)
        assertFalse(LDI(today.plusDays(1), today.plusDays(2)) isIn today)
    }

    @Test
    fun crossInterval() {
        assertTrue(LDI(LocalDate.MIN, LocalDate.MAX) isCrossing LDI(today.minusDays(2), today.plusDays(2)))//větší interval
        assertTrue(LDI(yesterday, tomorrow) isCrossing LDI(today.minusDays(2), today.plusDays(2)))//větší interval
        assertTrue(LDI(today.minusDays(2), today.plusDays(2)) isCrossing LDI(yesterday, tomorrow))//uvnitř intervalu
        assertTrue(LDI(yesterday, tomorrow) isCrossing LDI(today.minusDays(2), today.plusDays(2)))//uvnitř intervalu
        assertTrue(LDI(today.minusDays(2), tomorrow) isCrossing LDI(yesterday, today.plusDays(2)))//start v intervalu
        assertTrue(LDI(today.minusDays(1), today.plusDays(2)) isCrossing LDI(today.minusDays(2), tomorrow))//end v intervalu

        assertFalse(LDI(today.minusDays(2), yesterday) isCrossing LDI(today, tomorrow))//před
        assertFalse(LDI(today, tomorrow) isCrossing LDI(tomorrow.plusDays(1), tomorrow.plusDays(2)))//za intervalem
    }

}

data class LDI(override val _start: LocalDate, override var _end: LocalDate) : LocalDateInterval
