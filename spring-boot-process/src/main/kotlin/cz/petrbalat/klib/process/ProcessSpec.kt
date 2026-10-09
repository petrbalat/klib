package cz.petrbalat.klib.process

import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Neměnný popis externího procesu — sdílí ho [ManagedProcess], [ProcessGroup.start] i segmenty cyklu.
 * Spuštění přidá do env id instance ([ManagedProcess.INSTANCE_ENV]) a skupina svůj tag; obojí má přednost
 * před [environment].
 */
data class ProcessSpec(
    /** seznam argumentů, bez shellu */
    val command: List<String>,
    /** do logů, názvu korutiny watchdogu, [GiveUp] a klíče ve stavu skupiny; výchozí = název binárky */
    val name: String = defaultName(command),
    val environment: Map<String, String> = emptyMap(),
    /** null = pracovní adresář JVM */
    val workingDirectory: File? = null,
    val output: ProcessOutput = ProcessOutput.Discard,
    val restart: RestartPolicy = RestartPolicy(),
    /** SIGTERM → čekání → SIGKILL; [Duration.ZERO] = rovnou SIGKILL */
    val gracefulStop: Duration = 5.seconds,
) {
    init {
        require(command.isNotEmpty()) { "command must not be empty" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(!gracefulStop.isNegative()) { "gracefulStop must not be negative" }
    }

    // env může nést tajemství (tokeny) — do logů jen názvy proměnných
    override fun toString(): String =
        "ProcessSpec(name=$name, command=$command, environment=${environment.keys}, workingDirectory=$workingDirectory, " +
            "output=$output, restart=$restart, gracefulStop=$gracefulStop)"
}

/** název binárky — výchozí [ProcessSpec.name] */
internal fun defaultName(command: List<String>): String = command.firstOrNull()?.let { File(it).name }.orEmpty()

data class RestartPolicy(
    val maxRestarts: Int = 50,
    val delay: Duration = 2.seconds,
    /**
     * Pád po aspoň takto dlouhém běhu vynuluje počítadlo restartů. Bez toho by proces bez konce
     * s občasným pádem (Chromium GPU 1×/den) limit vyčerpal za pár týdnů.
     */
    val stableRun: Duration = 5.minutes,
) {
    init {
        require(maxRestarts >= 0) { "maxRestarts must be >= 0" }
    }

    companion object {
        val NEVER = RestartPolicy(maxRestarts = 0)
    }
}
