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
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Skutečné Chromium s kiosk volbami CDC (bez Waylandu → `--headless`) — strom browser/zygote/renderer,
 * vlastní SIGTERM handler a ProcessSingleton v profilu. Připravenost = `DevToolsActivePort` v profilu (jako CdpClient).
 */
@RequiresDockerTestImage
class ChromiumProcessTest {
    @TempDir
    lateinit var dir: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processes = mutableListOf<ManagedProcess>()

    @AfterEach
    fun tearDown() {
        runBlocking { processes.asReversed().forEach { runCatching { it.stop() } } }
        scope.cancel()
    }

    @Test
    fun `stop ukončí Chromium přes SIGTERM včetně zygote a rendereru`() {
        val profile = File(dir, "profile")
        val managed = start("chromium", profile, gracefulStop = 10.seconds)
        awaitDevTools(profile)
        val tree = managed.liveDescendants("chromium")
        assertTrue(tree.size >= 3, "browser + zygote + renderer: ${tree.size}")

        val startedAt = TimeSource.Monotonic.markNow()
        runBlocking { managed.stop() }

        assertTrue(startedAt.elapsedNow() < 5.seconds, "Chromium skončí na SIGTERM sám, bez eskalace na SIGKILL")
        waitUntil { tree.none { it.isAlive } }
        assertEquals(ProcessState.Stopped, managed.state.value)
    }

    /** Pád zanechá v profilu SingletonLock `<host>-2` — nová inkarnace (zase PID 2) ho musí vzít jako osiřelý. */
    @Test
    fun `po pádu browser procesu watchdog restartuje Chromium se stejným profilem`() {
        val profile = File(dir, "profile")
        val managed = start(
            "chromium-restart", profile,
            restart = RestartPolicy(maxRestarts = 1, delay = 100.milliseconds, stableRun = Duration.INFINITE),
        )
        val devTools = awaitDevTools(profile)
        val browser = managed.namespaceMain("chromium")
        devTools.delete()

        browser.destroyForcibly()

        managed.state.await(15.seconds) { it is ProcessState.Running && it.restarts == 1 }
        assertTrue(Files.isSymbolicLink(File(profile, "SingletonLock").toPath()))
        awaitDevTools(profile)
        assertFalse(browser.isAlive)
        assertTrue(managed.namespaceMain("chromium").isAlive)
    }

    /**
     * Charakterizace: ProcessSingleton čte PID ze SingletonLock a v cizím namespace se trefí do sebe → zámek
     * považuje za osiřelý a druhá instance nad stejným profilem běží (bez namespace skončí kódem 21).
     * Souběžná Chromia proto musí mít vlastní `--user-data-dir` — Chromium to v namespace samo nehlídá.
     */
    @Test
    fun `dvě instance se stejným profilem v různých namespace běží obě`() {
        val profile = File(dir, "profile")
        val first = start("chromium-first", profile)
        val devTools = awaitDevTools(profile)
        devTools.delete()

        val second = start("chromium-second", profile)

        awaitDevTools(profile)
        Thread.sleep(1_000)
        assertTrue(first.isAlive)
        assertTrue(second.isAlive, "druhou instanci ProcessSingleton v namespace nezastaví")
        assertEquals(ProcessState.Running(second.process!!.pid(), 0), second.state.value)
    }

    private fun start(
        name: String,
        profile: File,
        gracefulStop: Duration = 10.seconds,
        restart: RestartPolicy = RestartPolicy.NEVER,
    ): ManagedProcess = startManaged(
        scope, name, chromiumCommand(profile), gracefulStop, restart, log = File(dir, "$name.log"),
    ).also { processes += it }

    private fun awaitDevTools(profile: File): File {
        val file = File(profile, "DevToolsActivePort")
        waitUntil(20.seconds) { file.length() > 0 }
        return file
    }
}

/** kiosk volby CDC (`chromiumBaseArgs`) bez Waylandu, kiosk UI a GPU */
private fun chromiumCommand(profile: File): List<String> = listOf(
    "chromium", "--headless",
    "--no-sandbox", "--disable-setuid-sandbox",
    "--renderer-process-limit=1",
    "--incognito", "--noerrdialogs", "--no-first-run", "--no-default-browser-check",
    "--user-data-dir=${profile.path}",
    "--remote-debugging-port=0",
    "about:blank",
)
