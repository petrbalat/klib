package cz.petrbalat.klib.jdk.datetime

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month


/**
 * Created by Petr Balat
 */

val LocalDate.weekend: Boolean
    get() = this.dayOfWeek == DayOfWeek.SATURDAY || this.dayOfWeek == DayOfWeek.SUNDAY

internal fun easternMonday(rok: Int): LocalDate {
    val num2 = rok % 19
    val num3 = rok / 100
    val num4 = rok % 100
    val num5 = num3 / 4
    val num6 = num3 % 4
    val num7 = (num3 + 8) / 25
    val num8 = (num3 - num7 + 1) / 3
    val num9 = (19 * num2 + num3 - num5 - num8 + 15) % 30
    val num10 = num4 / 4
    val num11 = num4 % 4
    val num12 = (32 + 2 * num6 + 2 * num10 - num9 - num11) % 7
    val num13 = (num2 + 11 * num9 + 22 * num12) / 451
    val month = (num9 + num12 - 7 * num13 + 114) / 31
    val day = (num9 + num12 - 7 * num13 + 114) % 31 + 1

    val dateTime = LocalDate.of(rok, month, day)
    return dateTime.plusDays(1)
}

fun isCzechStateHolidays(day: LocalDate): Boolean {
    val velikonocniPondeli = easternMonday(day.year)
    if (velikonocniPondeli == day || day == velikonocniPondeli.minusDays(3)) {
        return true
    }

    return when {
        day.dayOfMonth == 1 && day.month == Month.JANUARY -> true //1.1 Nový rok
        day.dayOfMonth == 1 && day.month == Month.MAY -> true //1.5 Svátek práce
        day.dayOfMonth == 8 && day.month == Month.MAY -> true //8.5 Den vítěztví
        day.dayOfMonth == 5 && day.month == Month.JULY -> true //6.7 Den slovanských věrozvěstů Cyrila a Metoděje
        day.dayOfMonth == 6 && day.month == Month.JULY -> true //6.7 Den upálení mistra Jana Husa
        day.dayOfMonth == 28 && day.month == Month.SEPTEMBER -> true //28.9 Den české státnosti
        day.dayOfMonth == 28 && day.month == Month.OCTOBER -> true //28.10 Den vzniku samostatného československého státu
        day.dayOfMonth == 17 && day.month == Month.NOVEMBER -> true //17.11 Den boje za svobodu a demokracii
        day.dayOfMonth == 24 && day.month == Month.DECEMBER -> true //24.12 Štědrý den
        day.dayOfMonth == 25 && day.month == Month.DECEMBER -> true //25.12 1. svátek vánoční
        day.dayOfMonth == 26 && day.month == Month.DECEMBER -> true //26.12 2. svátek vánoční

        else -> false
    }
}

fun isCzechWorkingDay(day: LocalDate): Boolean = !isCzechStateHolidays(day) && !day.weekend

fun LocalDate.addCzechWorkingDay(day: Long): LocalDate {
    var rest = day
    var workDay: LocalDate = this
    if (day > 0) {
        while (rest > 0) {
            workDay = workDay.plusDays(1)
            if (isCzechWorkingDay(workDay)) {
                rest--
            }
        }
    } else {
        while (rest < 0) {
            workDay = workDay.minusDays(1)
            if (isCzechWorkingDay(workDay)) {
                rest++
            }
        }
    }

    return workDay
}
