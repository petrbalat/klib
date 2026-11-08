package cz.petrbalat.klib.process

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Výchozí nastavení procesů skupin (z `klib.process.*`). */
data class ProcessDefaults(
    val restart: RestartPolicy = RestartPolicy(),
    val gracefulStop: Duration = 5.seconds,
    /** základ pro [ProcessGroup.logFile]; null = aplikace si cesty k logům určuje sama */
    val logsDirectory: File? = null,
)

/** Háčky, které skupině předává [ProcessGroups]. */
internal interface ProcessGroupListener {
    fun restarted(group: ProcessGroup, name: String, restarting: ProcessState.Restarting) {}
    fun gaveUp(group: ProcessGroup, giveUp: GiveUp) {}
    fun stopped(group: ProcessGroup) {}
}

/** Kontext práce navázané na skupinu (cyklus) — [ProcessGroup.stop] zevnitř ní (whilePlaying) nečeká sám na sebe. */
internal class GroupWork(val group: ProcessGroup) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<GroupWork>
}

/** Háčky jednoho procesu skupiny (DSL `onRestarting` / `onGaveUp`) — volají se po háčcích registru. */
internal class ProcessHooks(
    val restarting: (ProcessState.Restarting) -> Unit = {},
    val gaveUp: (GiveUp) -> Unit = {},
)

/**
 * Procesy jedné jednotky práce (instance akce, ladicí běh…); jejich watchdogy běží ve [scope]. [stop] zastaví
 * spuštěné procesy a navázanou práci. Každý spravovaný proces má vlastní PID namespace.
 * Vytváří jen [ProcessGroups] — restart a vzdání se watchdogu předá jeho [ProcessGroupsListener].
 */
