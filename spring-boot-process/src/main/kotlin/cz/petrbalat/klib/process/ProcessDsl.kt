package cz.petrbalat.klib.process

import java.io.File
import kotlin.time.Duration

/** Buildery DSL — ve vnořeném bloku (`restart { }` uvnitř `start { }`) nejdou implicitně volat metody vnějšího. */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class ProcessDsl

/** Argumenty příkazu — podmíněné přepínače a volby bez ručního skládání seznamu. */
@ProcessDsl
class CommandBuilder internal constructor(executable: String, args: Array<out String>) {
    private val command: MutableList<String> = mutableListOf(executable).apply { addAll(args) }

    fun arg(vararg values: Any) {
        values.mapTo(command) { it.toString() }
    }

    fun args(values: Iterable<Any>) {
        values.mapTo(command) { it.toString() }
    }

    /** přidá [name], jen když [enabled] */
    fun flag(name: String, enabled: Boolean = true) {
        if (enabled) command += name
    }

    /** null = vynechat; [name] končící `=` → jeden argument `--app=url`, jinak dva `--volume 50` */
    fun option(name: String, value: Any?) {
        if (value == null) return
        if (name.endsWith("=")) {
            command += name + value
        } else {
            command += name
            command += value.toString()
        }
    }

    internal fun build(): List<String> = command.toList()
}

/** Příkaz (seznam argumentů, bez shellu) z [executable], pevných [args] a podmíněných částí v [block]. */
fun command(executable: String, vararg args: String, block: CommandBuilder.() -> Unit = {}): List<String> =
    CommandBuilder(executable, args).apply(block).build()

/**
 * `sudo -n <command>` — bez hesla selže hned, místo čekání na prompt až do timeoutu. sudo resetuje env: proces pod ním
 * nenese tag skupiny ani [ManagedProcess.INSTANCE_ENV] (a jako root by ho reaper stejně nepřečetl) — ukončí ho jen
 * stop přes strom procesu.
 */
fun sudo(command: List<String>): List<String> = listOf("sudo", "-n") + command

/**
 * `sudo -n` + příkaz jako [command] — i uvnitř builderu procesu, kde `command(…)` je jeho metoda a `sudo(command(…))`
 * nejde napsat: `start { command(sudo("/usr/bin/systemctl", "restart", unit)) }`.
 */
fun sudo(executable: String, vararg args: String, block: CommandBuilder.() -> Unit = {}): List<String> =
    sudo(command(executable, *args, block = block))

/** Společné části popisu procesu — [ProcessSpecBuilder] (s restartem) i segmentu cyklu (bez něj). */
@ProcessDsl
abstract class CommonProcessBuilder internal constructor(gracefulStop: Duration) {
    /** null = název binárky */
    var name: String? = null

    /** null = pracovní adresář JVM */
    var workingDirectory: File? = null

    var output: ProcessOutput = ProcessOutput.Discard

    /** SIGTERM → čekání → SIGKILL */
    var gracefulStop: Duration = gracefulStop

    private var command: List<String>? = null
    private val environment = LinkedHashMap<String, String>()

    fun command(executable: String, vararg args: String, block: CommandBuilder.() -> Unit = {}) {
        command = CommandBuilder(executable, args).apply(block).build()
    }

    fun command(command: List<String>) {
        this.command = command
    }

    fun env(name: String, value: String) {
        environment[name] = value
    }

    fun env(values: Map<String, String>) {
        environment += values
    }

    /** stdout+stderr do [file] s rotací při (re)startu */
    fun logTo(file: File, maxBytes: Long = ProcessOutput.ToFile.DEFAULT_MAX_BYTES) {
        output = ProcessOutput.ToFile(file, maxBytes)
    }

    internal fun spec(restart: RestartPolicy): ProcessSpec {
        val command = checkNotNull(command) { "command is not set" }
        return ProcessSpec(
            command = command,
            name = name ?: defaultName(command),
            environment = environment.toMap(),
            workingDirectory = workingDirectory,
            output = output,
            restart = restart,
            gracefulStop = gracefulStop,
        )
    }
}

/** Popis procesu s watchdogem; výchozí hodnoty z [ProcessDefaults] (ve skupině z `klib.process.*`). */
@ProcessDsl
open class ProcessSpecBuilder internal constructor(defaults: ProcessDefaults) : CommonProcessBuilder(defaults.gracefulStop) {
    var restart: RestartPolicy = defaults.restart

    /** úprava kopie aktuální politiky */
    fun restart(block: RestartPolicyBuilder.() -> Unit) {
        restart = RestartPolicyBuilder(restart).apply(block).build()
    }

    fun noRestart() {
        restart = RestartPolicy.NEVER
    }

    internal open fun build(): ProcessSpec = spec(restart)
}

@ProcessDsl
class RestartPolicyBuilder internal constructor(base: RestartPolicy) {
    var maxRestarts: Int = base.maxRestarts
    var delay: Duration = base.delay
    var stableRun: Duration = base.stableRun

    internal fun build() = RestartPolicy(maxRestarts, delay, stableRun)
}

/** Znovupoužitelný popis procesu (mimo skupinu) — `ManagedProcess(processSpec { … }, scope)`. */
fun processSpec(block: ProcessSpecBuilder.() -> Unit): ProcessSpec = ProcessSpecBuilder(ProcessDefaults()).apply(block).build()
