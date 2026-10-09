package cz.petrbalat.klib.process

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class ProcessGroupTest {

    @TempDir
    lateinit var logs: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tagging = ProcessTagging("test")
    private val gaveUp = CopyOnWriteArrayList<GiveUp>()
    private val restarts = AtomicInteger()
    private val stopped = CopyOnWriteArrayList<String>()

    private val listener = object : ProcessGroupListener {
        override fun restarted(group: ProcessGroup, name: String, restarting: ProcessState.Restarting) {
            restarts.incrementAndGet()
        }

        override fun gaveUp(group: ProcessGroup, giveUp: GiveUp) {
            gaveUp += giveUp
        }

        override fun stopped(group: ProcessGroup) {
            stopped += group.id
        }
    }

    private fun group(id: String = "12-3", defaults: ProcessDefaults = ProcessDefaults(logsDirectory = logs)) = ProcessGroup(
        id, tagging, scope, ProcessReaper(tagging), defaults, listener,
    )

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `proces i jeho potomci dostanou tag skupiny`() {
        val group = group()
        val log = File(logs, "mpv-12.log")

        // potomek (vnořený sh) env dědí — podle toho reaper dorazí sirotky
        group.start("mpv-12") {
            command("sh", "-c", "sh -c 'echo \$KLIB_PROCESS_TAG \$EXTRA'; sleep 5")
            env("EXTRA", "x")
            logToFile()
        }
        try {
            waitUntil { log.exists() && log.readText().isNotBlank() }
            assertEquals("test/12-3 x", log.readText().trim())
        } finally {
            runBlocking { group.stop() }
        }
    }

    @Test
    fun `vzdání se watchdogu dorazí do listeneru s GiveUp, restarty se počítají`() {
        val group = group()
        val log = File(logs, "imv.log")

        group.start {
            command("sh", "-c", "exit 1")
            logTo(log)
            restart { maxRestarts = 2; delay = 10.milliseconds }
        }

        waitUntil { gaveUp.isNotEmpty() }
        val giveUp = gaveUp.single()
        assertEquals("sh", giveUp.name)
        assertEquals(2, giveUp.restarts)
        assertEquals(ProcessOutput.ToFile(log), giveUp.spec.output)
        assertEquals(2, restarts.get())
    }

    @Test
    fun `po stop skupiny se vzdání nehlásí`() {
        val group = group()
        val process = group.start { command("sleep", "10"); noRestart() }

        runBlocking { group.stop() }
        Thread.sleep(300)
        assertEquals(emptyList(), gaveUp)
        assertEquals(ProcessState.Stopped, process.state.value)
        assertEquals(listOf("12-3"), stopped)
        assertFailsWith<IllegalStateException> { group.start { command("sleep", "1") } }
    }

    @Test
    fun `stav skupiny obsahuje všechny procesy podle názvu`() {
        val group = group()
        group.start("player") { command("sleep", "10") }
        group.start("helper") {
            command("sh", fixture("crash-loop.sh"))
            restart { maxRestarts = 0; stableRun = Duration.INFINITE }
        }

        val state = group.state.await { it["helper"] is ProcessState.GaveUp }
        assertIs<ProcessState.Running>(state["player"])

        runBlocking { group.stop() }
        assertEquals(mapOf("player" to ProcessState.Stopped, "helper" to ProcessState.GaveUp(0)), group.state.value)
    }

    @Test
    fun `druhý běžící proces stejného názvu selže a první nechá běžet`() {
        val group = group()
        val first = group.start { command("sleep", "10") }
        try {
            // oba by měly výchozí název „sleep“ — stav skupiny by se přepisoval
            assertFailsWith<IllegalStateException> { group.start { command("sleep", "20") } }
            assertTrue(first.isAlive)
            assertEquals(mapOf("sleep" to first.state.value), group.state.value)
        } finally {
            runBlocking { group.stop() }
        }
    }

    @Test
    fun `start(spec) bere spec beze změn - výchozí hodnoty skupiny dosazuje jen DSL`() {
        val group = group(defaults = ProcessDefaults(restart = RestartPolicy(maxRestarts = 3), gracefulStop = 1.seconds))
        val spec = ProcessSpec(listOf("sleep", "10"), environment = mapOf("A" to "1"))
        try {
            val process = group.start(spec)
            assertEquals(spec.copy(environment = mapOf("A" to "1", "KLIB_PROCESS_TAG" to "test/12-3")), process.spec)
        } finally {
            runBlocking { group.stop() }
        }
    }

    @Test
    fun `logFile bez logs-directory selže`() {
        val group = ProcessGroup("1", tagging, scope, ProcessReaper(tagging), ProcessDefaults(), listener)
        assertFailsWith<IllegalStateException> { group.logFile("x") }
    }

    @Test
    fun `opakovaný stop je no-op`() {
        val group = group()
        runBlocking {
            group.stop()
            group.stop()
        }
        assertTrue(group.isStopped)
        assertEquals(listOf("12-3"), stopped)
    }

    @Test
    fun `souběžný stop se vrátí až po doběhnutí prvního`() {
        val group = group()
        // ignoruje SIGTERM → první stop trvá celý gracefulStop
        val process = group.start("stubborn") {
            command("sh", fixture("ignore-term.sh"))
            noRestart()
            gracefulStop = 500.milliseconds
        }
        val pid = assertIs<ProcessState.Running>(process.state.value).pid
        Thread.sleep(200) // trap musí být nastavený

        val first = scope.launch { group.stop() }
        waitUntil { group.isStopped }
        val took = measureTime { runBlocking { group.stop() } }

        assertFalse(isPidAlive(pid), "druhý stop se vrátil s běžícím procesem")
        assertEquals(listOf("12-3"), stopped, "druhý stop se vrátil před dokončením prvního")
        assertTrue(took >= 200.milliseconds, "druhý stop nečekal: $took")
        runBlocking { first.join() }
    }

    @Test
    fun `stop z onRestarting během souběžného stopu neuvázne`() {
        val group = group()
        val inHook = CompletableFuture<Unit>()
        val hookDone = CompletableFuture<Unit>()
        group.start("crasher") {
            command("sh", "-c", "exit 1")
            restart { delay = 10.seconds }
            onRestarting {
                inHook.complete(Unit)
                // první stop už běží a joinuje tento watchdog — čekání na něj by uváznulo
                waitUntil { group.isStopped }
                runBlocking { group.stop() }
                hookDone.complete(Unit)
            }
        }
        inHook.get(5, TimeUnit.SECONDS)

        val took = measureTime { runBlocking { withTimeout(5.seconds) { group.stop() } } }
        hookDone.get(1, TimeUnit.SECONDS)
        assertTrue(took < 5.seconds, "stop trval $took")
        assertEquals(listOf("12-3"), stopped)
    }

    @Test
    fun `stop zastaví procesy skupiny souběžně, ne N × gracefulStop`() {
        val group = group()
        // ignoruje SIGTERM → každý stop čeká celý gracefulStop
        val processes = (1..3).map { i ->
            group.start("stubborn-$i") {
                command("sh", fixture("ignore-term.sh"))
                noRestart()
                gracefulStop = 1.seconds
            }
        }
        val took = measureTime { runBlocking { group.stop() } }
        assertTrue(took < 2.5.seconds, "stop trval $took")
        processes.forEach { assertFalse(it.isAlive) }
    }

    @Test
    fun `stop volaný ve finally zrušené korutiny procesy přesto zastaví`() {
        val group = group()
        val process = group.start { command("sleep", "1000"); noRestart() }
        val pid = assertIs<ProcessState.Running>(process.state.value).pid

        // běžný vzor konzumenta: úklid ve finally jobu akce; watchdog běží v jiném (dlouho žijícím) scope
        val action = CoroutineScope(Dispatchers.IO).launch {
            try {
                awaitCancellation()
            } finally {
                group.stop()
            }
        }
        runBlocking { action.cancelAndJoin() }

        assertTrue(group.isStopped)
        waitUntil { !isPidAlive(pid) }
        assertFalse(process.isAlive)
        assertEquals(ProcessState.Stopped, process.state.value)
    }

    @Test
    fun `souběžný start a stop skupiny nenechá běžet žádný proces`() {
        // konec akce (stop) se potká se spouštěním dalšího procesu téže akce (segment, helper)
        val started = CopyOnWriteArrayList<ManagedProcess>()
        repeat(RACE_ROUNDS) { round ->
            val group = group("race-$round")
            val barrier = CyclicBarrier(2)
            val result = CompletableFuture<ManagedProcess?>()
            thread {
                barrier.await()
                result.complete(runCatching {
                    group.start { command("sleep", "1000"); restart { delay = 50.milliseconds } }
                }.getOrNull()) // po stop skupiny start vyhodí IllegalStateException — v pořádku
            }
            barrier.await()
            runBlocking { group.stop() }
            result.get(5, TimeUnit.SECONDS)?.let { started += it }
        }

        Thread.sleep(300)
        val leaked = started.filter { it.state.value !is ProcessState.Stopped }
        assertEquals(emptyList(), leaked.map { it.toString() }, "procesy běžící po stop skupiny (${leaked.size}/${started.size})")
    }

    @Test
    fun `po GaveUp jde proces skupiny pod stejným názvem spustit znovu`() {
        val group = group()
        val marker = File(logs, "ready")
        val player: GroupProcessBuilder.() -> Unit = {
            command("sh", "-c", "test -f '${marker.path}' && exec sleep 1000; exit 1")
            noRestart()
        }
        group.start("player", player)
        group.state.await { it["player"] is ProcessState.GaveUp }

        marker.createNewFile()
        val retried = group.start("player", player)

        try {
            assertIs<ProcessState.Running>(group.state.value["player"])
            Thread.sleep(300)
            assertTrue(retried.isAlive)
            assertEquals(1, gaveUp.size)
        } finally {
            runBlocking { group.stop() }
        }
        assertEquals(mapOf("player" to ProcessState.Stopped), group.state.value)
    }

    @Test
    fun `env od volajícího nepřepíše tag skupiny`() {
        val group = group()
        val log = File(logs, "tag.log")
        // jinak by proces unikl úklidu skupiny podle tagu
        group.start {
            command("sh", "-c", "echo \$KLIB_PROCESS_TAG; sleep 5")
            env("KLIB_PROCESS_TAG", "test/other")
            logTo(log)
        }
        try {
            waitUntil { log.exists() && log.readText().isNotBlank() }
            assertEquals("test/12-3", log.readText().trim())
        } finally {
            runBlocking { group.stop() }
        }
    }

    private companion object {
        const val RACE_ROUNDS = 100
    }
}
