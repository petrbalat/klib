package cz.petrbalat.klib.jdk.datetime

import java.time.LocalDate

interface LocalDateInterval {

    val _start: LocalDate

    val _end: LocalDate

    infix fun isIn(date: LocalDate): Boolean = date in _start.._end

    /**
     * vrátí true pokud se interval kříží s intervalem z parametru
     */
    infix fun isCrossing(interval: LocalDateInterval): Boolean = isCrossing(interval._start, interval._end)

    fun isCrossing(start: LocalDate, end: LocalDate): Boolean =
            this isIn start || this isIn end || _start in start..end || _end in start..end
}
