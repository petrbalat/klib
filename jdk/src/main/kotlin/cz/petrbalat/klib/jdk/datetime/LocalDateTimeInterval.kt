package cz.petrbalat.klib.jdk.datetime

import java.time.LocalDateTime

/**
 * Created by Petr Balat
 */
interface LocalDateTimeInterval {

    val _start: LocalDateTime

    val _end: LocalDateTime

    infix fun isIn(date: LocalDateTime): Boolean = date in _start.._end

    /**
     * vrátí true pokud se interval kříží s intervalem z parametru
     */
    infix fun isCrossing(interval: LocalDateTimeInterval): Boolean = isCrossing(interval._start, interval._end)

    fun isCrossing(start: LocalDateTime, end: LocalDateTime): Boolean {
        return this isIn start || this isIn end || _start in start..end || _end in start..end
    }
}
