package cz.petrbalat.klib.process

import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Jediné místo, kde vzniká spravovaný proces: příkaz v odděleném PID namespace, pracovní adresář a env volajícího.
 * stdout+stderr jsou spojené; [configure] doplní přesměrování výstupu.
 */
internal fun launch(
    command: List<String>,
    environment: Map<String, String>,
    workingDirectory: File?,
    /** do logů */
    name: String = defaultName(command),
    configure: ProcessBuilder.() -> Unit = {},
): Launched {
    val process = ProcessBuilder(namespaceCommand(command))
        .redirectErrorStream(true)
        .directory(workingDirectory)
        .apply {
            environment().putAll(environment)
            configure()
        }
        .start()
    return Launched(process, name).also { it.awaitStarted() }
}

private fun namespaceCommand(command: List<String>): List<String> {
    check(System.getProperty("os.name").startsWith("Linux", ignoreCase = true)) {
        "Managed processes are supported only on Linux"
    }
    check(File("/usr/bin/tini").canExecute()) { "Managed processes require executable /usr/bin/tini" }
    // --mount-proc: vlastní /proc, jinak /proc/<getpid()> uvnitř ukazuje na cizí proces kontejneru.
    // Mount namespace je private — mounty hostitele vzniklé až po startu (CIFS) běžící proces neuvidí.
    return listOf(
        "unshare", "--user", "--map-current-user", "--pid", "--fork", "--kill-child", "--mount-proc",
        "/usr/bin/tini", "-g", "--",
    ) + command
}

/**
 * Ověří, že runtime dovolí spravované procesy (Linux, tini, user/PID namespace) — jinak každý start skončí
 * kódem 1 a chyba je jen v logu procesu. Při selhání [IllegalStateException] s výstupem `unshare`.
 */
fun checkPidNamespaceSupport(timeout: Duration = 5.seconds) {
    val process = ProcessBuilder(namespaceCommand(listOf("true"))).redirectErrorStream(true).start()
    process.outputStream.close()
    val exited = process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    if (!exited) process.destroyForcibly()
    val output = process.inputStream.bufferedReader().readText().trim()
    check(exited && process.exitValue() == 0) {
        val reason = if (exited) "exit code ${process.exitValue()}" else "timeout $timeout"
        "PID namespace for managed processes is not available ($reason): $output"
    }
}

/**
 * Jedno spravované spuštění. Potomky uklidí jádro při zániku PID namespace.
 */
internal class Launched(
    val process: Process,
    private val name: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Stop nesmí předběhnout instalaci signálových handlerů v tini ani fork příkazu. */
    fun awaitStarted(timeout: Duration = 5.seconds) {
        val startedAt = TimeSource.Monotonic.markNow()
        while (process.isAlive) {
            val init = namespaceInit()
            if (init?.children()?.toList()?.any { it.isAlive } == true) return

            val remaining = timeout - startedAt.elapsedNow()
            if (!remaining.isPositive()) {
                killWrapper()
                throw IllegalStateException("Timed out waiting for managed command ${name} to start")
            }
            Thread.sleep(minOf(10L, remaining.inWholeMilliseconds.coerceAtLeast(1)))
        }
    }

    /** SIGTERM vnitřnímu tini → [gracefulStop] → SIGKILL wrapperu; [Duration.ZERO] = rovnou SIGKILL */
    fun terminate(gracefulStop: Duration) {
        stopPidNamespace(gracefulStop)
    }

    private fun stopPidNamespace(gracefulStop: Duration) {
        if (!process.isAlive) return
        if (!gracefulStop.isPositive()) {
            killWrapper()
            return
        }

        val startedAt = TimeSource.Monotonic.markNow()
        val init = awaitNamespaceInit(gracefulStop)
        if (init == null) {
            logger.warn("Could not find namespace init for {} (PID {}) — killing unshare wrapper", name, process.pid())
            killWrapper()
            return
        }

        logger.info("Stopping process {} through namespace init PID {}", name, init.pid())
        init.destroy()
        val remaining = gracefulStop - startedAt.elapsedNow()
        val stopped = remaining.isPositive() && process.waitFor(
            remaining.inWholeMilliseconds.coerceAtLeast(1),
            TimeUnit.MILLISECONDS,
        )
        if (!stopped) {
            logger.warn("Namespace process {} ignored SIGTERM for {} — killing unshare wrapper", name, gracefulStop)
            killWrapper()
        }
    }

    private fun awaitNamespaceInit(timeout: Duration): ProcessHandle? {
        val startedAt = TimeSource.Monotonic.markNow()
        while (process.isAlive) {
            val init = namespaceInit()
            if (init != null) return init

            val remaining = timeout - startedAt.elapsedNow()
            if (!remaining.isPositive()) return null
            Thread.sleep(minOf(10L, remaining.inWholeMilliseconds.coerceAtLeast(1)))
        }
        return null
    }

    private fun namespaceInit(): ProcessHandle? = process.toHandle().children().toList().firstOrNull {
        it.isAlive && it.info().command().orElse("").substringAfterLast('/') == "tini"
    }

    private fun killWrapper() {
        if (!process.isAlive) return
        process.destroyForcibly()
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            logger.error("unshare wrapper for {} (PID {}) did not exit after SIGKILL", name, process.pid())
        }
    }
}
