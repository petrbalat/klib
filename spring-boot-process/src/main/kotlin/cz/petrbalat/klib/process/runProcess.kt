package cz.petrbalat.klib.process

import cz.petrbalat.klib.jdk.virtualThreadDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.io.Reader
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ProcessResult(
    val command: List<String>,
    /** null = proces po timeoutu nešel ukončit ani SIGKILL (nepřerušitelné I/O, zaseknutý síťový mount) */
    val exitValue: Int?,
    /** stdout+stderr; při [outputTruncated] jen konec */
    val output: String,
    val timedOut: Boolean,
    /** výstup přesáhl `maxOutput` — začátek se zahodil */
    val outputTruncated: Boolean = false,
) {
    val success: Boolean get() = !timedOut && exitValue == 0

    /** [ProcessFailedException] s příkazem, kódem a koncem výstupu; jinak vrací this — `runProcess(…).checkSuccess().output` */
    fun checkSuccess(): ProcessResult = if (success) this else throw ProcessFailedException(this)

    override fun toString(): String =
        "ProcessResult(command=$command, exitValue=$exitValue, timedOut=$timedOut, outputTruncated=$outputTruncated, " +
            "output=${output.take(200)})"
}

/** Jednorázový příkaz skončil chybou nebo timeoutem — zpráva nese příkaz, kód a konec výstupu (chyby bývají na konci). */
class ProcessFailedException(val result: ProcessResult) : RuntimeException(
    "Command ${result.command} " +
        (if (result.timedOut) "timed out" else "failed with exit code ${result.exitValue}") +
        ": " + result.output.takeLast(500).trim(),
)

/**
 * Jednorázový příkaz bez shellu (argumenty se neinterpretují — žádná injection); `sudo` si přidá volající.
 * Výstup (stdout+stderr) čte souběžně — plná roura (> 64 KB) proces nezablokuje; drží nejvýš [maxOutput] znaků
 * z konce (chyby bývají na konci), zbytek se dočte a zahodí. Na proces, který neukončí ani SIGKILL (nepřerušitelné
 * I/O), nečeká — vrátí `exitValue = null`.
 *
 * Bez [isolated] je to přímé spuštění: timeout i zrušení zabijí jen přímý proces, potomek držící zděděnou rouru
 * (`cmd &`) může přežít — po [OUTPUT_GRACE] se čtení opustí. S [isolated] běží příkaz v PID namespace jako
 * [ManagedProcess]: konec, timeout i zrušení ukončí i potomky (pipeline `sh -c "a | b"`); nejde se `sudo`.
 */
suspend fun runProcess(
    command: List<String>,
    timeout: Duration = 30.seconds,
    stdin: String? = null,
    environment: Map<String, String> = emptyMap(),
    maxOutput: Int = DEFAULT_MAX_OUTPUT,
    /** null = pracovní adresář JVM */
    workingDirectory: File? = null,
    /** PID namespace — potomci skončí s příkazem; setuid (`sudo`) se v user namespace ignoruje */
    isolated: Boolean = false,
): ProcessResult = withContext(Dispatchers.IO) {
    require(maxOutput > 0) { "maxOutput must be > 0" }
    val launched: Launched? = if (isolated) launch(command, environment, workingDirectory) else null
    val process: Process = launched?.process ?: ProcessBuilder(command)
        .redirectErrorStream(true)
        .directory(workingDirectory)
        .apply { environment().putAll(environment) }
        .start()
    // SIGKILL wrapperu → --kill-child zruší celý namespace
    val kill: () -> Unit = { if (launched != null) launched.terminate(Duration.ZERO) else process.killDirectly() }

    try {
        // průběžně do bufferu — při zaseknuté rouře se nezahodí, co už se přečetlo
        val output = OutputTail(maxOutput)
        val reading: Job = pipes.launch { process.inputStream.bufferedReader().use(output::readFrom) }
        // bez vstupu stačí rouru zavřít (neblokuje); zápis blokuje, dokud ho proces nepřečte
        if (stdin == null) writeStdin(process, null) else pipes.launch { writeStdin(process, stdin) }

        val exited: Boolean = withTimeoutOrNull(timeout) { process.onExit().await() } != null
        if (!exited) kill()
        withContext(NonCancellable) {
            reading.joinWithin(OUTPUT_GRACE)
        }
        // waitFor() bez limitu by u procesu v nepřerušitelném I/O visel navždy, i přes timeout
        val exitValue: Int? = if (process.isAlive) null else process.exitValue()
        ProcessResult(command, exitValue, output.text(), timedOut = !exited, outputTruncated = output.truncated)
    } catch (e: Throwable) {
        // zrušení korutiny nebo výjimka — proces (s isolated i potomci) nesmí přežít volajícího
        runCatching { kill() }.exceptionOrNull()?.let(e::addSuppressed)
        throw e
    }
}

