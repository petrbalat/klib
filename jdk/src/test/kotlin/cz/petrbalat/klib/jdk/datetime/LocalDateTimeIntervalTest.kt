package cz.petrbalat.klib.jdk.datetime

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Created by Petr Balat
 */
class LocalDateTimeIntervalTest {

    private val yesterday = now.minusDays(1)
    private val tomorrow = now.plusDays(1)

    @Test
    fun visibleNowInterval() {
        assertTrue(LDTI(yesterday, tomorrow) isIn now)
        assertTrue(LDTI(LocalDateTime.MIN, LocalDateTime.MAX) isIn now)
        assertTrue(LDTI(LocalDateTime.MIN, tomorrow) isIn now)
        assertTrue(LDTI(yesterday, LocalDateTime.MAX) isIn now)
        assertTrue(actual = LDTI(now, tomorrow) isIn now)
        assertTrue(LDTI(now.plusDays(-2), tomorrow) isIn now)
        //
        assertFalse(LDTI(now.plusDays(-3), now.plusDays(-1)) isIn now)
        assertFalse(LDTI(LocalDateTime.MIN, now.plusDays(-1)) isIn now)
        assertFalse(LDTI(now.plusDays(1), LocalDateTime.MAX) isIn now)
        assertFalse(LDTI(now.plusDays(1), now.plusDays(2)) isIn now)
    }

    @Test
    fun crossInterval() {
        assertTrue(LDTI(yesterday, tomorrow) isCrossing LDTI(now.minusDays(2), now.plusDays(2)))//větší interval
        assertTrue(LDTI(now.minusDays(2), now.plusDays(2)) isCrossing LDTI(yesterday, tomorrow))//uvnitř intervalu
        assertTrue(LDTI(now.minusDays(2), tomorrow) isCrossing LDTI(yesterday, now.plusDays(2)))//start v intervalu
        assertTrue(LDTI(now.minusDays(1), now.plusDays(2)) isCrossing LDTI(now.minusDays(2), tomorrow))//end v intervalu
        val _start = LocalDateTime.of(2017, 10, 25, 21, 26)
        assertTrue(LDTI(_start, _start.plusSeconds(10)) isCrossing LDTI(_start, _start.plusSeconds(2)))//end v intervalu
        val ted = now
        assertTrue(LDTI(ted, ted.plusSeconds(10)) isCrossing LDTI(ted.plusSeconds(10), ted.plusSeconds(20)))
        assertTrue(LDTI(ted, ted.plusSeconds(10)) isCrossing LDTI(ted.minusSeconds(10), ted))

        assertFalse(LDTI(now.minusDays(2), yesterday) isCrossing LDTI(now, tomorrow))//před
        assertFalse(LDTI(now, tomorrow) isCrossing LDTI(tomorrow.plusDays(1), tomorrow.plusDays(2)))//za intervalem
    }

}

data class LDTI(override val _start: LocalDateTime, override var _end: LocalDateTime) : LocalDateTimeInterval
