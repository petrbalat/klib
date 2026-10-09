package cz.petrbalat.klib.process

import kotlin.time.Duration

/**
 * Počítadlo restartů watchdogu podle [RestartPolicy] — rozhodnutí „restartovat / vzdát se / vynulovat“
 * bez procesů a času, testovatelné jednotkově. Jedna instance na jedno spuštění [ManagedProcess.start].
 */
internal class RestartBudget(private val policy: RestartPolicy) {

    /** restarty od startu nebo posledního stabilního běhu */
    var restarts: Int = 0
        private set

    val delay: Duration get() = policy.delay

    /** Proces skončil po [ran] — běh aspoň [RestartPolicy.stableRun] počítadlo vynuluje; true = vynulovalo se. */
    fun resetIfStable(ran: Duration): Boolean {
        if (ran < policy.stableRun || restarts == 0) return false
        restarts = 0
        return true
    }

    /** Pořadí dalšího restartu; null = limit vyčerpán → GaveUp. */
    fun next(): Int? = if (restarts >= policy.maxRestarts) null else ++restarts
}
