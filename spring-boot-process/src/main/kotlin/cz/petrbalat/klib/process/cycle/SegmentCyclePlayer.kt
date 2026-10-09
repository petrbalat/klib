package cz.petrbalat.klib.process.cycle

import cz.petrbalat.klib.process.CommonProcessBuilder
import cz.petrbalat.klib.process.GroupWork
import cz.petrbalat.klib.process.ManagedProcess
import cz.petrbalat.klib.process.ProcessDsl
import cz.petrbalat.klib.process.ProcessGroup
import cz.petrbalat.klib.process.ProcessSpec
import cz.petrbalat.klib.process.ProcessState
import cz.petrbalat.klib.process.RestartPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Jeden segment cyklu: externí proces + jeho délka + volitelná souběžná akce (stránkování, autentizace) běžící po dobu
 * segmentu. Neměnný popis — provider může každý cyklus vracet tytéž instance; co hraje, je v [SegmentCyclePlayer.nowPlaying].
 */
class Segment(
    val duration: Duration,
    /** [ProcessSpec.restart] se nepoužije — životní cyklus řídí cyklus, ne watchdog ([RestartPolicy.NEVER]) */
    val spec: ProcessSpec,
    /** příprava před startem (touch souborů, úklid lock souborů); smí blokovat — zrušení cyklu na ni nečeká */
    val prepare: () -> Unit = {},
    /** běží paralelně po dobu segmentu, na konci se zruší */
    val whilePlaying: (suspend () -> Unit)? = null,
)

/** Právě přehrávaný segment a jeho proces. */
class NowPlaying(val segment: Segment, val process: ManagedProcess, val startedAt: TimeMark) {
    override fun toString(): String = "NowPlaying(${process.spec.name}, ${process.state.value})"
}

/** Segment v DSL — popis procesu (bez restartu) + [prepare] a [whilePlaying]. */
@ProcessDsl
class SegmentBuilder internal constructor() : CommonProcessBuilder(gracefulStop = 5.seconds) {
    private var prepare: () -> Unit = {}
    private var whilePlaying: (suspend () -> Unit)? = null

    /** příprava před startem; smí blokovat — zrušení cyklu na ni nečeká */
    fun prepare(block: () -> Unit) {
        prepare = block
    }

    /** běží paralelně po dobu segmentu, na konci se zruší */
    fun whilePlaying(block: suspend () -> Unit) {
        whilePlaying = block
    }

    internal fun build(duration: Duration) = Segment(duration, spec(RestartPolicy.NEVER), prepare, whilePlaying)
}

/** `segment(30.seconds) { command("mpv", "--fs", file.path); prepare { … }; whilePlaying { … } }` */
fun segment(duration: Duration, block: SegmentBuilder.() -> Unit): Segment = SegmentBuilder().apply(block).build(duration)

/**
 * Cyklické přehrávání segmentů („proces na N sekund, pak další“).
 *
 *  - Jedna coroutine sériově prochází segmenty → vždy běží právě jeden proces.
 *  - Provider segmentů se volá na začátku každého cyklu — fixní seznam i nové načtení zdroje (adresář).
 *  - Provider i `prepare` běží mimo cyklus — blokující I/O (spadlý síťový mount) nezdrží [stop].
 *  - Spadlý proces segment ukončí dřív (nejdřív po `minSegment`) — další segment přijde hned, ne po celé délce.
 *  - Ukončení = [stop] → `finally` v cyklu zastaví běžící proces.
 *  - Cyklus běží ve [scope] — jeho zrušení zruší i cyklus.
 *  - S [group] nesou segmenty tag skupiny a [ProcessGroup.stop] cyklus zruší a počká na něj — po zastavení
 *    skupiny už další segment nezačne.
 */
