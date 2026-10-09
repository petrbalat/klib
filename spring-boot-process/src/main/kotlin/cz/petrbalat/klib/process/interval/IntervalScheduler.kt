package cz.petrbalat.klib.process.interval

import cz.petrbalat.klib.process.ProcessDsl
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Scheduler časových intervalů s párovanými start/end callbacky.
 *
 * Jeden [Job] na celý interval → cancel zruší obě fáze atomicky. Pokud `onStart`
 * začal, `onEnd` se zavolá vždy — i po jeho selhání ([EndReason.StartFailed]), při cancel
 * i shutdown — díky `try/finally` + `NonCancellable`; důvod konce nese [EndReason]. Opakované [schedule] se stejným `id`
 * předchozí interval nahradí (replace-on-reschedule); nový interval startuje až
 * po úplném doběhnutí starého včetně jeho `onEnd` — cancel event starého plánu
 * tak nikdy nepředběhne start nového (a nezabije mu proces). `end == null` =
 * nekonečný interval (čeká na [cancel]/[shutdown]).
 *
 * `onStart` i `onEnd` mají strop [callbackTimeout] — zaseknutý callback by jinak (NonCancellable)
 * blokoval cancel i nový plán téhož id navždy. Strop zastaví jen suspendující callback; blokující
 * volání (I/O bez suspendu) přerušit nejde.
 *
 * Callbacky běží na [Dispatchers.Default]. Konflikty mezi intervaly tato třída NEŘEŠÍ —
 * to dělá konzument (vytlačení přes [IntervalHandle] z `onStart`). Persistenci ani validaci
 * start<end taky neřeší. Bez Spring anotací — konzument ho deklaruje jako `@Bean` (destroy = [shutdown]).
 *
 * `start`/`end` jsou lokální časy v zóně [clock] — čeká se na odpovídající okamžik ([Instant]), takže přechod
 * letního času interval neposune o hodinu. Hodiny se při čekání čtou znovu každých [clockCheck] — jeden dlouhý
 * `delay` běží na monotónním čase a skok hodin (NTP po bootu zařízení bez RTC) by nezaznamenal.
 *
 * @property clock Clock pro výpočet času do start/end; v testech přepsatelný.
 * @property callbackTimeout strop pro `onStart` i `onEnd`; `onStart` po něm = jako výjimka (`onEnd(StartFailed)`).
 * @property clockCheck nejdelší čekání bez nového čtení hodin — o tolik nejvýš se start/end opozdí po skoku hodin.
 */
