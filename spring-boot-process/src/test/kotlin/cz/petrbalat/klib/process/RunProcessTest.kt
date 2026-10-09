package cz.petrbalat.klib.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RunProcessTest {

    @TempDir
    lateinit var dir: File

    /** Výstup větší než buffer roury (64 KB) nesmí proces zablokovat do timeoutu. */
    @Test
    fun `velký výstup nezablokuje proces`() = runBlocking {
        val result = runProcess("sh", fixture("chatty.sh"), "200000") { timeout = 5.seconds }
        assertEquals(0, result.exitValue)
        assertFalse(result.timedOut)
        assertEquals(200_000, result.output.length)
        assertFalse(result.outputTruncated)
        assertTrue(result.success)
    }

    @Test
    fun `timeout zabije proces`() = runBlocking {
        val started = System.currentTimeMillis()
        val result = runProcess("sleep", "10") { timeout = 300.milliseconds }
        assertTrue(result.timedOut)
        assertFalse(result.success)
        assertTrue(System.currentTimeMillis() - started < 5_000)
    }

    /** Potomek na pozadí drží zděděnou rouru výstupu — výstup procesu se nesmí zahodit a potomek přežít. */
    @Test
    @EnabledOnOs(OS.LINUX)
    fun `potomek držící rouru výstup nezahodí a nepřežije`() = runBlocking {
        val pidFile = File(dir, "child.pid")
        val result = runProcess("sh", "-c", "echo result; sleep 1000 & echo \$! > ${pidFile.path}") { timeout = 5.seconds }
        assertEquals(0, result.exitValue)
        assertEquals("result", result.output.trim())
        waitUntil { !isPidAlive(pidFile.readText().trim().toLong()) }
    }

    /** Upovídaný proces nesmí zaplnit haldu — drží se konec výstupu, kde bývají chyby. */
    @Test
    fun `výstup nad maxOutput se ořízne na konec`() = runBlocking {
        val result = runProcess("sh", "-c", "head -c 100000 /dev/zero | tr '\\0' x; printf END") { maxOutput = 1_000 }
        assertTrue(result.success)
        assertTrue(result.outputTruncated)
        assertEquals(1_000, result.output.length)
        assertTrue(result.output.endsWith("xEND"))
    }

    /** Démon po double-forku (mimo strom, rouru nedrží) — dřív přežil timeout, i když zrušení korutiny ne. */
    @Test
    @EnabledOnOs(OS.LINUX)
    fun `timeout zabije i odpojeného potomka mimo strom`() = runBlocking {
        val pidFile = File(dir, "daemon.pid")
        val result = runProcess("sh", "-c", "(sleep 1000 >/dev/null 2>&1 & echo \$! > '${pidFile.path}'); sleep 100") {
            timeout = 500.milliseconds
        }
        assertTrue(result.timedOut)
        val daemon = pidFile.readText().trim().toLong()
        waitUntil { !isPidAlive(daemon) }
    }

    @Test
    fun `stdin se předá procesu`() = runBlocking {
        val result = runProcess("cat") { stdin("ahoj světe") }
        assertEquals("ahoj světe", result.output)
    }

    @Test
    fun `bez stdin proces nečeká na vstup`() = runBlocking {
        val result = runProcess("cat") { timeout = 5.seconds }
        assertFalse(result.timedOut)
        assertEquals("", result.output)
    }

    @Test
    fun `exit kód, stderr a environment`() = runBlocking {
        val result = runProcess("sh", "-c", "echo \$GREETING >&2; exit 7") { env("GREETING", "hi") }
        assertEquals(7, result.exitValue)
        assertEquals("hi", result.output.trim())
    }

    @Test
    fun `zrušení korutiny zabije proces i potomky`() = runBlocking {
        val pids = File(dir, "pids")
        // IO — waitUntil níže blokuje vlákno runBlocking
        val job = async(Dispatchers.IO) {
            runProcess("sh", "-c", "sleep 30 & echo \$\$ \$! > '${pids.path}'; wait") { timeout = 30.seconds }
        }
        waitUntil { pids.exists() && pids.readText().isNotBlank() }
        val (shell, child) = pids.readText().trim().split(" ").map { it.toLong() }

        job.cancelAndJoin()
        delay(100)
        assertFalse(isPidAlive(shell), "shell")
        assertFalse(isPidAlive(child), "potomek")
    }

    @Test
    fun `DSL - parametry v bloku a checkSuccess vrací výsledek`() = runBlocking {
        val output = runProcess("sh", "-c", "cat; printf \" \$GREETING\"") {
            timeout = 5.seconds
            stdin("ahoj")
            env("GREETING", "světe")
        }.checkSuccess().output
        assertEquals("ahoj světe", output)
    }

    @Test
    fun `checkSuccess při chybě vyhodí výjimku s příkazem, kódem a koncem výstupu`() = runBlocking {
        val error = assertFailsWith<ProcessFailedException> {
            runProcess(command("sh", "-c") { arg("echo disk full >&2; exit 3") }) { timeout = 5.seconds }.checkSuccess()
        }
        assertEquals(3, error.result.exitValue)
        assertTrue("exit code 3" in error.message!!)
        assertTrue("disk full" in error.message!!)
    }

    @Test
    fun `checkSuccess po timeoutu`() = runBlocking {
        val error = assertFailsWith<ProcessFailedException> {
            runProcess("sleep", "10") { timeout = 200.milliseconds }.checkSuccess()
        }
        assertTrue(error.result.timedOut)
        assertTrue("timed out" in error.message!!)
    }
}
