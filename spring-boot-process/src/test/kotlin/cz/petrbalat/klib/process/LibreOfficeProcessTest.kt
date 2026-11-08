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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Skutečný LibreOffice Impress s volbami CDC (`buildOfficeCommand`, bez Waylandu → `--headless`) — strom
 * `libreoffice` (skript, exec) → `oosplash` → `soffice.bin`. Každý test má vlastní profil (`-env:UserInstallation`)
 * a adresář dokumentu.
 */
@RequiresDockerTestImage
class LibreOfficeProcessTest {
    @TempDir
    lateinit var dir: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processes = mutableListOf<ManagedProcess>()

    private val profile by lazy { File(dir, "profile") }
    private val presentation by lazy { File(dir, "slides.fodp").apply { writeText(FLAT_PRESENTATION) } }
    private val documentLock by lazy { File(dir, ".~lock.${presentation.name}#") }

    @AfterEach
    fun tearDown() {
        runBlocking { processes.asReversed().forEach { runCatching { it.stop() } } }
        scope.cancel()
    }

    @Test
    fun `stop ukončí oosplash i soffice bin přes SIGTERM`() {
        val managed = start("office")
        val office = awaitOffice(managed)
        val splash = managed.namespaceMain("oosplash")

        val startedAt = TimeSource.Monotonic.markNow()
        runBlocking { managed.stop() }

        assertTrue(startedAt.elapsedNow() < 5.seconds, "LibreOffice skončí na SIGTERM sám, bez eskalace na SIGKILL")
        waitUntil { !office.isAlive && !splash.isAlive }
        assertEquals(ProcessState.Stopped, managed.state.value)
    }

    /**
     * Pád zanechá `.lock` profilu, OSL pipe v /tmp i `.~lock.<dokument>#` (vzniká i s `--view`
     * a `SAL_ENABLE_FILE_LOCKING=0`). Zámek nese URL profilu — stejný profil ho bere za vlastní a restart projde;
     * zámek z jiného profilu by headless start ukončil kódem 0 (proto CDC maže zámky před startem).
     */
    @Test
    fun `po pádu soffice bin watchdog LibreOffice restartuje i se starým zámkem dokumentu`() {
        val managed = start(
            "office-restart",
            restart = RestartPolicy(maxRestarts = 1, delay = 100.milliseconds, stableRun = Duration.INFINITE),
        )
        val crashed = awaitOffice(managed)

        crashed.destroyForcibly()

        managed.state.await(15.seconds) { it is ProcessState.Running && it.restarts == 1 }
        val restarted = awaitOffice(managed)
        assertNotEquals(crashed.pid(), restarted.pid())
        Thread.sleep(2_000)
        assertTrue(restarted.isAlive, "nová inkarnace drží dokument a neskončila")
        assertFalse(crashed.isAlive)
    }

    private fun start(name: String, restart: RestartPolicy = RestartPolicy.NEVER): ManagedProcess = startManaged(
        scope, name, officeCommand(profile, presentation), restart = restart, environment = OFFICE_ENV,
        log = File(dir, "$name.log"),
    ).also { processes += it }

    /**
     * Dokument načtený (jeho `.~lock`) → soffice.bin je konečný: první start nového profilu oosplash spouští
     * soffice.bin dvakrát, dřívější PID by mezitím zanikl.
     */
    private fun awaitOffice(managed: ManagedProcess): ProcessHandle {
        var office: ProcessHandle? = null
        waitUntil(30.seconds) {
            office = managed.liveDescendants("soffice.bin").singleOrNull()
            office != null && documentLock.exists()
        }
        return checkNotNull(office)
    }
}

/** env jako CDC — `.~lock.*#` soubory dokumentu LibreOffice vytváří i tak */
private val OFFICE_ENV = mapOf("SAL_ENABLE_FILE_LOCKING" to "0")

/** volby CDC `buildOfficeCommand` pro prezentaci + headless a profil testu */
private fun officeCommand(profile: File, document: File): List<String> = listOf(
    "libreoffice", "-env:UserInstallation=file://${profile.absolutePath}", "--headless",
    "--impress", "--norestore", "--nologo", "--nodefault", "--view", "--show", document.path,
)

/** nejmenší prezentace (flat ODF) — dvě prázdné stránky */
private const val FLAT_PRESENTATION = """<?xml version="1.0" encoding="UTF-8"?>
<office:document xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
  xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0" office:version="1.3"
  office:mimetype="application/vnd.oasis.opendocument.presentation">
 <office:body><office:presentation><draw:page draw:name="p1"/><draw:page draw:name="p2"/></office:presentation></office:body>
</office:document>
"""
