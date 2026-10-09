package cz.petrbalat.klib.process

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Chování, které jde ověřit jen na skutečném Linuxu (`/proc`, setsid, zombie) — na macOS se přeskočí,
 * plně běží v kontejneru (`./gradlew :spring-boot-process:dockerTest`).
 */
@EnabledOnOs(OS.LINUX)
class LinuxProcessTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tagging = ProcessTagging("test")
    private val reaper = ProcessReaper(tagging)
    private val started = mutableListOf<Process>()

    @TempDir
    lateinit var dir: File

    private fun group(id: String) = ProcessGroup(id, tagging, scope, reaper, ProcessDefaults(), object : ProcessGroupListener {})

    @AfterEach
    fun tearDown() {
        started.forEach { it.destroyForcibly() }
        reaper.killAllOwned()
        scope.cancel()
    }

    @Test
    fun `stop skupiny dorazí odpojeného sirotka podle tagu`() {
        val group = group("detach-1")
        val process = group.start { command("sh", fixture("detacher.sh")); noRestart() }
        process.state.await { it is ProcessState.GaveUp } // rodič skončil hned
        waitUntil { taggedPids(tagging.tag("detach-1")).isNotEmpty() }
        val orphans = taggedPids(tagging.tag("detach-1"))
        assertTrue(orphans.all { isPidAlive(it) }, "sirotek po setsid běží dál s tagem skupiny")

        runBlocking { group.stop() }
        waitUntil { orphans.none { isPidAlive(it) } }
    }

    @Test
    @EnabledIf("cz.petrbalat.klib.process.LinuxProcessTest#underInit")
    fun `pod tini nezůstanou zombie osiřelých potomků`() {
        val group = group("zombie-1")
        group.start { command("sh", fixture("detacher.sh")); noRestart() }
        waitUntil { taggedPids(tagging.tag("zombie-1")).isNotEmpty() }

        runBlocking { group.stop() }
        Thread.sleep(300) // tini sirotka uklidí asynchronně
        assertEquals(emptyList(), zombies(), "zombie procesy v kontejneru")
    }

    @Test
    fun `killAllOwned nezabije procesy jiného vlastníka`() {
        val own = startTagged("test/1")
        val foreign = startTagged("other/1")

        assertEquals(1, reaper.killAllOwned())
        waitUntil { !own.isAlive }
        Thread.sleep(100)
        assertTrue(foreign.isAlive, "proces vlastníka other přežije úklid vlastníka test")
    }

    @Test
    @EnabledIf("cz.petrbalat.klib.process.LinuxProcessTest#sudoAvailable")
    fun `proces pod sudo reaper nepřečte, stop skupiny ho ukončí přes strom`() {
        val group = group("sudo-1")
        val process = group.start { command(sudo("/usr/bin/sleep", "1000")); noRestart() }
        val sudoPid = assertIs<ProcessState.Running>(process.state.value).pid
        waitUntil { ProcessHandle.of(sudoPid).get().descendants().count() > 0 || !isPidAlive(sudoPid) }

        // známé omezení: environ procesů roota (sudo, sleep) není čitelný → podle tagu je nenajde
        assertEquals(0, reaper.kill("sudo-1"))
        assertTrue(isPidAlive(sudoPid))

        runBlocking { group.stop() } // SIGTERM sudu, ten ho předá příkazu
        waitUntil { !isPidAlive(sudoPid) }
    }

    @Test
    fun `stovky restartů neprosakují deskriptory ani vlákna JVM`() {
        // kiosk běží měsíce a padající přehrávač se restartuje pořád dokola
        val group = group("soak-1")
        val process = group.start {
            command("sh", "-c", "echo crash; exit 1")
            logTo(File(dir, "soak.log"))
            restart { maxRestarts = SOAK_RESTARTS; delay = 10.milliseconds; stableRun = Duration.INFINITE }
        }

        // základ až po zahřátí (pool vláken IO, process reaper)
        process.state.await(10.seconds) { it is ProcessState.Running && it.restarts >= SOAK_WARMUP }
        val fds = openFds()
        val threads = threadCount()

        process.state.await(60.seconds) { it is ProcessState.GaveUp }
        Thread.sleep(300)
        assertTrue(openFds() - fds <= SOAK_TOLERANCE, "otevřené fd: $fds → ${openFds()}")
        assertTrue(threadCount() - threads <= SOAK_TOLERANCE, "vlákna: $threads → ${threadCount()}")
        runBlocking { group.stop() }
    }

    private fun openFds(): Int = File("/proc/self/fd").list().orEmpty().size

    private fun threadCount(): Int = File("/proc/self/task").list().orEmpty().size

    private fun startTagged(tag: String): Process =
        ProcessBuilder("sleep", "1000")
            .apply { environment()[tagging.envName] = tag }
            .start().also { started += it }

    private fun taggedPids(tag: String): List<Long> =
        File("/proc").listFiles().orEmpty()
            .mapNotNull { it.name.toLongOrNull() }
            .filter { readProcessTag(File("/proc/$it"), tagging.envName) == tag }

    /** stav `Z` z `/proc/<pid>/stat` (třetí pole za názvem v závorkách) */
    private fun zombies(): List<String> =
        File("/proc").listFiles().orEmpty()
            .filter { it.name.toLongOrNull() != null }
            .mapNotNull { dir ->
                val stat = try { File(dir, "stat").readText() } catch (_: IOException) { return@mapNotNull null }
                stat.takeIf { it.substringAfterLast(") ").startsWith("Z") }
            }

    companion object {
        private const val SOAK_RESTARTS = 200
        private const val SOAK_WARMUP = 20
        private const val SOAK_TOLERANCE = 10

        @JvmStatic
        fun underInit(): Boolean = runCatching { File("/proc/1/comm").readText().trim() == "tini" }.getOrDefault(false)

        @JvmStatic
        fun sudoAvailable(): Boolean = runCatching {
            ProcessBuilder("sudo", "-n", "/usr/bin/sleep", "0").start().waitFor() == 0
        }.getOrDefault(false)
    }
}
