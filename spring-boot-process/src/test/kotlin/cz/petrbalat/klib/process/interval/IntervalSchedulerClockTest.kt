package cz.petrbalat.klib.process.interval

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Čekání na start podle hodin — skok hodin (NTP po bootu zařízení bez RTC) a přechod letního času. */
class IntervalSchedulerClockTest {

    private val zone = ZoneId.of("Europe/Prague")

    @Test
    fun `skok hodin dopředu po NTP synchronizaci spustí interval do clockCheck`() = runBlocking {
        // zařízení bez RTC po bootu — hodiny v roce 1970, plán už z reálného světa
        val clock = MovableClock(Instant.EPOCH, zone)
        val scheduler = IntervalScheduler<Int>(clock, clockCheck = 50.milliseconds)
        try {
            val started = CompletableDeferred<Unit>()
            val start = LocalDateTime.of(2026, 10, 10, 12, 0)
            scheduler.schedule(1, start, null) { onStart { started.complete(Unit) } }
            delay(100.milliseconds)
            assertFalse(started.isCompleted)

            clock.set(start.atZone(zone).toInstant().minusMillis(200))
            // jeden delay spočítaný v roce 1970 by čekal 56 let
            withTimeout(2.seconds) { started.await() }
        } finally {
            scheduler.shutdown()
        }
    }

    @Test
    fun `přechod na letní čas neposune start o hodinu`() = runBlocking {
        // 29. 3. 2026 v 2:00 SEČ se posouvá na 3:00 SELČ
        val clock = MovableClock(LocalDateTime.of(2026, 3, 29, 1, 59, 59, 700_000_000).atZone(zone).toInstant(), zone)
        val scheduler = IntervalScheduler<Int>(clock)
        try {
            val started = CompletableDeferred<Unit>()
            // 3:00:00,2 SELČ je za 500 ms; rozdíl LocalDateTime by byl hodina
            scheduler.schedule(1, LocalDateTime.of(2026, 3, 29, 3, 0, 0, 200_000_000), null) {
                onStart { started.complete(Unit) }
            }
            withTimeout(2.seconds) { started.await() }
        } finally {
            scheduler.shutdown()
        }
    }

    /** Hodiny jdoucí v reálném čase od [base]; [set] = skok hodin (NTP). */
    private class MovableClock(base: Instant, private val zone: ZoneId) : Clock() {
        @Volatile
        private var offset: java.time.Duration = java.time.Duration.between(Instant.now(), base)

        fun set(now: Instant) {
            offset = java.time.Duration.between(Instant.now(), now)
        }

        override fun instant(): Instant = Instant.now().plus(offset)
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MovableClock(instant(), zone)
    }
}
