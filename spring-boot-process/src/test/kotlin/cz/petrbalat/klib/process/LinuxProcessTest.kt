package cz.petrbalat.klib.process

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class LinuxProcessTest {

    @TempDir
    lateinit var dir: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val groups = processGroups { shutdownTimeout = 5.seconds }

    @AfterEach
    fun tearDown() {
        groups.close()
        scope.cancel()
    }

    @Test
    @RequiresPidNamespace
    fun `stop skupiny uklidí odpojeného setsid potomka přes PID namespace`() {
        val group = groups.create("detached", scope)
        val ready = File(dir, "ready")
        val process = group.start {
            command("sh", "-c", "setsid sh -c 'exec sleep 1001' </dev/null >/dev/null 2>&1 & touch '${ready.path}'; exec sleep 1000")
            noRestart()
        }
        waitUntil { ready.exists() }
        val detached = awaitDescendant(process, "sleep", "1001")
        assertTrue(detached.isAlive)

        runBlocking { group.stop() }

        waitUntil { !detached.isAlive }
        assertFalse(process.isAlive)
    }

    @Test
    fun `stop skupiny zruší runProcess přímý proces a počká na něj`() = runBlocking {
        val group = groups.create("run-process", scope)
        val pidFile = File(dir, "run-process.pid")
        val task = async(Dispatchers.IO) {
            group.runProcess("sh", "-c", "echo \$\$ > '${pidFile.path}'; exec sleep 1000") {
                timeout = 30.seconds
            }
        }

        try {
            waitUntil { pidFile.exists() && pidFile.readText().isNotBlank() }
            val pid = pidFile.readText().trim().toLong()

            group.stop()

            waitUntil { !isPidAlive(pid) }
            assertTrue(task.isCompleted)
        } finally {
            task.cancel()
            group.stop()
        }
    }

    @Test
    fun `runProcess nepůsobí na cizí procesy`() = runBlocking {
        val unrelated = ProcessBuilder("sleep", "1000").start()
        try {
            assertTrue(runProcess("true").success)
            assertTrue(unrelated.isAlive, "runProcess smí ukončit jen vlastní přímý proces")
        } finally {
            unrelated.destroyForcibly()
            unrelated.waitFor(2, TimeUnit.SECONDS)
        }
    }

    @Test
    @EnabledIf("cz.petrbalat.klib.process.LinuxProcessTest#sudoAvailable")
    fun `runProcess zachovává přímé sudo volání`() = runBlocking {
        assertTrue(runProcess(sudo("/usr/bin/sleep", "0")).success)
    }

    private fun awaitDescendant(process: ManagedProcess, command: String, argument: String): ProcessHandle {
        var descendant: ProcessHandle? = null
        waitUntil(5.seconds) {
            descendant = process.process?.toHandle()?.descendants()?.toList()?.firstOrNull {
                it.isAlive && it.info().command().orElse("").substringAfterLast('/') == command &&
                    argument in it.info().arguments().orElse(emptyArray())
            }
            descendant != null
        }
        return checkNotNull(descendant) { "ManagedProcess has no live $command descendant" }
    }

    companion object {
        @JvmStatic
        fun sudoAvailable(): Boolean = runCatching {
            ProcessBuilder("sudo", "-n", "/usr/bin/sleep", "0").start().waitFor() == 0
        }.getOrDefault(false)
    }
}