class IntervalScheduler<K : Any>(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val callbackTimeout: Duration = 30.seconds,
    private val clockCheck: Duration = 30.seconds,
) {

    /**
     * Naplánovaný interval: [running] (onStart už proběhl) + [replaced] (zrušen kvůli
     * replace-on-reschedule, ne definitivně). AtomicBoolean — nastavují se z jiných vláken.
     */
    private inner class Interval(val id: K) : IntervalHandle {
        lateinit var job: Job
        val running = AtomicBoolean(false)
        val replaced = AtomicBoolean(false)

        override suspend fun cancelAndJoin() {
            // remove(id, this) — novější verzi téhož id (replace) nechá být
            synchronized(lock) { intervals.remove(id, this) }
            job.cancel(CancellationException("Interval $id preempted"))
            job.join()
        }
    }

    // SupervisorJob → pád jednoho intervalu nezruší ostatní.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("interval-scheduler")
    )

    // Aktivní intervaly podle id. Po dokončení se záznam odstraní přes
    // invokeOnCompletion variantou remove(key, value) — neodstraní novější
    // záznam, pokud byl id mezitím nahrazen (replace-on-reschedule).
    private val intervals = ConcurrentHashMap<K, Interval>()

    /** nastavený před zrušením scope — konec intervalů pak hlásí [EndReason.Shutdown] */
    private val shuttingDown = AtomicBoolean(false)

    // Serializuje remove+put v schedule/cancel — dva souběžné schedule(id) by
    // jinak mohly založit dva joby a jeden by zůstal běžet mimo mapu (nezrušitelný).
    private val lock = Any()

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Naplánuje interval [start]..[end] s callbacky z [block] (`onStart` povinný, `onEnd` a `awaitReady` volitelné).
     *
     * Život intervalu: replace předchozího (id) → čekání na `start` → `awaitReady` → `onStart`
     * → čekání na `end` (nebo `awaitCancellation` když `end == null`) → `onEnd(reason)`.
     *
     * Hrany: `start` v minulosti → onStart hned; `end` v minulosti → přeskočeno
     * celé (běžící interval téhož id skončí s [EndReason.Cancelled]); výjimka v `onStart` nebo
     * `onStart` delší než [callbackTimeout] → `onEnd(StartFailed)` — úklid případně rozběhnuté akce;
     * cancel během běžícího `onStart` → handler doběhne celý a pak přijde `onEnd`; `onEnd` delší
     * než [callbackTimeout] → zruší se (nový plán téhož id na něj čeká nejvýš tak dlouho).
     *
     * `awaitReady` běží po dosažení `start` a před `onStart` a je zrušitelný: cancel/replace
     * během čekání interval ukončí bez `onStart` i `onEnd`. Nestihne-li se do `end`, interval
     * se přeskočí. Použití: akce čeká na stažení souborů, běžící obsah mezitím hraje dál.
     */
    fun schedule(id: K, start: LocalDateTime, end: LocalDateTime?, block: IntervalBuilder.() -> Unit) {
        val callbacks = IntervalBuilder().apply(block)
        schedule(id, start, end, callbacks.startCallback(), callbacks.onEnd, callbacks.awaitReady)
    }

    private fun schedule(
        id: K,
        start: LocalDateTime,
        end: LocalDateTime?,
        onStart: suspend (IntervalHandle) -> Unit,
        onEnd: suspend (EndReason) -> Unit,
        awaitReady: suspend () -> Unit,
    ): Unit = synchronized(lock) {
        // Defenzivní guard proti stale datům: end už uplynul → není co plánovat.
        val skipped = end != null && end.toInstant() <= clock.instant()

        // Replace-on-reschedule: zruš předchozí instanci (jeho onEnd doběhne, pokud běžel).
        // replaced jen když opravdu vznikne nástupce — jinak by konzument čekal restart, který nepřijde.
        // Nastavit PŘED cancel — finally starého jobu ho čte z jiného vlákna.
        val previous: Interval? = intervals.remove(id)
        if (!skipped) previous?.replaced?.set(true)
        previous?.job?.cancel(CancellationException(if (skipped) "Interval $id cancelled" else "Interval $id replaced"))

        if (skipped) {
            logger.info("Interval {} skipped — end {} already passed (now={})", id, end, LocalDateTime.now(clock))
            return
        }

        val interval = Interval(id)
        // LAZY: job odstartuje až po zápisu do mapy — bleskově dokončený job by
        // jinak mohl přes invokeOnCompletion „odstranit" záznam dřív, než tam je.
        // Ve zrušeném scope (po shutdown) je ale job dokončený už teď — proto níže
        // nejdřív zápis, pak invokeOnCompletion (na dokončeném jobu proběhne hned).
        interval.job = scope.launch(CoroutineName("interval-$id"), start = CoroutineStart.LAZY) {
            // Při replace počkej na úplné doběhnutí starého intervalu (včetně onEnd).
            // Cancel event starého plánu tak nikdy nepředběhne start nového —
            // jinak by mohl zabít proces, který nový plán právě spustil.
            previous?.job?.join()
            delayUntil(start)

            val ready: Boolean = if (end == null) {
                awaitReady()
                true
            } else {
                awaitReadyBefore(end, awaitReady)
            }
            if (!ready) {
                logger.info("Interval {} skipped — not ready before its end {}", id, end)
                return@launch
            }

            // Gate pro onEnd: voláme ho, jakmile onStart začal (i když pak selže — úklid rozběhnuté akce).
            var attempted = false
            var started = false
            var ended = false
            val self = coroutineContext.job
            try {
                // NonCancellable: uživatel může akci zrušit, zatímco start handler
                // běží (spouští proces). Handler pak musí doběhnout a `started` se
                // nastavit — jinak by withContext po doběhlém handleru vyhodil
                // CancellationException, onEnd by se přeskočil a spuštěná akce
                // by už nikdy nedostala cancel event (proces by běžel navždy).
                withContext(NonCancellable) {
                    if (self.isCancelled) return@withContext // zrušeno těsně před startem
                    attempted = true
                    // strop — zaseknutý handler by jinak blokoval cancel i replace téhož id navždy
                    if (withTimeoutOrNull(callbackTimeout) { onStart(interval) } == null) {
                        logger.error("onStart of interval {} timed out after {}", id, callbackTimeout)
                        return@withContext
                    }
                    started = true
                    interval.running.set(true)
                }
                if (!started) return@launch
                logger.debug("Interval {}: onStart done", id)

                if (end != null) delayUntil(end) else awaitCancellation()
                ended = true
            } catch (e: CancellationException) {
                throw e // propagace nutná pro korektní cancel jobu; finally níže doběhne
            } catch (e: Throwable) {
                logger.error("Interval $id failed", e)
            } finally {
                // NonCancellable: onEnd doběhne i při shutdownu. runCatching: chyba (i timeout)
                // v onEnd se zaloguje, ale nepropaguje (neztratit původní cancel).
                if (attempted) {
                    val reason = when {
                        !started -> EndReason.StartFailed
                        ended -> EndReason.Ended
                        interval.replaced.get() -> EndReason.Replaced
                        shuttingDown.get() -> EndReason.Shutdown
                        else -> EndReason.Cancelled
                    }
                    withContext(NonCancellable) {
                        runCatching { withTimeout(callbackTimeout) { onEnd(reason) } }
                            .onFailure { logger.error("onEnd of interval $id failed", it) }
                    }
                    logger.debug("Interval {}: onEnd({}) done", id, reason)
                }
            }
        }

        intervals[id] = interval
        interval.job.invokeOnCompletion { intervals.remove(id, interval) }
        interval.job.start()
    }

    /**
     * Zruší interval podle id. Pokud běžel (onStart proběhl), jeho `onEnd`
     * doběhne asynchronně přes try/finally. Vrací `false`, pokud takový
     * aktivní interval neexistuje.
     */
    fun cancel(id: K): Boolean = removeAndCancel(id) != null

    /**
     * Jako [cancel], ale suspenduje, dokud zrušený interval úplně nedoběhne
     * (včetně `onEnd`). Použij, když na cancel navazuje další akce, která
     * nesmí předběhnout cancel event.
     */
    suspend fun cancelAndJoin(id: K): Boolean {
        val job = removeAndCancel(id) ?: return false
        job.join()
        return true
    }

    private fun removeAndCancel(id: K): Job? {
        val interval = synchronized(lock) { intervals.remove(id) } ?: return null
        interval.job.cancel(CancellationException("Interval $id cancelled"))
        return interval.job
    }

    /** `true` pokud je interval naplánován nebo běží. Pro replace/cancel nepoužívej (race) — volej rovnou [schedule]/[cancel]. */
    fun isActive(id: K): Boolean = intervals[id]?.job?.isActive == true

    /** Snapshot id všech naplánovaných intervalů (včetně čekajících na svůj start). */
    fun activeIds(): Set<K> = intervals.keys.toSet()

    /**
     * Snapshot id intervalů, jejichž `onStart` už proběhl (skutečně běží).
     * Na rozdíl od [activeIds] sem nepatří ty čekající na budoucí start.
     */
    fun runningIds(): Set<K> = intervals.filterValues { it.running.get() }.keys

    /**
     * Zruší scope → všechny intervaly (u `@Bean` ho Spring volá při zániku kontextu) a blokuje, dokud jejich
     * `onEnd` (kde proběhl onStart) nedoběhnou — nejvýš 2 × [callbackTimeout] (rozběhnutý `onStart` + `onEnd`).
     * Nevolat z callbacku intervalu — čekal by sám na sebe až do limitu.
     */
    fun shutdown() {
        logger.info("Shutting down IntervalScheduler — cancelling {} intervals", intervals.size)
        val job = scope.coroutineContext.job
        shuttingDown.set(true)
        job.cancel(CancellationException("IntervalScheduler shutdown"))
        // blokující callback strop nezastaví — po limitu jen přestaneme čekat
        val limit = callbackTimeout * 2
        if (runBlocking { withTimeoutOrNull(limit) { job.join() } } == null) {
            logger.warn("IntervalScheduler callbacks did not finish within {}", limit)
        }
    }

    /** Suspenduje do [target] (podle [clock], po krocích [clockCheck]); minulost/přítomnost → vrací se hned. */
    private suspend fun delayUntil(target: LocalDateTime) {
        val deadline = target.toInstant()
        var millis = millisUntil(deadline)
        while (millis > 0) {
            delay(millis.milliseconds.coerceAtMost(clockCheck))
            millis = millisUntil(deadline)
        }
    }

    /** Čeká na [awaitReady] nejvýš do [end]; false = nestihlo se (awaitReady se zruší). */
    private suspend fun awaitReadyBefore(end: LocalDateTime, awaitReady: suspend () -> Unit): Boolean = coroutineScope {
        val ready = async { awaitReady() }
        val deadline = end.toInstant()
        var millis = millisUntil(deadline)
        while (millis > 0) {
            if (withTimeoutOrNull(millis.milliseconds.coerceAtMost(clockCheck)) { ready.await() } != null) return@coroutineScope true
            millis = millisUntil(deadline)
        }
        ready.cancel()
        false
    }

    /** Lokální čas v zóně [clock] → okamžik; v mezeře přechodu letního času se posune dopředu. */
    private fun LocalDateTime.toInstant(): Instant = atZone(clock.zone).toInstant()

    private fun millisUntil(target: Instant): Long = clock.instant().until(target, ChronoUnit.MILLIS)
}

