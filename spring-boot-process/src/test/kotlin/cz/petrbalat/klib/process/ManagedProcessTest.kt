package cz.petrbalat.klib.process

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Watchdog [ManagedProcess] — limit restartů, jeho nulování po stabilním běhu, stav a stop.
 * Reálný čas s krátkými zpožděními; proces počítá svá spuštění řádky do souboru.
 */
class ManagedProcessTest {

    @TempDir
    lateinit var dir: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var managed: ManagedProcess? = null

    @AfterEach
    fun tearDown() {
        runBlocking { managed?.stop() }
        scope.cancel()
    }

    private fun counting(starts: File, tail: String = "exit 1") =
        listOf("sh", "-c", "echo x >> '${starts.path}'; $tail")

    /** spustí proces popsaný v [spec]; tearDown ho zastaví */
    private fun startProcess(
        scope: CoroutineScope = this.scope,
        onStateChange: (ProcessState) -> Unit = {},
        spec: ProcessSpecBuilder.() -> Unit,
    ): ManagedProcess = ManagedProcess(processSpec(spec), scope, onStateChange).also { managed = it; it.start() }

    @Test
    fun `bez stabilního běhu se po vyčerpání limitu restartů vzdá`() {
        val starts = File(dir, "starts")
        val process = startProcess {
            command(counting(starts))
            restart { maxRestarts = 2; delay = RESTART_DELAY; stableRun = Duration.INFINITE }
        }

        process.state.await { it is ProcessState.GaveUp }
        Thread.sleep(SETTLE_MS)
        assertEquals(3, starts.lineCount(), "1 start + 2 restarty, pak konec")
    }

    @Test
    fun `velký log se při startu odsune do log_1`() {
        val log = File(dir, "mpv-1.log").apply { writeText("x".repeat(100)) }
        startProcess {
            command("sh", "-c", "echo novy")
            logTo(log, maxBytes = 50)
            noRestart()
        }

        waitUntil { log.exists() && log.readText().contains("novy") }
        assertEquals("x".repeat(100), File(dir, "mpv-1.log.1").readText())
        assertEquals("novy", log.readText().trim())
    }

    @Test
    fun `GaveUp přijde do onStateChange právě jednou`() {
        val starts = File(dir, "starts")
        val gaveUp = CopyOnWriteArrayList<ProcessState.GaveUp>()
        startProcess(onStateChange = { if (it is ProcessState.GaveUp) gaveUp += it }) {
            command(counting(starts))
            restart { maxRestarts = 2; delay = RESTART_DELAY; stableRun = Duration.INFINITE }
        }

        waitUntil { gaveUp.isNotEmpty() }
        Thread.sleep(SETTLE_MS)
        assertEquals(3, starts.lineCount())
        assertEquals(listOf(ProcessState.GaveUp(restarts = 2)), gaveUp)
    }

    @Test
    fun `po stop GaveUp nepřijde`() {
        val gaveUp = AtomicInteger()
        val process = startProcess(onStateChange = { if (it is ProcessState.GaveUp) gaveUp.incrementAndGet() }) {
            command("sleep", "10")
            noRestart()
        }

        runBlocking { process.stop() }
        Thread.sleep(SETTLE_MS)
        assertEquals(0, gaveUp.get())
    }

    @Test
    fun `zrušení scope zastaví proces a watchdog ho už nerestartuje`() {
        val starts = File(dir, "starts")
        val ownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val process = startProcess(ownScope) {
            command(counting(starts, "sleep 10"))
            restart { delay = RESTART_DELAY }
        }
        waitUntil { starts.lineCount() == 1 }

        runBlocking { ownScope.coroutineContext.job.cancelAndJoin() }
        assertFalse(process.isAlive, "po zrušení scope proces neběží")
        assertEquals(ProcessState.Stopped, process.state.value)
        Thread.sleep(SETTLE_MS)
        assertEquals(1, starts.lineCount(), "zastavení není pád — žádný restart")
    }

    @Test
    fun `start do už zrušeného scope proces hned zastaví`() {
        val cancelled = CoroutineScope(Job().apply { cancel() })
        val process = startProcess(cancelled) { command("sleep", "10") }

        process.state.await { it == ProcessState.Stopped }
        assertFalse(process.isAlive, "watchdog musí proces zastavit i ve zrušeném scope")
    }

    @Test
    fun `pád po stabilním běhu nuluje počítadlo a restartuje dál`() {
        val starts = File(dir, "starts")
        val states = CopyOnWriteArrayList<ProcessState>()
        startProcess(onStateChange = { states += it }) {
            command(counting(starts, "sleep 0.2; exit 1"))
            // každý běh (200 ms) je „stabilní“
            restart { maxRestarts = 1; delay = RESTART_DELAY; stableRun = 100.milliseconds }
        }

        waitUntil { starts.lineCount() >= 5 }
        assertTrue(starts.lineCount() >= 5, "s maxRestarts=1 by bez nulování skončil po 2 spuštěních")
        val restarting = states.filterIsInstance<ProcessState.Restarting>()
        assertTrue(restarting.size >= 3)
        assertTrue(restarting.all { it.restarts == 1 && it.exitCode == 1 }, "po nulování je každý restart první: $restarting")
    }

