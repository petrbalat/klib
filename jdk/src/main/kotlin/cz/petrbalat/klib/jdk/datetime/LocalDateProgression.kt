package cz.petrbalat.klib.jdk.datetime

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Created by Petr Balat
 */
class LocalDateProgression(override val start: LocalDate,
                           override val endInclusive: LocalDate,
                           val amountToAdd: Long = 1,
                           val unit: ChronoUnit = ChronoUnit.DAYS) : Iterable<LocalDate>, ClosedRange<LocalDate> {

    override fun iterator(): Iterator<LocalDate> = LocalDateProgressionIterator(start, endInclusive, amountToAdd, unit)

    infix fun stepDays(days: Long) = LocalDateProgression(start, endInclusive, days)

    infix fun stepWeeks(weeks: Long) = LocalDateProgression(start, endInclusive, weeks, ChronoUnit.WEEKS)

    infix fun stepMonths(motnhs: Long) = LocalDateProgression(start, endInclusive, motnhs, ChronoUnit.MONTHS)
}

internal class LocalDateProgressionIterator(start: LocalDate,
                                            val endInclusive: LocalDate,
                                            val amountToAdd: Long,
                                            val unit: ChronoUnit = ChronoUnit.DAYS) : Iterator<LocalDate> {

    var current = start

    override fun hasNext() = current <= endInclusive

    override fun next(): LocalDate {
        val next = current
        current = current.plus(amountToAdd, unit)
        return next
    }
}

operator fun LocalDate.rangeTo(other: LocalDate) = LocalDateProgression(this, other)
