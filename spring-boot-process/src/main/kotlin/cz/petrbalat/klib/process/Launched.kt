package cz.petrbalat.klib.process

import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID
import kotlin.time.Duration

/**
 * Jediné místo, kde vzniká proces: příkaz, pracovní adresář, env volajícího a nové id spuštění
 * [ManagedProcess.INSTANCE_ENV] — to vyhrává, podle něj se hledají potomci, kteří přežili rodiče. stdout+stderr spojené;
 * [configure] doplní přesměrování výstupu.
 */
internal fun launch(
    command: List<String>,
    environment: Map<String, String>,
    workingDirectory: File?,
    /** do logů */
    name: String = defaultName(command),
    configure: ProcessBuilder.() -> Unit = {},
): Launched {
    val instance = UUID.randomUUID().toString()
    val process = ProcessBuilder(command)
        .redirectErrorStream(true)
        .directory(workingDirectory)
        .apply {
            environment().putAll(environment)
            environment()[ManagedProcess.INSTANCE_ENV] = instance
            configure()
        }
        .start()
    return Launched(process, instance, name)
}

/**
 * Jedno spuštění — proces a jeho id v env [ManagedProcess.INSTANCE_ENV], které dědí i potomci mimo strom (přežili
 * rodiče, odpojili se). Jediné místo, kde se spuštění zabíjí: [ManagedProcess] i [runProcess].
 */
internal class Launched(val process: Process, private val instance: String, private val name: String) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** SIGTERM → [gracefulStop] → SIGKILL stromu ([stopGracefully]), pak [killLeftovers]; [Duration.ZERO] = rovnou SIGKILL */
    fun terminate(gracefulStop: Duration) {
        process.stopGracefully(gracefulStop)
        killLeftovers()
    }

    /** SIGKILL procesům spuštění mimo strom (podle env); bez `/proc` no-op */
    fun killLeftovers() {
        val pids: List<Long> = killByEnv(ManagedProcess.INSTANCE_ENV) { it == instance }
        if (pids.isNotEmpty()) logger.warn("Killed leftover processes of {} (PID {}): {}", name, process.pid(), pids)
    }
}
