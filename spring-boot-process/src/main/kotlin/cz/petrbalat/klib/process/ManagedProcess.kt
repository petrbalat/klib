package cz.petrbalat.klib.process

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.TimeSource

/** Watchdog vyčerpal restarty. Strukturovaná informace — text pro uživatele si sestaví aplikace. */
class GiveUp(val spec: ProcessSpec, val restarts: Int) {
    val name: String get() = spec.name

    override fun toString(): String = "GiveUp(name=$name, restarts=$restarts, output=${spec.output})"
}

/** Stav procesu pro UI a testy (čekání na [Running] místo pollingu). */
sealed interface ProcessState {
    /** před [ManagedProcess.start] */
    data object Idle : ProcessState
    data class Running(val pid: Long, val restarts: Int) : ProcessState
    /** čeká [RestartPolicy.delay] před restartem číslo [restarts]; [exitCode] null = selhalo spuštění */
    data class Restarting(val restarts: Int, val exitCode: Int?) : ProcessState
    /** vyčerpán [RestartPolicy.maxRestarts] */
    data class GaveUp(val restarts: Int) : ProcessState
    /** [ManagedProcess.stop] nebo zrušený scope */
    data object Stopped : ProcessState
}

/**
 * Externí proces (Chromium, mpv, LibreOffice…) s watchdogem: restart při pádu podle [RestartPolicy], pak [ProcessState.GaveUp].
 * Watchdog je coroutine ve [scope] — [stop] i zrušení scope zastaví proces (SIGTERM → SIGKILL celého stromu);
 * odpojené potomky (`setsid`) dorazí [ProcessReaper]. Potomky, které přežily pád rodiče (spadlý wrapper Chromia),
 * zabije watchdog před restartem a [stop] — na Linuxu podle env [INSTANCE_ENV] každého spuštění. V kontejneru je nutný init (`tini`, `docker run --init`),
 * jinak se hromadí zombie osiřelých potomků.
 */
