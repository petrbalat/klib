package cz.petrbalat.klib.jdk.concurrent

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals

class ConcurrentUtilsTest {

    @Test
    fun runAtomic() {
        var counter = 0

        //nespustí se
        val lock = AtomicBoolean(true)
        runAtomic(lock) {
            counter++
        }
        assertEquals(true, lock.get())
        assertEquals(0, counter)

        //spustí
        lock.set(false)
        runAtomic(lock) {
            counter++
            assertEquals(true, lock.get())
        }
        assertEquals(false, lock.get())
        assertEquals(1, counter)
    }

    @Test
    fun runAtomicAndResult() {
        var counter = 0

        //nespustí se
        val lock = AtomicBoolean(true)
        var result = runAtomic<Int>(lock) {
            counter++
        }
        assertEquals(true, lock.get())
        assertEquals(0, counter)

        //spustí
        lock.set(false)
        result = runAtomic<Int>(lock) {
            counter++
            assertEquals(true, lock.get())
            counter
        }
        assertEquals(false, lock.get())
        assertEquals(result, counter)
    }
}