    @Test
    fun `přechody stavu Idle - Running - Restarting - Running - GaveUp`() {
        val states = CopyOnWriteArrayList<ProcessState>()
        val spec = processSpec {
            command("sh", fixture("crash-loop.sh"), "0.1")
            restart { maxRestarts = 1; delay = RESTART_DELAY; stableRun = Duration.INFINITE }
        }
        val process = ManagedProcess(spec, scope, onStateChange = { states += it })
        assertEquals(ProcessState.Idle, process.state.value)
        managed = process
        process.start()

        process.state.await { it is ProcessState.GaveUp }
        assertEquals(4, states.size, "$states")
        assertIs<ProcessState.Running>(states[0]).also { assertEquals(0, it.restarts) }
        assertEquals(ProcessState.Restarting(restarts = 1, exitCode = 3), states[1])
        assertIs<ProcessState.Running>(states[2]).also { assertEquals(1, it.restarts) }
        assertEquals(ProcessState.GaveUp(restarts = 1), states[3])
    }

    @Test
    fun `stop přepne stav na Stopped`() {
        val process = startProcess { command("sleep", "10") }
        val running = assertIs<ProcessState.Running>(process.state.value)
        assertTrue(isPidAlive(running.pid))

        runBlocking { process.stop() }
        assertEquals(ProcessState.Stopped, process.state.value)
        assertFalse(isPidAlive(running.pid))
    }

    @Test
    fun `stop zabije celý strom procesu`() {
        val process = startProcess { command("sh", fixture("forker.sh")) }
        val root = ProcessHandle.of(assertIs<ProcessState.Running>(process.state.value).pid).get()
        waitUntil { root.descendants().count() >= 2 }
        val children: List<ProcessHandle> = root.descendants().toList()

        runBlocking { process.stop() }
        waitUntil { children.none { it.isAlive } }
    }

    @Test
    fun `proces ignorující SIGTERM dostane po gracefulStop SIGKILL`() {
        val process = startProcess {
            command("sh", fixture("ignore-term.sh"))
            gracefulStop = 300.milliseconds
        }
        val pid = assertIs<ProcessState.Running>(process.state.value).pid
        Thread.sleep(SETTLE_MS) // trap musí být nastavený

        val started = System.currentTimeMillis()
        runBlocking { process.stop() }
        assertFalse(isPidAlive(pid))
        assertTrue(System.currentTimeMillis() - started >= 300, "SIGKILL až po gracefulStop")
    }

    @Test
    fun `selhání spuštění při restartu watchdog neukončí`() {
        // skript se při prvním běhu smaže → restart selže (IOException); po obnovení skriptu musí další pokus proběhnout
        val script = File(dir, "run.sh")
        val marker = File(dir, "restarted")
        script.writeText("#!/bin/sh\nrm -f \"$0\"\nexit 1\n")
        script.setExecutable(true)

        val process = startProcess {
            command(script.path)
            restart { delay = RESTART_DELAY }
        }

        waitUntil { !script.exists() }
        Thread.sleep(RESTART_DELAY.inWholeMilliseconds * 3) // pár neúspěšných pokusů o restart
        assertEquals(null, (process.state.value as? ProcessState.Restarting)?.exitCode, "selhané spuštění nemá exit kód")
        script.writeText("#!/bin/sh\ntouch '${marker.path}'\nsleep 60\n")
        script.setExecutable(true)

        waitUntil { marker.exists() }
    }

    @Test
    @EnabledOnOs(OS.LINUX) // potomky mimo strom rodiče najde jen /proc
    fun `po pádu rodiče se před restartem zastaví i jeho potomci`() {
        val pids = File(dir, "pids")
        val process = startProcess {
            command("sh", fixture("orphan-on-crash.sh"), pids.path)
            restart { maxRestarts = 1; delay = RESTART_DELAY; stableRun = Duration.INFINITE }
        }

        try {
            process.state.await { it is ProcessState.GaveUp }
            waitUntil { pids.lineCount() == 2 }
            val (crashed, last) = pids.readLines().map { it.trim().toLong() }
            // jinak nová instance narazí na zámek profilu / obsazené zařízení starého přehrávače
            waitUntil(2.seconds) { !isPidAlive(crashed) }
            assertTrue(isPidAlive(last), "po GaveUp pozůstatky zůstávají (odpojený launcher s RestartPolicy.NEVER)")

            runBlocking { process.stop() }
            waitUntil(2.seconds) { !isPidAlive(last) }
        } finally {
            pids.readLines().forEach { pid -> ProcessHandle.of(pid.trim().toLong()).ifPresent { it.destroyForcibly() } }
        }
    }

