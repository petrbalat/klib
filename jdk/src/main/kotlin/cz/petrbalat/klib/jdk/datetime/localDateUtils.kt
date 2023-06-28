package cz.petrbalat.klib.jdk.datetime

import java.sql.Timestamp
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*


/**
 * Created by Petr Balat
 */

inline val today: LocalDate
    get() = LocalDate.now()

inline val now: LocalDateTime
    get() = LocalDateTime.now()

fun LocalDate?.orMin(): LocalDate = this ?: LocalDate.MIN
fun LocalDate?.orMax(): LocalDate = this ?: LocalDate.MAX

fun LocalDateTime?.orMin(): LocalDateTime = this ?: LocalDateTime.MIN
fun LocalDateTime?.orMax(): LocalDateTime = this ?: LocalDateTime.MAX

fun LocalDate.toDate(zoneId: ZoneId = ZoneId.systemDefault()): Date = atStartOfDay().toDate(zoneId)

fun LocalDateTime.toInstant(zoneId: ZoneId = ZoneId.systemDefault()) = this.atZone(zoneId).toInstant()

fun LocalDateTime.toDate(zoneId:     ZoneId = ZoneId.systemDefault()): Date = Date.from(toInstant())

fun ZoneId.toOffset(): ZoneOffset = Instant.now().atZone(this).offset

fun Date.toLocalDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate = toLocalDateTime(zoneId).toLocalDate()

fun Date.toLocalDateTime(zoneId: ZoneId = ZoneId.systemDefault()): LocalDateTime {
    val instant = Instant.ofEpochMilli(time)
    return LocalDateTime.ofInstant(instant, zoneId)
}

fun LocalDateTime.toOffsetDateTime(zoneId: ZoneId = ZoneId.systemDefault()): OffsetDateTime =
        atZone(zoneId).toOffsetDateTime()

fun LocalDateTime.toSystemZone(): ZonedDateTime = atZone(ZoneId.systemDefault())
fun LocalDateTime.toUtcZone(): ZonedDateTime = atZone(ZoneOffset.UTC)
fun ZonedDateTime.toUtcZone(): ZonedDateTime = withZoneSameInstant(ZoneOffset.UTC)

fun LocalDateTime.toTimestamp(): Timestamp = Timestamp(toInstant().epochSecond)

fun Long.toLocalDateTime(zoneId: ZoneId = ZoneId.systemDefault()): LocalDateTime {
    val instant = Instant.ofEpochMilli(this)
    return LocalDateTime.ofInstant(instant, zoneId)
}

fun Instant.toLocalDateTime(zoneId: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        LocalDateTime.ofInstant(this, zoneId)

fun LocalDateTime.removeSec(): LocalDateTime = LocalDateTime.of(year, month, dayOfMonth, hour, minute)

infix fun LocalDateTime.duration(other: LocalDateTime): Duration = Duration.between(this, other)

infix fun LocalDate.duration(other: LocalDate): Duration = Duration.between(this, other)

infix fun LocalDate.period(other: LocalDate): Period = Period.between(this, other)

fun LocalDateTime.isFuture(now: LocalDateTime = LocalDateTime.now()): Boolean = this >= now

fun LocalDate.isFuture(now: LocalDate = today): Boolean = this > now

infix fun LocalDate.minusDays(other: LocalDate): Long = ChronoUnit.DAYS.between(other, this)

infix fun LocalDateTime.minusSecond(other: LocalDateTime): Long = ChronoUnit.SECONDS.between(other, this)

infix fun LocalDateTime.minusMinutes(other: LocalDateTime): Long = ChronoUnit.MINUTES.between(other, this)

infix fun LocalDateTime.minusHours(other: LocalDateTime): Long = ChronoUnit.HOURS.between(other, this)

infix fun LocalDateTime.minusDays(other: LocalDateTime): Long = ChronoUnit.DAYS.between(other, this)

fun LocalDate.startWeek(start: DayOfWeek = DayOfWeek.MONDAY, minus:Boolean = true): LocalDate {
    var day = this

    while (true) {
        val dayOfWeek: DayOfWeek = day.dayOfWeek
        if (dayOfWeek == start) return day
        day = if(minus) day.minusDays(1) else day.plusDays(1)
    }
}

fun LocalDateTime.roundUpToMinute(): LocalDateTime = withNano(0).run {
    if (second > 0) plusMinutes(1).withSecond(0) else this
}

fun LocalDateTime.roundDownToMinute(): LocalDateTime = withNano(0).withSecond(0)
