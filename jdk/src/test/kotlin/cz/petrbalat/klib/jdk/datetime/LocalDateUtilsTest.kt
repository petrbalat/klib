package cz.petrbalat.klib.jdk.datetime

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals


class LocalDateUtilsTest {

    private val test2016_7_26 = LocalDate.of(2016, 8, 26)

    @Test
    fun minusSecondLocalDate() {
        assertEquals(0, test2016_7_26 minusDays test2016_7_26)

        assertEquals(-3, LocalDate.of(2017, 8, 27) minusDays LocalDate.of(2017, 8, 30))
        assertEquals(11, test2016_7_26.plusDays(11) minusDays test2016_7_26)
    }

    @Test
    fun startWeek() {
        assertEquals(0, test2016_7_26 minusDays test2016_7_26)
        assertEquals(LocalDate.of(2017, 3, 20), LocalDate.of(2017, 3, 25).startWeek())
    }

    @Test
    fun testToDate() {
        val date = LocalDate.of(2014, 3, 13).toDate()
        val calendar = Calendar.getInstance()
        calendar.time = date

        assertEquals(2014, calendar.get(Calendar.YEAR).toLong())
        assertEquals(Calendar.MARCH.toLong(), calendar.get(Calendar.MONTH).toLong())
        assertEquals(13, calendar.get(Calendar.DAY_OF_MONTH).toLong())
    }

    @Test
    fun testToDateFromLocalDateTime() {
        val date = LocalDateTime.of(2014, 3, 13, 11, 4, 10).toDate()
        val calendar = Calendar.getInstance()
        calendar.time = date

        assertEquals(2014, calendar.get(Calendar.YEAR).toLong())
        assertEquals(Calendar.MARCH.toLong(), calendar.get(Calendar.MONTH).toLong())
        assertEquals(13, calendar.get(Calendar.DAY_OF_MONTH).toLong())
        assertEquals(11, calendar.get(Calendar.HOUR_OF_DAY).toLong())
        assertEquals(4, calendar.get(Calendar.MINUTE).toLong())
        assertEquals(10, calendar.get(Calendar.SECOND).toLong())
    }


    @Test
    fun testToLocalDate() {
        val localDate = datum.toLocalDate()
        assertEquals(2014, localDate.year.toLong())
        assertEquals(3, localDate.monthValue.toLong())
        assertEquals(Month.MARCH, localDate.month)
        assertEquals(13, localDate.dayOfMonth.toLong())
    }

    @Test
    fun minusSecondLocalDateTime() {
        assertEquals(5, test2016_7_26.atStartOfDay().plusSeconds(5) minusSecond test2016_7_26.atStartOfDay())
        assertEquals(86460, LocalDate.of(2016, 8, 27).atStartOfDay().plusMinutes(1) minusSecond test2016_7_26.atStartOfDay())
    }

    @Test
    fun removeSec() {
        assertEquals(LocalDateTime.of(2016, 8, 26, 0, 0, 0), test2016_7_26.atStartOfDay().plusSeconds(5).removeSec())
    }

    @Test
    fun orMin() {
        val testTime: LocalDateTime? = null
        assertEquals(LocalDateTime.MIN, testTime.orMin())
        assertEquals(test2016_7_26, test2016_7_26.orMin())

        val test: LocalDate? = null
        assertEquals(LocalDate.MIN, test.orMin())
        assertEquals(test2016_7_26.atStartOfDay(), test2016_7_26.atStartOfDay().orMin())
    }

    @Test
    fun orMax() {
        val testTime: LocalDateTime? = null
        assertEquals(LocalDateTime.MAX, testTime.orMax())
        assertEquals(test2016_7_26, test2016_7_26.orMax())

        val test: LocalDate? = null
        assertEquals(LocalDate.MAX, test.orMax())
        assertEquals(test2016_7_26.atStartOfDay(), test2016_7_26.atStartOfDay().orMax())
    }

    @Test
    fun testStartWeek() {
        assertEquals(LocalDate.of(2016, 8, 22), LocalDate.of(2016, 8, 26).startWeek())
        assertEquals(LocalDate.of(2017, 9, 22), LocalDate.of(2017, 9, 21).startWeek(DayOfWeek.FRIDAY, minus = false))
        assertEquals(LocalDate.of(2017, 9, 18), LocalDate.of(2017, 9, 18).startWeek())
        assertEquals(LocalDate.of(2017, 9, 17), LocalDate.of(2017, 9, 18).startWeek(DayOfWeek.SUNDAY))
    }

    private val datum: Date
        get() {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = 1394704488000L
            return calendar.time
        }
}