/** Parametry [runProcess] v DSL — `runProcess("ffprobe", file.path) { timeout = 10.seconds }`. */
@ProcessDsl
class RunProcessBuilder internal constructor() {
    var timeout: Duration = 30.seconds

    /** null = pracovní adresář JVM */
    var workingDirectory: File? = null

    /** strop výstupu ve znacích; drží se konec, zbytek se dočte a zahodí */
    var maxOutput: Int = DEFAULT_MAX_OUTPUT

    /** v PID namespace — konec, timeout i zrušení ukončí i potomky; ne pro `sudo` */
    var isolated: Boolean = false

    private var stdin: String? = null
    private val environment = LinkedHashMap<String, String>()

    fun stdin(text: String) {
        stdin = text
    }

    fun env(name: String, value: String) {
        environment[name] = value
    }

    fun env(values: Map<String, String>) {
        environment += values
    }

    internal suspend fun run(command: List<String>): ProcessResult =
        runProcess(command, timeout, stdin, environment.toMap(), maxOutput, workingDirectory, isolated)
}

/** [runProcess] s parametry v DSL; podmíněné argumenty přes `runProcess(command("x") { flag(…) }) { … }`. */
suspend fun runProcess(executable: String, vararg args: String, block: RunProcessBuilder.() -> Unit = {}): ProcessResult =
    RunProcessBuilder().apply(block).run(listOf(executable) + args)

/** [runProcess] s parametry v DSL. */
suspend fun runProcess(command: List<String>, block: RunProcessBuilder.() -> Unit): ProcessResult =
    RunProcessBuilder().apply(block).run(command)

/** Jednorázový příkaz navázaný na skupinu — [ProcessGroup.stop] ho zruší a počká na jeho konec. */
suspend fun ProcessGroup.runProcess(executable: String, vararg args: String, block: RunProcessBuilder.() -> Unit = {}): ProcessResult =
    runProcess(listOf(executable) + args, block)

/** Jednorázový příkaz navázaný na skupinu — [ProcessGroup.stop] ho zruší a počká na jeho konec. */
suspend fun ProcessGroup.runProcess(command: List<String>, block: RunProcessBuilder.() -> Unit = {}): ProcessResult {
    val builder = RunProcessBuilder().apply(block)
    return coroutineScope {
        val task: Deferred<ProcessResult> = async(start = CoroutineStart.LAZY) { builder.run(command) }
        check(attach(task)) { "Process group $id is stopped" }
        task.start()
        task.await()
    }
}

private fun Process.killDirectly() {
    if (!isAlive) return
    destroyForcibly()
    waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
}

private fun writeStdin(process: Process, stdin: String?) {
    try {
        process.outputStream.bufferedWriter().use { writer -> stdin?.let(writer::write) }
    } catch (_: IOException) {
        // proces skončil dřív, než stdin přečetl
    }
}

/** Konec výstupu — nejvýš [max] znaků, starší se zahodí. Plní ho vlákno roury, [text] čte volající. */
private class OutputTail(private val max: Int) {
    private val buffer = StringBuilder()

    @Volatile
    var truncated = false
        private set

    fun readFrom(reader: Reader) {
        val chunk = CharArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = reader.read(chunk)
            if (read < 0) return
            synchronized(buffer) {
                buffer.appendRange(chunk, 0, read)
                // ořez až nad dvojnásobkem — amortizovaně, ne po každém bloku
                if (buffer.length - max > max) trim()
            }
        }
    }

    fun text(): String = synchronized(buffer) {
        if (buffer.length > max) trim()
        buffer.toString()
    }

    private fun trim() {
        buffer.deleteRange(0, buffer.length - max)
        // nepřeříznout pár surrogátů (emoji) napůl
        if (buffer.isNotEmpty() && buffer[0].isLowSurrogate()) buffer.deleteAt(0)
        truncated = true
    }
}

/** join bez zrušení jobu — po limitu jen přestaneme čekat */
private suspend fun Job.joinWithin(limit: Duration): Boolean = withTimeoutOrNull(limit) { join() } != null

/**
 * Blokující I/O na rourách mimo strukturu volajícího — withContext by jinak čekal na čtení roury,
 * kterou drží potomek. Chyba roury (proces zabitý uprostřed) se jen zaloguje.
 */
private val pipes = CoroutineScope(
    SupervisorJob() + virtualThreadDispatcher +
        CoroutineExceptionHandler { _, e -> logger.debug("runProcess pipe failed", e) },
)

private val logger = LoggerFactory.getLogger("cz.petrbalat.klib.process.RunProcess")

/** výchozí strop výstupu ve znacích (~2 MB v paměti) */
private const val DEFAULT_MAX_OUTPUT = 1_000_000

/** jak dlouho po skončení procesu čekat na zbytek výstupu */
private val OUTPUT_GRACE = 5.seconds
