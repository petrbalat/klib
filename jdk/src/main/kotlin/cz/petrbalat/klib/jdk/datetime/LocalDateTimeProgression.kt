package cz.petrbalat.klib.jdk.datetime

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Created by Petr Balat
 */
class LocalDateTimeProgression(override val start: LocalDateTime,
                               override val endInclusive: LocalDateTime,
                               val amountToAdd: Long,
                               val unit: ChronoUnit) : Iterable<LocalDateTime>, ClosedRange<LocalDateTime> {

    override fun iterator(): Iterator<LocalDateTime> = LocalDateTimeProgressionIterator(start, endInclusive, amountToAdd, unit)

    infix fun stepSeconds(seconds: Long) = LocalDateTimeProgression(start, endInclusive, seconds, ChronoUnit.SECONDS)

    infix fun stepMinutes(minutes: Long) = LocalDateTimeProgression(start, endInclusive, minutes, ChronoUnit.MINUTES)

    infix fun stepDays(days: Long) = LocalDateTimeProgression(start, endInclusive, days, ChronoUnit.DAYS)

    infix fun stepWeeks(weeks: Long) = LocalDateTimeProgression(start, endInclusive, weeks, ChronoUnit.WEEKS)

    infix fun stepMonths(motnhs: Long) = LocalDateTimeProgression(start, endInclusive, motnhs, ChronoUnit.MONTHS)
}

internal class LocalDateTimeProgressionIterator(start: LocalDateTime,
                                                val endInclusive: LocalDateTime,
                                                val amountToAdd: Long,
                                                val unit: ChronoUnit) : Iterator<LocalDateTime> {

    private var current: LocalDateTime = start

    override fun hasNext() = current <= endInclusive

    override fun next(): LocalDateTime {
        val next = current
        current = current.plus(amountToAdd, unit)
        return next
    }
}

operator fun LocalDateTime.rangeTo(other: LocalDateTime) = LocalDateTimeProgression(this, other, 1, ChronoUnit.SECONDS)