@ProcessDsl
class ProcessGroup internal constructor(
    val id: String,
    private val scope: CoroutineScope,
    internal val defaults: ProcessDefaults,
    private val listener: ProcessGroupListener,
) {

    /** podle [ManagedProcess.name] — proces po GaveUp / stop nahradí nový stejného názvu */
    private val processes = ConcurrentHashMap<String, ManagedProcess>()

    /** práce navázaná na skupinu (cykly segmentů, jednorázové příkazy) — stop() ji zruší a počká na její konec */
    private val attached: MutableSet<Job> = ConcurrentHashMap.newKeySet()

    // start (včetně forku) vs. přepnutí isStopped — stop() uprostřed startu by našel proces bez watchdogu a nezastavil ho
    private val lock = Any()

    private val _state = MutableStateFlow<Map<String, ProcessState>>(emptyMap())

    /** stav procesů skupiny podle [ManagedProcess.name]; Stopped procesy zůstávají do [stop] skupiny */
    val state: StateFlow<Map<String, ProcessState>> = _state.asStateFlow()

    @Volatile
    var isStopped: Boolean = false
        private set

    /** dokončení [stop] — souběžný volající na něj počká */
    private val stopCompleted = CompletableDeferred<Unit>()

    @Volatile
    private var scopeCompletion: DisposableHandle? = null

    /**
     * Konec [scope] bez [stop] (zrušený job akce) skupinu zastaví a odebere ji z registru.
     * Volá jen `ProcessGroups` po zápisu do registru: dřív by duplicitní id zabilo procesy živé skupiny.
     */
    internal fun stopOnScopeCompletion() {
        val job = scope.coroutineContext[Job] ?: return
        // handler musí být rychlý a neblokující → stop mimo zrušený scope
        scopeCompletion = job.invokeOnCompletion { CoroutineScope(Dispatchers.IO).launch { stop() } }
    }

    /** `<logsDirectory>/<name>.log`; vyžaduje `klib.process.logs-directory` */
    fun logFile(name: String): File =
        File(checkNotNull(defaults.logsDirectory) { "klib.process.logs-directory is not set" }, "$name.log")

    /**
     * Spustí [ManagedProcess] s watchdogem. [spec] je úplný — výchozí hodnoty
     * `klib.process.*` ([ProcessDefaults]) dosazuje až DSL `start(name) { }`. Název je ve skupině klíčem: běžící
     * proces stejného názvu → [IllegalStateException] (stav skupiny by se přepisoval); proces po GaveUp nebo zastavení
     * nový stejného názvu nahradí.
     */
    fun start(spec: ProcessSpec): ManagedProcess = start(spec, ProcessHooks())

    internal fun start(spec: ProcessSpec, hooks: ProcessHooks): ManagedProcess = synchronized(lock) {
        check(!isStopped) { "Process group $id is stopped" }
        val name = spec.name
        val previous: ManagedProcess? = processes[name]
        check(previous?.isRunning != true) { "Process $name is already running in group $id — use a different name" }
        lateinit var process: ManagedProcess
        process = ManagedProcess(spec, scope) { s ->
            // nahrazený proces (doběhnutý finally po stop) stav nového nepřepíše
            if (processes[name] === process) inCallback(s) {
                _state.update { it + (name to s) }
                // po stop() skupiny se nehlásí — instance už neběží
                if (!isStopped) when (s) {
                    is ProcessState.Restarting -> {
                        listener.restarted(this, name, s)
                        hooks.restarting(s)
                    }
                    is ProcessState.GaveUp -> {
                        val giveUp = GiveUp(process.spec, s.restarts)
                        listener.gaveUp(this, giveUp)
                        hooks.gaveUp(giveUp)
                    }
                    else -> {}
                }
            }
        }
        processes[name] = process
        try {
            process.start()
        } catch (e: Throwable) {
            // selhané spuštění (chybějící binárka) nezůstane ve skupině do stop()
            if (previous != null) processes[name] = previous else processes.remove(name)
            throw e
        }
        process
    }

    /**
     * Naváže [job] (cyklus segmentů nebo jednorázový příkaz) na skupinu: [stop] ho zruší a počká na něj. Zastavená
     * skupina job rovnou zruší a vrací false.
     */
    internal fun attach(job: Job): Boolean {
        synchronized(lock) {
            if (isStopped) {
                job.cancel()
                return false
            }
            attached += job
        }
        job.invokeOnCompletion { attached -= job }
        return true
    }

    /**
     * Stop navázaných cyklů a všech spuštěných procesů. Doběhne i ve
     * zrušené korutině (úklid ve `finally` jobu akce). Opakované volání se vrátí, až první stop doběhne (i souběžné
     * se zastavením po konci scope) — kromě volání zevnitř skupiny, viz [awaitStopped].
     */
    suspend fun stop() {
        val fromRestartingCallback: Boolean =
            callbackGroup.get() === this && callbackState.get() is ProcessState.Restarting
        // počká na rozběhnutý start() — po zámku už každý spuštěný proces má watchdog a další start selže
        val first: Boolean = synchronized(lock) {
            if (isStopped) {
                false
            } else {
                isStopped = true
                true
            }
        }
        if (!first) return awaitStopped()
        if (fromRestartingCallback) {
            // zastavení joinuje watchdog, jehož callback právě běží na této niti
            CoroutineScope(Dispatchers.IO).launch { stopFirst() }
            return
        }
        stopFirst()
    }

    private suspend fun stopFirst() {
        try {
            // NonCancellable — ve zrušené korutině by withContext hned vyhodil a procesy by běžely dál
            withContext(NonCancellable + Dispatchers.IO) {
                // nejdřív cykly — jinak by po zastavení procesů spustily další segment;
                // stop() zevnitř cyklu (whilePlaying) na něj nečeká — čekal by sám na sebe
                attached.forEach { it.cancel() }
                if (currentCoroutineContext()[GroupWork]?.group !== this@ProcessGroup) attached.toList().joinAll()
                // souběžně — jinak N × gracefulStop přeleze shutdownTimeout
                processes.values.map { launch { it.stop() } }.joinAll()
            }
        } finally {
            try {
                // dlouho žijící scope (aplikace) by jinak držel každou zastavenou skupinu
                scopeCompletion?.dispose()
                listener.stopped(this)
            } finally {
                stopCompleted.complete(Unit)
            }
        }
    }

    /**
     * Souběžný stop počká na první — jinak by se volající vrátil s běžícími procesy. Nečeká zevnitř
     * skupiny, čekal by sám na sebe: z callbacku watchdogu (první stop joinuje watchdog, např. při Restarting)
     * ani z navázaného cyklu (první stop joinuje cyklus, např. z whilePlaying).
     */
    private suspend fun awaitStopped() {
        if (callbackGroup.get() === this || currentCoroutineContext()[GroupWork]?.group === this) return
        withContext(NonCancellable) { stopCompleted.await() }
    }

    /** callbacky procesu (listener registru, háčky DSL) — stop() z nich (i přes runBlocking) pozná, že je zevnitř */
    private inline fun inCallback(state: ProcessState, block: () -> Unit) {
        val previousGroup = callbackGroup.get()
        val previousState = callbackState.get()
        callbackGroup.set(this)
        callbackState.set(state)
        try {
            block()
        } finally {
            callbackState.set(previousState)
            callbackGroup.set(previousGroup)
        }
    }

    override fun toString(): String = "ProcessGroup($id)"

    private companion object {
        /** skupina, jejíž callback právě běží na tomto vlákně (vlákno watchdogu) */
        val callbackGroup = ThreadLocal<ProcessGroup?>()
        /** stav, jehož callback právě běží; jen Restarting potřebuje odložit první stop kvůli joinu watchdogu */
        val callbackState = ThreadLocal<ProcessState?>()
    }
}
