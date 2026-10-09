package cz.petrbalat.klib.process

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Proces skupiny — k popisu přidává log v `klib.process.logs-directory` a háčky restartu / vzdání se. */
@ProcessDsl
class GroupProcessBuilder internal constructor(private val group: ProcessGroup) : ProcessSpecBuilder(group.defaults) {
    private var logToFile = false
    private var logFileName: String? = null
    private var logMaxBytes = ProcessOutput.ToFile.DEFAULT_MAX_BYTES
    private val restartingHooks = mutableListOf<(ProcessState.Restarting) -> Unit>()
    private val gaveUpHooks = mutableListOf<(GiveUp) -> Unit>()

    /** `<logs-directory>/<fileName ?: name>.log` ([ProcessGroup.logFile]); název se vyhodnotí až při spuštění */
    fun logToFile(fileName: String? = null, maxBytes: Long = ProcessOutput.ToFile.DEFAULT_MAX_BYTES) {
        logToFile = true
        logFileName = fileName
        logMaxBytes = maxBytes
    }

    /** synchronně z vlákna watchdogu před restartem; nesmí dlouho blokovat */
    fun onRestarting(block: (ProcessState.Restarting) -> Unit) {
        restartingHooks += block
    }

    /** synchronně z vlákna watchdogu; smí skupinu zastavit (i přes `runBlocking`) */
    fun onGaveUp(block: (GiveUp) -> Unit) {
        gaveUpHooks += block
    }

    override fun build(): ProcessSpec {
        val spec = super.build()
        if (!logToFile) return spec
        return spec.copy(output = ProcessOutput.ToFile(group.logFile(logFileName ?: spec.name), logMaxBytes))
    }

    internal fun hooks() = ProcessHooks(
        restarting = { state -> restartingHooks.forEach { it(state) } },
        gaveUp = { giveUp -> gaveUpHooks.forEach { it(giveUp) } },
    )
}

/**
 * Spustí proces skupiny popsaný v [block]; výchozí hodnoty z `klib.process.*` ([ProcessDefaults]), tag skupiny
 * se přidá do env. [name] je klíč ve skupině, null = název binárky.
 */
fun ProcessGroup.start(name: String? = null, block: GroupProcessBuilder.() -> Unit): ManagedProcess {
    val builder = GroupProcessBuilder(this).apply { this.name = name }.apply(block)
    return start(builder.build(), builder.hooks())
}

/**
 * Skupina na dobu [block]: vytvoří ji, spustí blok a ve `finally` zastaví — i při zrušení a výjimce. Watchdogy jsou
 * potomci volající korutiny (strukturovaná souběžnost). Zastavuje-li se ještě skupina se stejným id (předchozí běh
 * akce), počká na ni místo chyby; živá skupina se stejným id → [IllegalArgumentException].
 */
suspend fun <T> ProcessGroups.withGroup(id: String, block: suspend ProcessGroup.() -> T): T {
    groups.first { it[id]?.isStopped != true }
    return coroutineScope {
        val group = create(id, this)
        try {
            group.block()
        } finally {
            group.stop() // NonCancellable uvnitř; watchdogy skončí → coroutineScope se dokončí
        }
    }
}

/** Registr skupin bez Springu; ve Spring Bootu ho dává autokonfigurace z `klib.process.*`. */
@ProcessDsl
class ProcessGroupsBuilder internal constructor(private val owner: String) {
    var envName: String = "KLIB_PROCESS_TAG"

    /** základ pro [ProcessGroup.logFile] a `logToFile()` */
    var logsDirectory: File? = null

    var restart: RestartPolicy = RestartPolicy()
    var gracefulStop: Duration = 5.seconds

    /** strop pro [ProcessGroups.close] */
    var shutdownTimeout: Duration = 8.seconds

    private val restartingHooks = mutableListOf<(ProcessGroup, String, ProcessState.Restarting) -> Unit>()
    private val gaveUpHooks = mutableListOf<(ProcessGroup, GiveUp) -> Unit>()

    fun restart(block: RestartPolicyBuilder.() -> Unit) {
        restart = RestartPolicyBuilder(restart).apply(block).build()
    }

    /** viz [ProcessGroupsListener.restarting] */
    fun onRestarting(block: (group: ProcessGroup, name: String, restarting: ProcessState.Restarting) -> Unit) {
        restartingHooks += block
    }

    /** viz [ProcessGroupsListener.gaveUp] */
    fun onGaveUp(block: (group: ProcessGroup, giveUp: GiveUp) -> Unit) {
        gaveUpHooks += block
    }

    internal fun build(): ProcessGroups {
        val tagging = ProcessTagging(owner, envName)
        return ProcessGroups(
            tagging = tagging,
            reaper = ProcessReaper(tagging),
            defaults = ProcessDefaults(restart, gracefulStop, logsDirectory),
            listener = object : ProcessGroupsListener {
                override fun restarting(group: ProcessGroup, name: String, restarting: ProcessState.Restarting) =
                    restartingHooks.forEach { it(group, name, restarting) }

                override fun gaveUp(group: ProcessGroup, giveUp: GiveUp) = gaveUpHooks.forEach { it(group, giveUp) }
            },
            shutdownTimeout = shutdownTimeout,
        )
    }
}

/**
 * Registr skupin vlastníka [owner] bez Springu. Sirotky z minulého běhu JVM nezabíjí — zavolej
 * [ProcessGroups.reapOrphans]; skupiny, které aplikace nezastavila, zastaví [ProcessGroups.close].
 */
fun processGroups(owner: String, block: ProcessGroupsBuilder.() -> Unit = {}): ProcessGroups =
    ProcessGroupsBuilder(owner).apply(block).build()
