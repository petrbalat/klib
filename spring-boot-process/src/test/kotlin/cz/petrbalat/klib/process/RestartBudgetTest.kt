package cz.petrbalat.klib.process

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Rozhodování watchdogu bez procesů — integrační chování pokrývá `ManagedProcessTest`. */
class RestartBudgetTest {

    @Test
    fun `NEVER se vzdá hned po prvním konci`() {
        val budget = RestartBudget(RestartPolicy.NEVER)
        assertNull(budget.next())
        assertEquals(0, budget.restarts)
    }

    @Test
    fun `restarty se číslují do limitu, pak null`() {
        val budget = RestartBudget(RestartPolicy(maxRestarts = 2))
        assertEquals(1, budget.next())
        assertEquals(2, budget.next())
        assertNull(budget.next())
        assertEquals(2, budget.restarts)
    }

    @Test
    fun `stabilní běh počítadlo vynuluje, krátký ne`() {
        val budget = RestartBudget(RestartPolicy(maxRestarts = 2, stableRun = 5.minutes))
        budget.next()
        budget.next()

        assertFalse(budget.resetIfStable(10.seconds))
        assertNull(budget.next())

        assertTrue(budget.resetIfStable(5.minutes))
        assertEquals(0, budget.restarts)
        assertEquals(1, budget.next())
    }

    @Test
    fun `bez restartů se nuluje nic - false, ať watchdog neloguje reset`() {
        val budget = RestartBudget(RestartPolicy(stableRun = 1.seconds))
        assertFalse(budget.resetIfStable(1.minutes))
    }
}
