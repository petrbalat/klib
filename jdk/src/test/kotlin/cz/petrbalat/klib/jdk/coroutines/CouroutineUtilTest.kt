package cz.petrbalat.klib.jdk.coroutines

import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/**
 * Created by Petr Balat
 */
class CouroutineUtilTest {

    @ExperimentalTime
    @Test
    fun testtryAgain(): Unit = runBlocking {
        val pom = AtomicBoolean(false)
        val counter = AtomicInteger(0)

        val test = tryAgain(100.milliseconds) {
            counter.incrementAndGet()
            pom.get()
        }
        assertFalse(test)
        assertEquals(2, counter.get())

        counter.set(0)
        val test2 = tryAgain(100.milliseconds, maxAttempt = 3) {
            counter.incrementAndGet()
            if (counter.compareAndSet(3, 3)) {
                return@tryAgain true
            }
            pom.get()
        }
        assertTrue(test2)
        assertEquals(3, counter.get())
    }
}