/**
 * Jedno konkrétní naplánování intervalu (předává se do `onStart`). Na rozdíl od id se nesplete
 * s novější verzí plánu po replace-on-reschedule — vytlačení tak nikdy nezruší (a nečeká na) nový start.
 */
interface IntervalHandle {
    /** Zruší tento interval a počká na jeho úplné doběhnutí včetně `onEnd`; už skončený = no-op. */
    suspend fun cancelAndJoin()
}

/** Proč interval skončil — `onEnd(reason)`. */
enum class EndReason {
    /** dosažen `end` */
    Ended,

    /** `cancel(id)`, `cancelAndJoin(id)`, [IntervalHandle.cancelAndJoin] nebo nový plán téhož id s koncem v minulosti */
    Cancelled,

    /** nahrazen novým plánem téhož id — akce se hned restartuje, neoznačovat jako ukončenou */
    Replaced,

    /** [IntervalScheduler.shutdown] — vypnutí aplikace; plán po jejím startu pokračuje, neoznačovat jako ukončený */
    Shutdown,

    /** `onStart` vyhodil výjimku nebo překročil `callbackTimeout` — úklid případně rozběhnuté akce */
    StartFailed,
}

/** Callbacky intervalu v DSL — `schedule(id, start, end) { awaitReady { … }; onStart { … }; onEnd { reason -> … } }`. */
@ProcessDsl
class IntervalBuilder internal constructor() {
    private var onStart: (suspend (IntervalHandle) -> Unit)? = null
    internal var onEnd: suspend (EndReason) -> Unit = {}
        private set
    internal var awaitReady: suspend () -> Unit = {}
        private set

    /** po dosažení `start`, před `onStart`; zrušitelné — nestihne-li se do `end`, interval se přeskočí */
    fun awaitReady(block: suspend () -> Unit) {
        awaitReady = block
    }

    /** povinné; [IntervalHandle] vytlačí konfliktní interval bez rizika záměny s novější verzí plánu */
    fun onStart(block: suspend (IntervalHandle) -> Unit) {
        onStart = block
    }

    fun onEnd(block: suspend (EndReason) -> Unit) {
        onEnd = block
    }

    internal fun startCallback(): suspend (IntervalHandle) -> Unit = checkNotNull(onStart) { "onStart is not set" }
}
