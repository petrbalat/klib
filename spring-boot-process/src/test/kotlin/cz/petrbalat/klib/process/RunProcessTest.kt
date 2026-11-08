package cz.petrbalat.klib.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
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

    /** runProcess spravuje pouze přímý proces; potomek držící zděděnou rouru může přežít. */
    @Test
    fun `potomek držící rouru může přežít konec přímého procesu`() = runBlocking {
        val pidFile = File(dir, "child.pid")
        try {
            val result = runProcess("sh", "-c", "echo result; sleep 1000 & echo \$! > ${pidFile.path}") { timeout = 5.seconds }
            val child = pidFile.readText().trim().toLong()
            assertEquals(0, result.exitValue)
            assertEquals("result", result.output.trim())
            assertTrue(isPidAlive(child), "runProcess nespravuje potomky")
        } finally {
            killPids(pidFile)
        }
    }

    /** Normální konec rodiče nesmí zaměnit nespravované procesy s cizími spuštěními. */
    @Test
    fun `runProcess ponechá potomka i nesouvisející proces běžet`() = runBlocking {
        val pidFile = File(dir, "orphan.pid")
        val unrelated = ProcessBuilder("sleep", "1000").start()
        try {
            val result = runProcess("sh", "-c", "sleep 1000 >/dev/null 2>&1 & echo \$! > '${pidFile.path}'; exit 7") {
                timeout = 5.seconds
            }
            val pid = pidFile.readText().trim().toLong()

            assertEquals(7, result.exitValue)
            assertFalse(result.timedOut)
            assertTrue(isPidAlive(pid), "potomek není součástí spravovaného namespace")
            assertTrue(unrelated.isAlive, "runProcess nesmí zasáhnout cizí proces")
        } finally {
            killPids(pidFile)
            unrelated.destroyForcibly()
        }
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

    /** Timeout zabije přímý proces; oddělený potomek není spravován runProcess. */
    @Test
    fun `timeout zabije přímý proces ale ne odpojeného potomka`() = runBlocking {
        val pidFile = File(dir, "daemon.pid")
        try {
            val result = runProcess("sh", "-c", "(sleep 1000 >/dev/null 2>&1 & echo \$! > '${pidFile.path}'); exec sleep 100") {
                timeout = 500.milliseconds
            }
            val daemon = pidFile.readText().trim().toLong()
            assertTrue(result.timedOut)
            assertTrue(isPidAlive(daemon), "runProcess neukončuje procesy mimo vlastní PID")
        } finally {
            killPids(pidFile)
        }
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
    fun `zrušení korutiny zabije přímý proces ale ne potomka`() = runBlocking {
        val pids = File(dir, "pids")
        // IO — waitUntil níže blokuje vlákno runBlocking
        val job = async(Dispatchers.IO) {
            runProcess("sh", "-c", "sleep 30 & echo \$\$ \$! > '${pids.path}'; wait") { timeout = 30.seconds }
        }
        try {
            waitUntil { pids.exists() && pids.readText().isNotBlank() }
            val (shell, child) = pids.readText().trim().split(" ").map { it.toLong() }
            job.cancelAndJoin()
            delay(100)
            assertFalse(isPidAlive(shell), "shell")
            assertTrue(isPidAlive(child), "potomek není součástí spravovaného procesu")
        } finally {
            job.cancelAndJoin()
            killPids(pids)
        }
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

    /** isolated: potomek držící rouru skončí s příkazem — výsledek bez čekání na OUTPUT_GRACE */
    @Test
    @RequiresPidNamespace
    fun `isolated - konec příkazu ukončí i potomka na pozadí`() = runBlocking {
        val started = System.currentTimeMillis()
        val result = runProcess("sh", "-c", "sleep 1231 & echo result") {
            timeout = 5.seconds
            isolated = true
        }
        assertEquals(0, result.exitValue)
        assertEquals("result", result.output.trim())
        assertTrue(System.currentTimeMillis() - started < 3_000, "výstup nesmí čekat na potomka")
        waitUntil { liveSleeps("1231") == 0 }
    }

    @Test
    @RequiresPidNamespace
    fun `isolated - timeout ukončí celou pipeline`() = runBlocking {
        val result = runProcess("sh", "-c", "sleep 1232 | sleep 1233") {
            timeout = 500.milliseconds
            isolated = true
        }
        assertTrue(result.timedOut)
        waitUntil { liveSleeps("1232") == 0 && liveSleeps("1233") == 0 }
    }

    @Test
    @RequiresPidNamespace
    fun `isolated - zrušení korutiny ukončí namespace`() = runBlocking {
        val job = async(Dispatchers.IO) {
            runProcess("sh", "-c", "sleep 1234 | sleep 1235") { timeout = 30.seconds; isolated = true }
        }
        waitUntil { liveSleeps("1234") == 1 && liveSleeps("1235") == 1 }
        job.cancelAndJoin()
        waitUntil { liveSleeps("1234") == 0 && liveSleeps("1235") == 0 }
    }

    @Test
    @RequiresPidNamespace
    fun `isolated - stdin, env a exit kód projdou wrapperem`() = runBlocking {
        val result = runProcess("sh", "-c", "cat; printf \" \$GREETING\"; exit 3") {
            timeout = 5.seconds
            stdin("ahoj")
            env("GREETING", "světe")
            isolated = true
        }
        assertEquals(3, result.exitValue)
        assertEquals("ahoj světe", result.output)
    }

    /** živé `sleep <argument>` — unikátní délka odliší procesy testu */
    private fun liveSleeps(argument: String): Int = ProcessHandle.allProcesses().filter {
        it.isAlive && it.info().command().orElse("").endsWith("/sleep") &&
            it.info().arguments().orElse(emptyArray()).contentEquals(arrayOf(argument))
    }.count().toInt()

    private fun killPids(file: File) {
        val pids = runCatching { file.readText() }.getOrDefault("")
        pids.split(Regex("\\s+")).mapNotNull(String::toLongOrNull).forEach { pid ->
            ProcessHandle.of(pid).ifPresent { it.destroyForcibly() }
        }
    }
}