class ManagedProcess(
    val spec: ProcessSpec,
    /** rodič watchdogu — zrušení scope proces zastaví, takže ani selhaný start nenechá proces restartovat navždy */
    private val scope: CoroutineScope,
    /**
     * Každá změna [state], synchronně a **před** zápisem do [state] (kdo čeká na stav procesu, vidí už stav skupiny).
     * Volá se z vlákna watchdogu, nikdy pod zámkem; při [ProcessState.GaveUp] smí volat [stop] (i synchronně přes
     * `runBlocking`) i nový [start].
     */
    private val onStateChange: (ProcessState) -> Unit = {},
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val _state = MutableStateFlow<ProcessState>(ProcessState.Idle)
    val state: StateFlow<ProcessState> = _state.asStateFlow()

    // start/stop vs. přepnutí spuštění — stop() uprostřed startu by minul proces, kterému se teprve zakládá watchdog
    private val lock = Any()

    /** poslední spuštění watchdogu; mění se jen pod [lock] */
    @Volatile
    private var current: Run? = null

    /** proces aktuálního spuštění; null = před startem, po zastavení nebo po selhaném restartu */
    val process: Process? get() = current?.process

    val isAlive: Boolean get() = process?.isAlive == true

    /** watchdog běží (i mezi restarty) a nevzdal se — nový [start] by selhal */
    internal val isRunning: Boolean get() = current?.isRunning == true

    /**
     * Spustí proces s watchdogem. Selhání prvního spuštění vyhodí výjimku. Běžící proces (i mezi restarty)
     * → [IllegalStateException]; po [ProcessState.GaveUp] nebo [stop] jde spustit znovu, i z [onStateChange].
     */
    fun start(): Unit = synchronized(lock) {
        val previous = current
        check(previous?.isRunning != true) { "Process ${spec.name} is already running" }
        previous?.killLeftovers() // nový start po GaveUp — pozůstatky posledního běhu by blokovaly zařízení / profil
        val run = Run()
        current = run
        try {
            run.start()
        } catch (e: Throwable) {
            current = previous
            throw e
        }
    }

    /** Zruší watchdog a počká, až zastaví proces přes [stopGracefully]; po GaveUp dorazí pozůstatky posledního běhu. */
    suspend fun stop() {
        val run = synchronized(lock) { current } ?: return
        run.stop()
    }

    /** SIGKILL pozůstatkům posledního spuštění — pro skupinu, která proces nahrazuje novým stejného názvu. */
    internal fun killLeftovers() {
        current?.killLeftovers()
    }

    override fun toString(): String = "ManagedProcess(${spec.name}, ${state.value})"

    /**
     * Jedno spuštění watchdogu — vlastní proces, id instance, příznak vzdání se a job. Watchdog předchozího
     * spuštění tak po novém [start] (např. z [onStateChange] při GaveUp) nezastaví nový proces ani nepřepíše jeho stav.
     */
    private inner class Run {
        @Volatile
        var process: Process? = null

        /** poslední spuštění — zůstává i po zastavení, podle něj se dorazí potomci, kteří přežili rodiče */
        @Volatile
        private var launched: Launched? = null

        /** nastavený dřív než stav GaveUp — [stop] volaný z [onStateChange] tak nečeká sám na sebe */
        @Volatile
        private var gaveUp = false

        @Volatile
        private var job: Job? = null

        /** watchdog běží a nevzdal se */
        val isRunning: Boolean get() = job?.isActive == true && !gaveUp

        fun start() {
            val first: Process = launchProcess(restarts = 0)
            // UNDISPATCHED: tělo začne i ve zrušeném scope → finally proces vždy zastaví
            job = scope.launch(Dispatchers.IO + CoroutineName("watchdog-${spec.name}"), CoroutineStart.UNDISPATCHED) {
                watch(first)
            }
        }

        suspend fun stop() {
            val job = job ?: return
            // po GaveUp proces neběží a není na co čekat — stop() z onStateChange (vlákno watchdogu) by jinak čekal sám na sebe
            if (gaveUp) {
                job.cancel()
                killLeftovers()
            } else {
                job.cancelAndJoin()
            }
        }

        private suspend fun watch(first: Process) {
            var next: Process? = first
            val budget = RestartBudget(spec.restart)
            try {
                while (true) {
                    // null = selhalo spuštění
                    val exitCode: Int? = next?.let { proc ->
                        val ran = proc.awaitExit()
                        val restarts = budget.restarts
                        if (budget.resetIfStable(ran)) {
                            logger.info("Process {} ran stable for {} — resetting restart counter ({})", spec.name, ran, restarts)
                        }
                        proc.exitValue()
                    }
                    val restarts: Int = budget.next() ?: return giveUp(budget.restarts)
                    // potomci spadlého procesu (už mimo jeho strom) — nová instance by narazila na zámek profilu / zařízení
                    if (exitCode != null) killLeftovers()
                    setState(ProcessState.Restarting(restarts, exitCode))
                    logger.info("Restarting process {} (#{}) in {}", spec.name, restarts, budget.delay)
                    delay(budget.delay)
                    next = relaunch(restarts)
                }
            } finally {
                // po GaveUp proces neběží a pozůstatky zůstávají (odpojený launcher s RestartPolicy.NEVER) — uklidí je až stop()
                if (!gaveUp) withContext(NonCancellable) { launched?.terminate(spec.gracefulStop) }
                process = null
                if (!gaveUp) setState(ProcessState.Stopped)
            }
        }

        /** Počká na konec procesu; vrací délku běhu — monotónní čas, NTP skok po bootu (zařízení bez RTC) ji nezkreslí. */
        private suspend fun Process.awaitExit(): Duration {
            val startedAt = TimeSource.Monotonic.markNow()
            onExit().await()
            val ran = startedAt.elapsedNow()
            logger.warn("Process {} ({}) exited with code {} after {}", spec.name, pid(), exitValue(), ran)
            return ran
        }

        private fun giveUp(restarts: Int) {
            logger.error("Process {} reached the limit of {} restarts — giving up", spec.name, spec.restart.maxRestarts)
            gaveUp = true
            setState(ProcessState.GaveUp(restarts))
        }

        /** selhání spuštění (chybějící binárka) je další pokus, ne konec watchdogu */
        private fun relaunch(restarts: Int): Process? = try {
            launchProcess(restarts)
        } catch (e: Exception) {
            logger.error("Restart of process ${spec.name} failed", e)
            null
        }

        private fun launchProcess(restarts: Int): Process {
            logger.info("Starting process {}: {}", spec.name, spec.command.joinToString(" "))

            // OS redirect (ne pumpa v JVM) — výstup nejde přes rouru, takže ji nemá kdo zaplnit
            val redirect: ProcessBuilder.Redirect = when (val output = spec.output) {
                ProcessOutput.Discard -> ProcessBuilder.Redirect.DISCARD
                is ProcessOutput.ToFile -> {
                    output.file.parentFile?.mkdirs()
                    rotateLog(output.file, output.maxBytes)
                    ProcessBuilder.Redirect.appendTo(output.file)
                }
            }

            val launched = launch(spec.command, spec.environment, spec.workingDirectory, spec.name) { redirectOutput(redirect) }
            this.launched = launched
            process = launched.process
            setState(ProcessState.Running(launched.process.pid(), restarts))
            logger.info("Process {} started, PID {}", spec.name, launched.process.pid())
            return launched.process
        }

        /** SIGKILL procesům tohoto spuštění, které přežily rodiče ([Launched.killLeftovers]); bez `/proc` no-op. */
        fun killLeftovers() {
            launched?.killLeftovers()
        }

        /** Stav hlásí jen aktuální spuštění — stav předchozího watchdogu po novém [start] se zahodí. */
        private fun setState(state: ProcessState) {
            if (current !== this) return
            runCatching { onStateChange(state) }.onFailure { logger.error("State listener of process ${spec.name} failed", it) }
            synchronized(lock) { if (current === this) _state.value = state }
        }
    }

    companion object {
        /** env s id spuštění — dědí ho potomci, i ti, kteří přežijí rodiče */
        const val INSTANCE_ENV = "KLIB_PROCESS_INSTANCE"
    }
}
