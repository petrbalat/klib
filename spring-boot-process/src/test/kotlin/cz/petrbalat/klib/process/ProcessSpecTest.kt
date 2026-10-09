package cz.petrbalat.klib.process

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ProcessSpecTest {

    @TempDir
    lateinit var dir: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `výchozí název je název binárky`() {
        assertEquals("mpv", ProcessSpec(listOf("/usr/bin/mpv", "--fs")).name)
    }

    @Test
    fun `prázdný příkaz selže hned při popisu, ne až při spuštění`() {
        assertFailsWith<IllegalArgumentException> { ProcessSpec(emptyList()) }
    }

    @Test
    fun `toString nevypíše hodnoty env - můžou nést tajemství`() {
        val spec = ProcessSpec(listOf("app"), environment = mapOf("TOKEN" to "secret-123"))
        assertFalse("secret-123" in spec.toString())
    }

    @Test
    fun `ManagedProcess běží v pracovním adresáři spec`() {
        val log = File(dir, "pwd.log")
        val work = File(dir, "work").apply { mkdirs() }
        val spec = processSpec {
            command("pwd", "-P")
            workingDirectory = work
            logTo(log)
            noRestart()
        }
        val process = ManagedProcess(spec, scope)
        process.start()
        try {
            waitUntil { log.exists() && log.readText().isNotBlank() }
            assertEquals(work.canonicalPath, log.readText().trim())
        } finally {
            runBlocking { process.stop() }
        }
    }

    @Test
    fun `runProcess v pracovním adresáři`() = runBlocking {
        val result = runProcess("pwd", "-P") { workingDirectory = dir }
        assertEquals(dir.canonicalPath, result.output.trim())
    }
}
