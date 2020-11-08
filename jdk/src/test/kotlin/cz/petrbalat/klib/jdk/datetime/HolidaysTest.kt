package cz.petrbalat.klib.jdk.datetime

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Created by Petr Balat
 */
class HolidaysTest {

    @Test
    fun getWeekend() {
        assertTrue(LocalDate.of(2017, 11, 12).weekend)
        assertTrue(LocalDate.of(2017, 11, 11).weekend)
        assertFalse(LocalDate.of(2017, 11, 13).weekend)
    }

    @Test
    fun velikonocniPondeli() {
        assertEquals(LocalDate.of(2016, 3, 28), easternMonday(2016))
        assertEquals(LocalDate.of(2017, 4, 17), easternMonday(2017))
        assertEquals(LocalDate.of(2018, 4, 2), easternMonday(2018))
        assertEquals(LocalDate.of(2019, 4, 22), easternMonday(2019))
    }

    @Test
    fun isCzechStateHolidays() {
        assertFalse(cz.petrbalat.klib.jdk.datetime.isCzechStateHolidays(LocalDate.of(2017, 3, 28)))
        assertTrue(cz.petrbalat.klib.jdk.datetime.isCzechStateHolidays(LocalDate.of(2017, 11, 17)))
        assertFalse(cz.petrbalat.klib.jdk.datetime.isCzechStateHolidays(LocalDate.of(2017, 4, 2)))
        assertTrue(cz.petrbalat.klib.jdk.datetime.isCzechStateHolidays(LocalDate.of(2017, 12, 24)))
        assertTrue(cz.petrbalat.klib.jdk.datetime.isCzechStateHolidays(LocalDate.of(2017, 12, 25)))
        assertTrue(cz.petrbalat.klib.jdk.datetime.isCzechStateHolidays(LocalDate.of(2017, 12, 26)))
    }

    @Test
    fun isCzechWorkingDay() {
        val days2016 = (LocalDate.of(2016, 1, 1)..LocalDate.of(2016, 12, 31)).toList()
        assertEquals(252, days2016.filter { cz.petrbalat.klib.jdk.datetime.isCzechWorkingDay(it) }.count())

        val days2017 = (LocalDate.of(2017, 1, 1)..LocalDate.of(2017, 12, 31)).toList()
        assertEquals(250, days2017.filter { cz.petrbalat.klib.jdk.datetime.isCzechWorkingDay(it) }.count())

        val days2018 = (LocalDate.of(2018, 1, 1)..LocalDate.of(2018, 12, 31)).toList()
        assertEquals(250, days2018.filter { cz.petrbalat.klib.jdk.datetime.isCzechWorkingDay(it) }.count())

        val days2019 = (LocalDate.of(2019, 1, 1)..LocalDate.of(2019, 12, 31)).toList()
        assertEquals(251, days2019.filter { cz.petrbalat.klib.jdk.datetime.isCzechWorkingDay(it) }.count())
    }

    @Test
    fun addCzechWorkingDay() {
        assertEquals(LocalDate.of(2017, 11, 13), LocalDate.of(2017, 11, 13).addCzechWorkingDay(0))
        assertEquals(LocalDate.of(2017, 11, 15), LocalDate.of(2017, 11, 13).addCzechWorkingDay(2))
        assertEquals(LocalDate.of(2017, 11, 21), LocalDate.of(2017, 11, 16).addCzechWorkingDay(2))
        assertEquals(LocalDate.of(2017, 12, 22), LocalDate.of(2017, 12, 21).addCzechWorkingDay(1))
        assertEquals(LocalDate.of(2017, 12, 27), LocalDate.of(2017, 12, 21).addCzechWorkingDay(2))
        assertEquals(LocalDate.of(2017, 12, 29), LocalDate.of(2017, 12, 22).addCzechWorkingDay(3))

        assertEquals(LocalDate.of(2020, 11, 6), LocalDate.of(2020, 11, 8).addCzechWorkingDay(-1))
        assertEquals(LocalDate.of(2020, 11, 4), LocalDate.of(2020, 11, 6).addCzechWorkingDay(-2))
    }
}
