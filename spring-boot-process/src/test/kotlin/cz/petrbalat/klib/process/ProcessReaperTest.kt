package cz.petrbalat.klib.process

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Falešný `/proc` — reaper musí zabít jen procesy se shodným tagem a přežít nečitelné záznamy. */
class ProcessReaperTest {

    @TempDir
    lateinit var proc: File

    private val killed = mutableListOf<Long>()

    @BeforeEach
    fun setUp() {
        killed.clear()
        proc.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun reaper(procDirectory: File = proc) = ProcessReaper(ProcessTagging("test"), procDirectory) { killed += it }

    private fun process(pid: Long, vararg env: String) {
        File(proc, pid.toString()).mkdirs()
        File(proc, "$pid/environ").writeBytes(env.joinToString("\u0000", postfix = "\u0000").toByteArray())
    }

    @Test
    fun `zabije jen procesy se shodným tagem`() {
        process(100, "PATH=/usr/bin", "KLIB_PROCESS_TAG=test/5-1")
        process(101, "KLIB_PROCESS_TAG=test/5-2")
        process(102, "KLIB_PROCESS_TAG=test/15-1") // jiná skupina, stejný suffix
        process(103, "KLIB_PROCESS_TAG=other/5-1") // jiný vlastník, stejná skupina
        process(104, "PATH=/usr/bin")

        assertEquals(1, reaper().kill("5-1"))
        assertEquals(listOf(100L), killed)
    }

    @Test
    fun `killAllOwned zabije jen procesy vlastníka`() {
        process(100, "KLIB_PROCESS_TAG=test/5-1")
        process(101, "KLIB_PROCESS_TAG=test/7-2")
        process(102, "KLIB_PROCESS_TAG=other/1")
        process(103, "KLIB_PROCESS_TAG=testing/1") // prefix vlastníka bez lomítka
        process(104, "HOME=/home/pi")

        assertEquals(2, reaper().killAllOwned())
        assertEquals(setOf(100L, 101L), killed.toSet())
    }

    @Test
    fun `vlastní název env proměnné`() {
        process(100, "KLIB_PROCESS_TAG=test/1")
        process(101, "APP_TAG=test/1")

        val reaper = ProcessReaper(ProcessTagging("test", envName = "APP_TAG"), proc) { killed += it }
        assertEquals(1, reaper.kill("1"))
        assertEquals(listOf(101L), killed)
    }

    @Test
    fun `nečitelný environ, skončený proces a nečíselné záznamy přeskočí`() {
        File(proc, "200/environ").mkdirs()   // čtení selže jako u procesu pod sudo
        File(proc, "201").mkdirs()           // proces mezitím skončil
        File(proc, "self").mkdirs()
        process(202, "KLIB_PROCESS_TAG=test/5-1")

        assertEquals(1, reaper().kill("5-1"))
        assertEquals(listOf(202L), killed)
    }

    @Test
    fun `vlastní JVM nezabije, ani když nese tag vlastníka`() {
        // JVM spuštěná z otagovaného procesu (aktualizační skript klienta) env tag zdědí
        val self = ProcessHandle.current().pid()
        process(self, "KLIB_PROCESS_TAG=test/5-1")
        process(100, "KLIB_PROCESS_TAG=test/5-1")

        assertEquals(1, reaper().kill("5-1"))
        assertEquals(1, reaper().killAllOwned())
        assertEquals(listOf(100L, 100L), killed)
    }

    @Test
    fun `bez proc (macOS) je no-op`() {
        assertEquals(0, reaper(File(proc, "neexistuje")).killAllOwned())
    }

    @Test
    fun `readProcessTag bez tagu vrací null`() {
        process(300, "A=1")
        assertNull(readProcessTag(File(proc, "300"), "KLIB_PROCESS_TAG"))
    }
}
