package cz.petrbalat.klib.process

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@RequiresPidNamespace
class PidNamespaceManagedProcessTest {
    @TempDir
    lateinit var dir: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processes = mutableListOf<ManagedProcess>()

    @AfterEach
    fun tearDown() {
        runBlocking {
            processes.asReversed().forEach { process ->
                runCatching { process.stop() }
                process.process?.takeIf { it.isAlive }?.destroyForcibly()
            }
        }
        scope.cancel()
    }

    @Test
    fun `startovní kontrola namespace projde v CDC-like runtime`() {
        checkPidNamespaceSupport()
    }

    /** vlastní /proc — PID z getpid() uvnitř namespace odpovídá /proc, cizí procesy kontejneru nejsou vidět */
    @Test
    fun `namespace vidí vlastní proc`() = runBlocking {
        val result = runProcess("sh", "-c", "cat /proc/\$\$/comm; ls /proc | grep -c '^[0-9]'") {
            timeout = 5.seconds
            isolated = true
        }
        val (comm, count) = result.checkSuccess().output.trim().lines()
        assertEquals("sh", comm)
        assertTrue(count.toInt() <= 4, "v /proc jsou jen procesy namespace: $count")
    }

    @Test
    fun `ManagedProcess posílá SIGTERM vnitřnímu tini a uklidí namespace`() {
        val ready = File(dir, "term.ready")
        val term = File(dir, "term.received")
        val managed = start(
            "pid-namespace-term",
            "trap 'printf term > '${term.absolutePath}'; exit 0' TERM; printf ready > '${ready.absolutePath}'; while :; do sleep 0.05; done",
            gracefulStop = 2.seconds,
        )
        waitUntil { ready.exists() }
        val init = awaitInit(managed)
        val child = awaitChild(managed, "sleep")
        val nspids = File("/proc/${init.pid()}/status").readLines()
            .first { it.startsWith("NSpid:") }
            .substringAfter(':')
            .trim()
            .split(Regex("\\s+"))
        assertEquals("1", nspids.last(), "vnitřní tini musí být PID 1 izolovaného namespace")

        runBlocking { managed.stop() }

        assertEquals("term", term.readText())
        assertFalse(managed.isAlive)
        waitUntil { !init.isAlive && !child.isAlive }
        assertEquals(ProcessState.Stopped, managed.state.value)
    }

    @Test
    fun `Duration ZERO zabije wrapper bez SIGTERM`() {
        val ready = File(dir, "zero.ready")
        val term = File(dir, "zero.term")
        val managed = start(
            "pid-namespace-zero",
            "trap 'touch '${term.absolutePath}'; exit 0' TERM; touch '${ready.absolutePath}'; while :; do sleep 0.05; done",
            gracefulStop = Duration.ZERO,
        )
        waitUntil { ready.exists() }
        val child = awaitChild(managed, "sleep")

        runBlocking { managed.stop() }

        assertFalse(term.exists(), "nulová lhůta nesmí poslat SIGTERM")
        assertFalse(managed.isAlive)
        waitUntil { !child.isAlive }
    }

    @Test
    fun `zrušení scope uklidí celou pipeline čtoucí text ze souboru`() {
        val input = File(dir, "pipeline-input.txt").apply { writeText("test pipeline") }
        val managed = start(
            "pid-namespace-pipeline",
            "cat '${input.path}' | sleep 1000",
            gracefulStop = Duration.ZERO,
        )
        val consumer = awaitChild(managed, "sleep")

        scope.cancel()

        managed.state.await { it == ProcessState.Stopped }
        waitUntil { !consumer.isAlive }
        assertFalse(managed.isAlive)
    }

    @Test
    fun `ignorovaný SIGTERM eskaluje na zabití wrapperu a namespace`() {
        val ready = File(dir, "ignore-term.ready")
        val managed = start(
            "pid-namespace-ignore-term",
            "trap '' TERM; touch '${ready.absolutePath}'; while :; do sleep 0.05; done",
            gracefulStop = 150.milliseconds,
        )
        waitUntil { ready.exists() }
        val child = awaitChild(managed, "sleep")

        runBlocking { managed.stop() }

        assertFalse(managed.isAlive)
        waitUntil { !child.isAlive }
        assertEquals(ProcessState.Stopped, managed.state.value)
    }

    @Test
    fun `restart spustí další inkarnaci také v PID namespace`() {
        val starts = File(dir, "starts")
        val ready = File(dir, "restart.ready")
        val script = "n=0; test ! -f '${starts.absolutePath}' || n=\$(cat '${starts.absolutePath}'); n=\$((n+1)); printf '%s' \"\$n\" > '${starts.absolutePath}'; if [ \"\$n\" -eq 1 ]; then exit 7; fi; touch '${ready.absolutePath}'; exec sleep 1000"
        val managed = start(
            "pid-namespace-restart",
            script,
            gracefulStop = 2.seconds,
            restart = RestartPolicy(maxRestarts = 1, delay = 50.milliseconds, stableRun = Duration.INFINITE),
        )

        waitUntil { ready.exists() }
        val running = assertIs<ProcessState.Running>(managed.state.await { it is ProcessState.Running && it.restarts == 1 })
        assertEquals(1, running.restarts)
        assertEquals("2", starts.readText())
        assertTrue(awaitInit(managed).isAlive)

        runBlocking { managed.stop() }
        assertFalse(managed.isAlive)
    }

    @Test
    fun `konec launcheru uklidí setsid potomka bez ohledu na exit kód`() {
        listOf(0, 7).forEach { exitCode ->
            val ready = File(dir, "launcher-$exitCode.ready")
            val finish = File(dir, "launcher-$exitCode.finish")
            val script = "setsid sh -c 'exec sleep 1000' >/dev/null 2>&1 </dev/null & touch '${ready.path}'; while test ! -f '${finish.path}'; do sleep 0.01; done; exit $exitCode"
            val managed = start("pid-namespace-launcher-$exitCode", script, gracefulStop = 1.seconds)
            val wrapper = managed.process!!
            waitUntil { ready.exists() }
            val detached = awaitChild(managed, "sleep", "1000")

            finish.createNewFile()
            assertTrue(wrapper.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(exitCode, wrapper.exitValue())
            waitUntil { !detached.isAlive }
        }
    }

    private fun start(
        name: String,
        script: String,
        gracefulStop: Duration,
        restart: RestartPolicy = RestartPolicy.NEVER,
    ): ManagedProcess = ManagedProcess(
        ProcessSpec(
            command = listOf("sh", "-c", script),
            name = name,
            restart = restart,
            gracefulStop = gracefulStop,
        ),
        scope,
    ).also { processes += it; it.start() }

    private fun awaitInit(managed: ManagedProcess): ProcessHandle {
        var init: ProcessHandle? = null
        waitUntil(5.seconds) {
            init = managed.process?.toHandle()?.children()?.toList()?.firstOrNull {
                it.isAlive && it.info().command().orElse("").substringAfterLast('/') == "tini"
            }
            init != null
        }
        return checkNotNull(init) { "ManagedProcess did not start namespace tini" }
    }

    private fun awaitChild(managed: ManagedProcess, command: String, argument: String? = null): ProcessHandle {
        var child: ProcessHandle? = null
        waitUntil(5.seconds) {
            child = managed.process?.toHandle()?.descendants()?.toList()?.firstOrNull {
                it.isAlive && it.info().command().orElse("").substringAfterLast('/') == command &&
                    (argument == null || argument in it.info().arguments().orElse(emptyArray()))
            }
            child != null
        }
        return checkNotNull(child) { "ManagedProcess has no live $command child" }
    }
}