    @Test
    fun `SIGKILL zvenku (OOM killer) watchdog pozná a proces restartuje`() {
        val states = CopyOnWriteArrayList<ProcessState>()
        val process = startProcess(onStateChange = { states += it }) {
            command("sleep", "1000")
            restart { delay = RESTART_DELAY }
        }
        val first = assertIs<ProcessState.Running>(process.state.value).pid

        ProcessHandle.of(first).get().destroyForcibly()

        val restarted = assertIs<ProcessState.Running>(process.state.await { it is ProcessState.Running && it.pid != first })
        assertEquals(1, restarted.restarts)
        assertTrue(isPidAlive(restarted.pid))
        // 128 + SIGKILL(9)
        assertEquals(ProcessState.Restarting(restarts = 1, exitCode = 137), states[1], "$states")
    }

    @Test
    fun `čisté ukončení s kódem 0 se restartuje jako pád`() {
        // uživatel zavře kiosek (Alt+F4, Ctrl+W na dotykové klávesnici) — obrazovka nesmí zůstat prázdná
        val starts = File(dir, "starts")
        val states = CopyOnWriteArrayList<ProcessState>()
        val process = startProcess(onStateChange = { states += it }) {
            command(counting(starts, "exit 0"))
            restart { maxRestarts = 2; delay = RESTART_DELAY; stableRun = Duration.INFINITE }
        }

        process.state.await { it is ProcessState.GaveUp }
        assertEquals(3, starts.lineCount(), "1 start + 2 restarty")
        assertEquals(
            listOf(ProcessState.Restarting(1, 0), ProcessState.Restarting(2, 0)),
            states.filterIsInstance<ProcessState.Restarting>(),
        )
    }

    @Test
    fun `po GaveUp jde proces znovu spustit`() {
        // záchranná obrazovka „Zkusit znovu“ — příčina pádu (chybějící soubor, displej) mezitím zmizela
        val marker = File(dir, "ready")
        val gaveUp = AtomicInteger()
        val process = startProcess(onStateChange = { if (it is ProcessState.GaveUp) gaveUp.incrementAndGet() }) {
            command("sh", "-c", "test -f '${marker.path}' && exec sleep 1000; exit 1")
            restart { maxRestarts = 1; delay = RESTART_DELAY; stableRun = Duration.INFINITE }
        }
        process.state.await { it is ProcessState.GaveUp }

        marker.createNewFile()
        process.start()

        val running = assertIs<ProcessState.Running>(process.state.value)
        assertEquals(0, running.restarts, "nový start začíná s čistým počítadlem")
        Thread.sleep(SETTLE_MS)
        assertTrue(process.isAlive)
        assertEquals(1, gaveUp.get())

        runBlocking { process.stop() }
        assertEquals(ProcessState.Stopped, process.state.value)
        assertFalse(isPidAlive(running.pid))
    }

    @Test
    fun `crash-loop s upovídaným výstupem nezaplní disk - log a log_1 omezené`() {
        // padající přehrávač, který při každém startu vypíše hromadu varování (SD karta kiosku)
        val log = File(dir, "player.log")
        val maxBytes = 1_000L
        val perRun = 300L
        val process = startProcess {
            command("sh", fixture("chatty.sh"), perRun.toString())
            logTo(log, maxBytes)
            restart { maxRestarts = 20; delay = RESTART_DELAY; stableRun = Duration.INFINITE }
        }

        process.state.await(10.seconds) { it is ProcessState.GaveUp }
        val rotated = File(dir, "player.log.1")
        assertEquals(setOf("player.log", "player.log.1"), dir.list().orEmpty().toSet())
        // rotuje se jen při (re)startu → navíc nejvýš výstup jednoho běhu
        assertTrue(log.length() in 1..maxBytes + perRun, "log: ${log.length()} B")
        assertTrue(rotated.length() in maxBytes + 1..maxBytes + perRun, "log.1: ${rotated.length()} B")
    }

    @Test
    fun `start z onStateChange při GaveUp - watchdog předchozího spuštění nový proces nezastaví`() {
        // automatický nový pokus po vyčerpání restartů — finally předchozího watchdogu dřív zabil nový proces
        val marker = File(dir, "ready")
        val retried = AtomicBoolean()
        lateinit var process: ManagedProcess
        val spec = processSpec {
            command("sh", "-c", "test -f '${marker.path}' && exec sleep 1000; exit 1")
            noRestart()
        }
        process = ManagedProcess(
            spec,
            scope = scope,
            onStateChange = {
                if (it is ProcessState.GaveUp && retried.compareAndSet(false, true)) {
                    marker.createNewFile()
                    process.start()
                }
            },
        )
        managed = process
        process.start() // až po přiřazení — listener ho čte z vlákna watchdogu

        waitUntil { retried.get() }
        Thread.sleep(SETTLE_MS)
        val running = assertIs<ProcessState.Running>(process.state.value)
        assertTrue(process.isAlive)
        assertTrue(isPidAlive(running.pid))
    }

    @Test
    fun `start běžícího procesu selže a proces nechá běžet`() {
        val process = startProcess { command("sleep", "10") }
        val running = assertIs<ProcessState.Running>(process.state.value)

        assertFailsWith<IllegalStateException> { process.start() }
        assertEquals(running, process.state.value)
        assertTrue(isPidAlive(running.pid))
    }

    companion object {
        private val RESTART_DELAY = 50.milliseconds
        private const val SETTLE_MS = 500L
    }
}
