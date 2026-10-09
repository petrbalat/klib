package cz.petrbalat.klib.process

import kotlinx.coroutines.CompletableDeferred
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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ProcessDslTest {

    @TempDir
    lateinit var logs: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gaveUp = CopyOnWriteArrayList<String>()

    private val groups by lazy {
        processGroups("dsl-test") {
            logsDirectory = logs
            gracefulStop = 1.seconds
            restart { maxRestarts = 3; delay = 10.milliseconds }
            onGaveUp { group, giveUp -> gaveUp += "${group.id}/${giveUp.name}" }
        }
    }

    @AfterEach
    fun tearDown() {
        groups.close()
        scope.cancel()
    }

    @Test
    fun `command - pevné argumenty, přepínače a volby`() {
        val socket: File? = null
        val command = command("mpv", "--fs") {
            option("--volume=", 50)
            flag("--mute", false)
            flag("--no-osc")
            option("--input-ipc-server=", socket?.path)
            option("--title", "Akce 1")
            arg(File("/media/a.mp4"))
        }
        assertEquals(listOf("mpv", "--fs", "--volume=50", "--no-osc", "--title", "Akce 1", "/media/a.mp4"), command)
        assertEquals(listOf("sudo", "-n", "systemctl", "restart", "x"), sudo(command("systemctl", "restart", "x")))
        assertEquals(listOf("sudo", "-n", "systemctl", "restart", "x"), sudo("systemctl", "restart", "x"))
        assertEquals(listOf("sudo", "-n", "mpv", "--fs"), sudo("mpv") { flag("--fs"); option("--title", null) })
    }

    @Test
    fun `sudo uvnitř builderu procesu`() {
        val spec = processSpec { command(sudo("/usr/bin/systemctl", "restart", "display")) }
        assertEquals(listOf("sudo", "-n", "/usr/bin/systemctl", "restart", "display"), spec.command)
        assertEquals("sudo", spec.name)
    }

    @Test
    fun `processSpec - výchozí hodnoty, env a úprava kopie politiky restartů`() {
        val spec = processSpec {
            command("/usr/bin/chromium", "--kiosk")
            env("DISPLAY", ":0")
            restart { maxRestarts = 10 }
        }
        assertEquals("chromium", spec.name)
        assertEquals(mapOf("DISPLAY" to ":0"), spec.environment)
        assertEquals(RestartPolicy(maxRestarts = 10), spec.restart)
        assertEquals(5.seconds, spec.gracefulStop)

        assertEquals(RestartPolicy.NEVER, processSpec { command("true"); noRestart() }.restart)
        assertFailsWith<IllegalStateException> { processSpec { name = "bez příkazu" } }
    }

    @Test
    fun `start ve skupině dosadí výchozí hodnoty skupiny a log podle názvu`() = runBlocking {
        val group = groups.create("g-1", scope)
        try {
            val process = group.start { command("sleep", "10"); logToFile() }

            assertEquals("sleep", process.spec.name)
            assertEquals(RestartPolicy(maxRestarts = 3, delay = 10.milliseconds), process.spec.restart)
            assertEquals(1.seconds, process.spec.gracefulStop)
            assertEquals(ProcessOutput.ToFile(File(logs, "sleep.log")), process.spec.output)
            assertEquals("dsl-test/g-1", process.spec.environment["KLIB_PROCESS_TAG"])
        } finally {
            group.stop()
        }
    }

    @Test
    fun `onGaveUp procesu i registru dostanou vzdání se`() = runBlocking {
        val group = groups.create("g-2", scope)
        val processGaveUp = CompletableDeferred<GiveUp>()
        group.start("player") {
            command("sh", "-c", "exit 1")
            restart { maxRestarts = 0 }
            onGaveUp { processGaveUp.complete(it) }
        }

        assertEquals("player", withTimeout(5.seconds) { processGaveUp.await() }.name)
        assertEquals(listOf("g-2/player"), gaveUp)
        group.stop()
    }

    @Test
    fun `withGroup zastaví skupinu na konci bloku`() = runBlocking {
        lateinit var process: ManagedProcess
        val result = groups.withGroup("w-1") {
            process = start { command("sleep", "1000") }
            assertIs<ProcessState.Running>(process.state.value)
            42
        }

        assertEquals(42, result)
        assertFalse(process.isAlive)
        assertNull(groups.find("w-1"))
    }

    @Test
    fun `withGroup zastaví skupinu i při zrušení korutiny`() = runBlocking {
        val started = CompletableDeferred<ManagedProcess>()
        val action = scope.launch {
            groups.withGroup("w-2") {
                started.complete(start { command("sleep", "1000") })
                awaitCancellation()
            }
        }
        val process = withTimeout(5.seconds) { started.await() }

        action.cancelAndJoin()
        assertFalse(process.isAlive)
        assertNull(groups.find("w-2"))
    }

    @Test
    fun `withGroup počká na dobíhající skupinu se stejným id`() = runBlocking {
        // předchozí běh akce ještě zastavuje proces, který ignoruje SIGTERM (gracefulStop 1 s)
        val previous = groups.create("action-42", scope)
        previous.start { command("sh", fixture("ignore-term.sh")) }
        val stopping = scope.launch { previous.stop() }
        waitUntil { previous.isStopped }

        groups.withGroup("action-42") {
            assertTrue(stopping.isCompleted)
            assertEquals(this, groups.find("action-42"))
        }
    }

    @Test
    fun `restart v DSL mění kopii výchozí politiky skupiny`() = runBlocking {
        groups.withGroup("r-1") {
            val process = start {
                command("sleep", "10")
                restart { stableRun = 1.minutes }
            }
            assertEquals(RestartPolicy(maxRestarts = 3, delay = 10.milliseconds, stableRun = 1.minutes), process.spec.restart)
        }
    }

    @Test
    fun `runProcess ve withGroup nese tag skupiny, env z bloku ho nepřepíše`() = runBlocking {
        val tags = groups.withGroup("run-1") {
            val implicit = runProcess("sh", "-c", "printf \"\$KLIB_PROCESS_TAG\"").output
            val explicit = runProcess(listOf("sh", "-c", "printf \"\$KLIB_PROCESS_TAG\"")) { env("KLIB_PROCESS_TAG", "jiny/tag") }.output
            implicit to explicit
        }
        assertEquals("dsl-test/run-1" to "dsl-test/run-1", tags)
    }
}