class SegmentCyclePlayer(
    private val scope: CoroutineScope,
    /**
     * Minimální délka segmentu — pojistka proti nulovým délkám (chybějící soubory),
     * bez ní by cyklus v těsné smyčce spouštěl a zabíjel procesy.
     */
    private val minSegment: Duration = 5.seconds,
    /** skupina, jejíž tag segmenty nesou a jejíž stop cyklus ukončí; null = samostatný cyklus */
    private val group: ProcessGroup? = null,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var cycleJob: Job? = null

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)

    /** právě přehrávaný segment; null = mezi segmenty, před startem a po zastavení */
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    /**
     * Spustí cyklus. Prázdný výsledek [segments] → čekání [emptyRetry] a nový pokus.
     * Běžící cyklus → [IllegalStateException] — dva by hrály souběžně; nejdřív [stop]. Zastavená [group] → cyklus
     * nezačne.
     */
    @Synchronized
    fun start(
        jobName: String,
        emptyRetry: Duration = 30.seconds,
        segments: suspend () -> List<Segment>,
    ) {
        check(cycleJob?.isActive != true) { "Cycle is already running — call stop() first" }
        val context = Dispatchers.IO + CoroutineName(jobName) + (group?.let(::GroupWork) ?: EmptyCoroutineContext)
        // LAZY: skupina cyklus eviduje dřív, než začne — stop() skupiny ho tak nemine
        val job = scope.launch(context, CoroutineStart.LAZY) { play(emptyRetry, segments) }
        cycleJob = job
        if (group?.attach(job) != false) job.start()
    }

    /** Smyčka cyklu v aktuální korutině — do zrušení. */
    private suspend fun play(emptyRetry: Duration, segments: suspend () -> List<Segment>) {
        while (currentCoroutineContext().isActive) {
            // chyba provideru (I/O na síťovém mountu) cyklus nezabije — jako prázdný výsledek
            val list: List<Segment> = try {
                detached { segments() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.error("Loading segments failed", e)
                emptyList()
            }
            if (list.isEmpty()) {
                logger.warn("No segments to play — retrying in {}", emptyRetry)
                delay(emptyRetry)
                continue
            }
            for (segment in list) {
                if (!currentCoroutineContext().isActive) break
                try {
                    playSegment(segment)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // vadný segment (chybějící binárka) nesmí cyklus shodit ani roztočit naprázdno
                    logger.error("Playing segment failed", e)
                    delay(minSegment)
                }
            }
        }
    }

    private suspend fun playSegment(segment: Segment) {
        val duration = segment.duration.coerceAtLeast(minSegment)
        if (duration != segment.duration) {
            logger.warn("Segment duration {} — using minimum {}", segment.duration, minSegment)
        }

        try {
            detached { segment.prepare() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error("Segment prepare failed", e)
        }

        val startedAt = TimeSource.Monotonic.markNow()
        // coroutineScope → watchdog i souběžná akce jsou děti cyklu, zrušení cyklu je zruší taky
        coroutineScope {
            // bez restartu — životní cyklus řídí cyklus, ne watchdog; tag skupiny má přednost
            val spec = segment.spec.copy(
                restart = RestartPolicy.NEVER,
                environment = segment.spec.environment + group?.environment.orEmpty(),
            )
            val process = ManagedProcess(spec, this).apply { start() }
            _nowPlaying.value = NowPlaying(segment, process, startedAt)
            val side: Job? = segment.whilePlaying?.let { action -> launch { runSide(action) } }
            try {
                // spadlý proces (RestartPolicy.NEVER → GaveUp) segment ukončí dřív — jinak by obrazovka zůstala prázdná
                val crashed = withTimeoutOrNull(duration) { process.state.first { it is ProcessState.GaveUp } } != null
                if (crashed) {
                    logger.warn("Segment process {} exited after {} — skipping to the next segment",
                        process.spec.name, startedAt.elapsedNow())
                    // pojistka proti těsné smyčce, když padají všechny segmenty
                    delay(minSegment - startedAt.elapsedNow())
                }
            } finally {
                side?.cancel()
                // stop i po GaveUp — dorazí pozůstatky; NonCancellable, cyklus bývá zrušený
                withContext(NonCancellable) { process.stop() }
                _nowPlaying.value = null
            }
        }
    }

    private suspend fun runSide(action: suspend () -> Unit) {
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error("Segment side action failed", e)
        }
    }

    /**
     * [block] mimo strukturu cyklu — zrušení cyklu na něj nečeká. Blokující I/O na spadlém síťovém mountu nejde
     * přerušit; bez toho by [stop] (a přepnutí akce) čekal, dokud mount neodpoví.
     */
    private suspend fun <T> detached(block: suspend () -> T): T {
        val task: Deferred<T> = CoroutineScope(Dispatchers.IO).async { block() }
        try {
            return task.await()
        } finally {
            task.cancel()
        }
    }

    /** Zruší cyklus a počká na jeho doběhnutí (`finally` zastaví běžící segment). */
    suspend fun stop() {
        // bez nulování — souběžný start() by nový cyklus nechal nezastavitelný; dokončený job start nebrání
        cycleJob?.cancelAndJoin()
    }
}

/**
 * Cyklus segmentů skupiny v aktuální korutině — běží, dokud ji nikdo nezruší nebo se nezastaví skupina (pak se vrátí).
 * Segmenty nesou tag skupiny; [ProcessGroup.stop] cyklus nejdřív zruší a počká na něj, takže po zastavení skupiny
 * už další segment nezačne. Kdo potřebuje [SegmentCyclePlayer.nowPlaying], vytvoří přehrávač se `group` sám.
 */
suspend fun ProcessGroup.playCycle(
    name: String,
    minSegment: Duration = 5.seconds,
    emptyRetry: Duration = 30.seconds,
    segments: suspend () -> List<Segment>,
) {
    val group = this
    coroutineScope { SegmentCyclePlayer(this, minSegment, group).start(name, emptyRetry, segments) }
}
