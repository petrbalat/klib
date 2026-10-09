package cz.petrbalat.klib.process.interval

import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/** Klíč intervalu je generický — mimo Int (CDC) třeba String. */
class IntervalSchedulerKeyTest {

    @Test
    fun `string klíč - schedule, runningIds a cancelAndJoin`() = runBlocking {
        val scheduler = IntervalScheduler<String>()
        try {
            val started = CompletableFuture<Unit>()
            val ended = CompletableFuture<EndReason>()
            scheduler.schedule("room-1", LocalDateTime.now(), null) {
                onStart { started.complete(Unit) }
                onEnd { ended.complete(it) }
            }

            started.get(5, TimeUnit.SECONDS)
            assertEquals(setOf("room-1"), scheduler.runningIds())
            assertEquals(true, scheduler.cancelAndJoin("room-1"))
            assertEquals(EndReason.Cancelled, ended.get(5, TimeUnit.SECONDS))
            assertEquals(emptySet(), scheduler.activeIds())
        } finally {
            scheduler.shutdown()
        }
    }
}
