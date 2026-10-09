package cz.petrbalat.klib.process.interval

import cz.petrbalat.klib.jdk.datetime.now
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Testy pro [IntervalScheduler].
 *
 * # Vrstvení testů
 *
 * Tento soubor testuje plánovací vrstvu — **kdy** interval startuje a končí
 * (replace-on-reschedule, sync/async exkluzivita). Vnitřní cyklus jednoho běžícího
 * intervalu (střídání procesů) testuje `SegmentCyclePlayerTest` stejným přístupem.
 *
 * Testy běží v reálném čase s krátkými durationy (~50–500 ms). Záměrně
 * nepoužíváme `runTest` s virtual time, protože:
 *
 *  1. [IntervalScheduler] počítá delay z `LocalDateTime.now(clock)`, takže
 *     virtual time by vyžadoval i fake clock — což by zamlžilo testovanou
 *     logiku.
 *  2. Real-time testy zachytí i timing edge cases, které virtual time
 *     skryje (např. race podmínky mezi cancellací a finally blokem).
 *
 * Cena: každý test trvá několik set ms. Pro tuto třídu je to akceptovatelné.
 *
 * # Tolerance
 *
 * Pro asserce o časování používáme **tolerance ±100 ms** — tj. když
 * očekáváme spuštění za 200 ms, akceptujeme 100–300 ms. JVM scheduling +
 * coroutine overhead nezaručuje přesnost lepší než ~10 ms a slow CI runner
 * může klidně přidat dalších 50 ms.
 *
 * # Pomocné konstanty
 *
 * Pro čitelnost testů extrahované do top-levelu:
 *  - [SHORT_DELAY] — typický interval, kdy chceme rychle spustit (~50ms)
 *  - [MEDIUM_DELAY] — interval, který běží/čeká (~150ms)
 *  - [SETTLE_TIME] — pauza, kterou dáváme schedulleru na dokončení akce (~250ms)
 */
class IntervalSchedulerTest {

    private lateinit var scheduler: IntervalScheduler<Int>

    @BeforeEach
    fun setUp() {
        // Vytváříme fresh scheduler per test, aby testy byly izolované.
        // Použití systémových hodin (default) — testy běží v reálném čase.
        scheduler = IntervalScheduler()
    }

    @AfterEach
    fun tearDown() {
        // Korektní úklid — zruší případné hangující intervaly z testu.
        // Důležité, aby se threads nezbylí mezi testy a nezpůsobovali interference.
        scheduler.shutdown()
    }

    // ============================================================
    //  ZÁKLADNÍ FUNKČNOST
    // ============================================================

    @Test
    fun `onStart se zavolá v naplánovaný čas`() = runBlocking {
        val started = AtomicBoolean(false)

        scheduler.schedule(1, now.plusNanos(MEDIUM_DELAY * 1_000_000), now.plusSeconds(5)) {
            onStart { started.set(true) }
            onEnd {}
        }

        // Před uplynutím start času ještě nesmí být spuštěno
        assertFalse(started.get(), "onStart se nesmí volat před start časem")

        // Počkáme dost dlouho na to, aby se start spustil
        delay(SETTLE_TIME.milliseconds)

        assertTrue(started.get(), "onStart musí být zavolán po start času")
    }

    @Test
    fun `onEnd se zavolá v naplánovaný čas po onStart`() = runBlocking {
        val events = ConcurrentLinkedQueue<String>()

        val start = now.plusNanos(SHORT_DELAY * 1_000_000)
        scheduler.schedule(1, start, start.plusNanos(MEDIUM_DELAY * 1_000_000)) {
            onStart { events.add("start") }
            onEnd { events.add("end") }
        }

        // Po doběhnutí celého intervalu + rezerva
        delay((SHORT_DELAY + MEDIUM_DELAY + SETTLE_TIME).milliseconds)

        assertContentEquals(
            listOf("start", "end"),
            events.toList(),
            "Pořadí callbacků musí být start → end",
        )
    }

    @Test
    fun `start v minulosti spustí onStart okamžitě`() = runBlocking {
        val started = AtomicBoolean(false)

        scheduler.schedule(1, now.minusMinutes(5), now.plusSeconds(5)) { // start už dávno
            onStart { started.set(true) }
        }

        // Krátká pauza pro dispatch — ne čekání na start.
        delay(SETTLE_TIME.milliseconds)

        assertTrue(started.get(), "Plán se startem v minulosti se musí spustit hned")
    }

    @Test
    fun `end v minulosti přeskočí celý interval`() = runBlocking {
        val started = AtomicBoolean(false)
        val ended = AtomicBoolean(false)

        scheduler.schedule(1, now.minusMinutes(10), now.minusMinutes(5)) { // end už skončilo
            onStart { started.set(true) }
            onEnd { ended.set(true) }
        }

        delay(SETTLE_TIME.milliseconds)

        assertFalse(started.get(), "Plán s end v minulosti se nesmí spustit")
        assertFalse(ended.get(), "Plán s end v minulosti nesmí volat onEnd")
        assertFalse(scheduler.isActive(1), "Plán s end v minulosti nesmí být active")
    }

    // ============================================================
    //  CANCELLATION
    // ============================================================

    @Test
    fun `cancel před startem nezavolá ani onStart ani onEnd`() = runBlocking {
        val started = AtomicBoolean(false)
        val ended = AtomicBoolean(false)

        scheduler.schedule(1, now.plusNanos(MEDIUM_DELAY * 1_000_000), now.plusSeconds(5)) {
            onStart { started.set(true) }
            onEnd { ended.set(true) }
        }

        // Cancel ještě před uplynutím start
        delay(SHORT_DELAY.milliseconds)
        val cancelled = scheduler.cancel(1)
        assertTrue(cancelled, "cancel musí vrátit true pro existující interval")

        // Počkáme déle, než by trvalo doběhnutí intervalu
        delay(SETTLE_TIME.milliseconds)

        assertFalse(started.get(), "onStart se nesmí volat po cancelu před startem")
        assertFalse(ended.get(), "onEnd se nesmí volat, když onStart neproběhl")
    }

    @Test
    fun `cancel uprostřed intervalu zavolá onEnd`() = runBlocking {
        val started = AtomicBoolean(false)
        val ended = AtomicBoolean(false)

        scheduler.schedule(1, now, now.plusSeconds(10)) { // start hned, end dlouho
            onStart { started.set(true) }
            onEnd { ended.set(true) }
        }

        // Počkáme než onStart proběhne
        delay(SETTLE_TIME.milliseconds)
        assertTrue(started.get(), "onStart musí proběhnout před cancelem")
        assertFalse(ended.get(), "onEnd ještě nesmí běžet")

        // Cancel uprostřed
        scheduler.cancel(1)
        delay(SETTLE_TIME.milliseconds)

        assertTrue(ended.get(), "onEnd se musí zavolat při cancelu po onStart")
    }

    @Test
    fun `schedule bez onStart selže hned při plánování`() {
        val error = assertFailsWith<IllegalStateException> { scheduler.schedule(1, now, null) { onEnd {} } }
        assertEquals("onStart is not set", error.message)
        assertTrue(scheduler.activeIds().isEmpty())
    }

    @Test
    fun `cancel neexistujícího intervalu vrátí false`() {
        val result = scheduler.cancel(999)
        assertFalse(result, "cancel pro neexistující id musí vrátit false")
    }

    @Test
    fun `null end znamená nekonečný interval - běží do cancelu`() = runBlocking {
        val started = AtomicBoolean(false)
        val ended = AtomicBoolean(false)

        scheduler.schedule(1, now, null) { // end = null → nekonečný
            onStart { started.set(true) }
            onEnd { ended.set(true) }
        }

        delay(SETTLE_TIME.milliseconds)
        assertTrue(started.get(), "onStart musí proběhnout")
        assertFalse(ended.get(), "onEnd nesmí běžet pro nekonečný interval")
        assertTrue(scheduler.isActive(1), "Nekonečný interval musí být active")

        // Externí cancel
        scheduler.cancel(1)
        delay(SETTLE_TIME.milliseconds)

        assertTrue(ended.get(), "onEnd musí proběhnout po cancelu nekonečného intervalu")
        assertFalse(scheduler.isActive(1), "Po cancelu už nesmí být active")
    }

    // ============================================================
    //  RESCHEDULE (replace-on-reschedule)
    // ============================================================

    @Test
    fun `reschedule se stejným id zruší předchozí`() = runBlocking {
        val firstStarted = AtomicBoolean(false)
        val secondStarted = AtomicBoolean(false)

        // První plán s pozdějším startem
        scheduler.schedule(1, now.plusSeconds(2), now.plusSeconds(5)) {
            onStart { firstStarted.set(true) }
        }

        // Hned přepiš novým plánem se stejným id, ale dřívějším startem
        scheduler.schedule(1, now.plusNanos(SHORT_DELAY * 1_000_000), now.plusSeconds(5)) {
            onStart { secondStarted.set(true) }
        }

        // Počkáme na druhý plán
        delay(SETTLE_TIME.milliseconds)

        assertTrue(secondStarted.get(), "Druhý plán se musí spustit")
        assertFalse(firstStarted.get(), "První plán se nesmí spustit (byl zrušen)")
    }

    @Test
    fun `reschedule po onStart prvního zavolá onEnd starého před spuštěním nového`() = runBlocking {
        val events = ConcurrentLinkedQueue<String>()

        // První plán — start okamžitý, dlouhý běh
        scheduler.schedule(1, now, now.plusSeconds(10)) {
            onStart { events.add("first-start") }
            onEnd { events.add("first-end") }
        }

        // Počkáme než první plán dojde do onStart
        delay(SETTLE_TIME.milliseconds)
        assertTrue(events.contains("first-start"))

        // Replace stejným id — měl by zrušit první (zavolat first-end) a založit druhý
        scheduler.schedule(1, now, now.plusSeconds(10)) {
            onStart { events.add("second-start") }
            onEnd { events.add("second-end") }
        }

        delay(SETTLE_TIME.milliseconds)

        // Nový interval joinuje starý → pořadí first-end před second-start je garantované.
        assertContentEquals(
            listOf("first-start", "first-end", "second-start"),
            events.toList(),
            "onEnd starého intervalu musí doběhnout před onStart nového (replace)",
        )
    }

    @Test
    fun `onEnd dostane Replaced při replace, Cancelled při externím cancelu a Ended při natural endu`() = runBlocking {
        val endReasons = ConcurrentLinkedQueue<String>()

        // 1) Replace-on-reschedule → onEnd(Replaced)
        scheduler.schedule(1, now, null) {
            onStart {}
            onEnd { reason -> endReasons.add("replace:$reason") }
        }
        delay(SETTLE_TIME.milliseconds) // počkej na onStart
        scheduler.schedule(1, now, null) {
            onStart {}
            onEnd { reason -> endReasons.add("external:$reason") }
        }
        delay(SETTLE_TIME.milliseconds)

        // 2) Externí cancel (terminate, sync exkluzivita) → onEnd(Cancelled)
        scheduler.cancel(1)
        delay(SETTLE_TIME.milliseconds)

        // 3) Natural end → onEnd(Ended)
        scheduler.schedule(2, now, now.plusNanos(SHORT_DELAY * 1_000_000)) {
            onStart {}
            onEnd { reason -> endReasons.add("natural:$reason") }
        }
        delay(SHORT_DELAY + SETTLE_TIME)

        assertContentEquals(
            listOf("replace:Replaced", "external:Cancelled", "natural:Ended"),
            endReasons.toList(),
            "Replaced smí přijít jen při replace-on-reschedule",
        )
    }

    @Test
    fun `nový plán s koncem v minulosti ukončí běžící interval jako Cancelled, ne Replaced`() = runBlocking {
        val endReasons = ConcurrentLinkedQueue<EndReason>()
        scheduler.schedule(1, now, now.plusHours(1)) {
            onStart {}
            onEnd { reason -> endReasons.add(reason) }
        }
        delay(SETTLE_TIME.milliseconds) // počkej na onStart

        // akce zkrácená do minulosti — nic ji nenahradí, konzument ji musí uzavřít
        scheduler.schedule(1, now.minusHours(2), now.minusHours(1)) { onStart {} }
        delay(SETTLE_TIME.milliseconds)

        assertContentEquals(listOf(EndReason.Cancelled), endReasons.toList(), "Replaced slibuje restart, který nepřijde")
        assertTrue(scheduler.activeIds().isEmpty())
    }

    @Test
    fun `cancel během běžícího onStart nechá handler doběhnout a zavolá onEnd`() = runBlocking {
        val events = ConcurrentLinkedQueue<String>()

        scheduler.schedule(1, now, now.plusSeconds(10)) {
            onStart {
                events.add("start-begin")
                delay(MEDIUM_DELAY) // simulace delšího startu (spouštění procesu)
                events.add("start-done")
            }
            onEnd { events.add("end") }
        }

        // Cancel dorazí uprostřed běžícího onStart — dřív by onEnd propadl
        // (started zůstal false) a spuštěná akce by nikdy nedostala cancel event.
        delay(SHORT_DELAY)
        scheduler.cancel(1)
        delay(SETTLE_TIME.milliseconds)

        assertContentEquals(
            listOf("start-begin", "start-done", "end"),
            events.toList(),
            "onStart musí doběhnout celý a onEnd musí následovat i při cancelu uprostřed startu",
        )
    }

    @Test
    fun `cancelAndJoin se vrátí až po dokončení onEnd`() = runBlocking {
        val ended = AtomicBoolean(false)

        scheduler.schedule(1, now, null) {
            onStart {}
            onEnd {
                delay(MEDIUM_DELAY)
                ended.set(true)
            }
        }

        delay(SETTLE_TIME.milliseconds) // počkej na onStart

        assertTrue(scheduler.cancelAndJoin(1), "cancelAndJoin musí vrátit true pro běžící interval")
        assertTrue(ended.get(), "Po návratu z cancelAndJoin už musí být onEnd dokončen")
        assertFalse(scheduler.cancelAndJoin(1), "Opakovaný cancelAndJoin musí vrátit false")
    }

    @Test
    fun `awaitReady odloží onStart, dokud nevrátí`() = runBlocking {
        val ready = CompletableDeferred<Unit>()
        val started = AtomicBoolean(false)
        scheduler.schedule(1, now, null) { onStart { started.set(true) }; awaitReady { ready.await() } }

        delay(SETTLE_TIME.milliseconds)
        assertFalse(started.get(), "onStart nesmí proběhnout před připraveností")
        assertTrue(1 !in scheduler.runningIds())

        ready.complete(Unit)
        delay(SETTLE_TIME.milliseconds)
        assertTrue(started.get())
    }

    @Test
    fun `cancel během awaitReady ukončí interval bez onStart i onEnd`() = runBlocking {
        val started = AtomicBoolean(false)
        val ended = AtomicBoolean(false)
        scheduler.schedule(1, now, null) {
            onStart { started.set(true) }
            onEnd { ended.set(true) }
            awaitReady { awaitCancellation() }
        }
        delay(SHORT_DELAY.milliseconds)

        assertTrue(scheduler.cancelAndJoin(1))
        assertFalse(started.get())
        assertFalse(ended.get())
    }

    @Test
    fun `nepřipravený do konce intervalu se přeskočí`() = runBlocking {
        val started = AtomicBoolean(false)
        scheduler.schedule(1, now, now.plusNanos(MEDIUM_DELAY * 1_000_000)) {
            onStart { started.set(true) }
            awaitReady { awaitCancellation() }
        }

        delay(SETTLE_TIME.milliseconds)
        assertFalse(started.get())
        assertFalse(scheduler.isActive(1))
    }

    @Test
    fun `výjimka v awaitReady interval přeskočí bez onStart i onEnd, scheduler běží dál`() = runBlocking {
        val events = ConcurrentLinkedQueue<String>()
        // stažení souborů akce selhalo — nekonečný i ohraničený interval
        scheduler.schedule(1, now, null) {
            onStart { events.add("1-start") }
            onEnd { events.add("1-end") }
            awaitReady { error("download failed") }
        }
        scheduler.schedule(2, now, now.plusSeconds(10)) {
            onStart { events.add("2-start") }
            onEnd { events.add("2-end") }
            awaitReady { error("download failed") }
        }
        scheduler.schedule(3, now, null) { onStart { events.add("3-start") } }

        delay(SETTLE_TIME.milliseconds)
        assertEquals(listOf("3-start"), events.toList())
        assertEquals(setOf(3), scheduler.activeIds(), "selhané intervaly nesmí zůstat v registru")

        // další pokus (nový plán ze serveru) proběhne normálně
        scheduler.schedule(1, now, null) { onStart { events.add("1-retry") } }
        delay(SETTLE_TIME.milliseconds)
        assertEquals(setOf(1, 3), scheduler.runningIds())
        assertTrue("1-retry" in events)
    }

    @Test
    fun `handle z onStart zruší svůj interval a počká na onEnd`() = runBlocking {
        val ended = AtomicBoolean(false)
        val handle = CompletableDeferred<IntervalHandle>()
        scheduler.schedule(1, now, null) { onStart { handle.complete(it) }; onEnd { ended.set(true) } }

        handle.await().cancelAndJoin()
        assertTrue(ended.get(), "Po návratu už musí být onEnd dokončen")
        assertFalse(scheduler.isActive(1))
    }

    @Test
    fun `handle staré verze nezruší novou verzi po replace`() = runBlocking {
        val handles = Channel<IntervalHandle>(Channel.UNLIMITED)
        scheduler.schedule(1, now, null) { onStart { handles.send(it) } }
        val old = handles.receive()
        scheduler.schedule(1, now, null) { onStart { handles.send(it) } }
        handles.receive() // nová verze je v onStart
        // running se nastaví až po návratu z onStart
        withTimeout(SETTLE_TIME.milliseconds) { while (1 !in scheduler.runningIds()) delay(5) }

        old.cancelAndJoin()
        assertTrue(scheduler.isActive(1), "Nová verze intervalu musí běžet dál")
        assertTrue(1 in scheduler.runningIds())
    }

    // ============================================================
    //  CHYBOVÉ STAVY
    // ============================================================

    @Test
    fun `výjimka v onStart zavolá onEnd(StartFailed) - úklid rozběhnuté akce`() = runBlocking {
        val endReasons = ConcurrentLinkedQueue<EndReason>()

        scheduler.schedule(1, now, now.plusNanos(MEDIUM_DELAY * 1_000_000)) {
            // proces už běží, pak selže zbytek startu — bez onEnd by běžel navždy
            onStart { error("simulovaná chyba v onStart") }
            onEnd { endReasons.add(it) }
        }

        delay(SETTLE_TIME + MEDIUM_DELAY)

        assertContentEquals(listOf(EndReason.StartFailed), endReasons.toList())
        assertTrue(scheduler.activeIds().isEmpty())
    }

    @Test
    fun `výjimka v onEnd se nepropaguje a neshodí scheduler`() = runBlocking {
        val firstFailed = AtomicBoolean(false)
        val secondStarted = AtomicBoolean(false)

        // První plán — onEnd shodí výjimku
        scheduler.schedule(1, now, now.plusNanos(SHORT_DELAY * 1_000_000)) {
            onStart { firstFailed.set(true) }
            onEnd { error("simulovaná chyba v onEnd") }
        }

        // Druhý plán by měl normálně proběhnout — supervisor scope nepouští výjimky dál
        scheduler.schedule(2, now.plusNanos(MEDIUM_DELAY * 1_000_000), now.plusSeconds(2)) {
            onStart { secondStarted.set(true) }
        }

        delay(SETTLE_TIME * 2)

        assertTrue(firstFailed.get(), "První onStart musel proběhnout")
        assertTrue(secondStarted.get(), "Druhý plán musí běžet i přes chybu v prvním onEnd")
    }

    @Test
    fun `zaseknutý onEnd starého plánu nezablokuje nový plán navždy`() = runBlocking {
        val scheduler = IntervalScheduler<Int>(callbackTimeout = CALLBACK_TIMEOUT.milliseconds)
        val hang = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        try {
            // onEnd starého plánu visí (proces nejde zastavit, zaseknutý I/O)
            scheduler.schedule(1, now, null) { onStart {}; onEnd { hang.await() } }
            delay(SETTLE_TIME.milliseconds) // počkej na onStart

            // server pošle nový plán téhož id — kiosk nesmí zůstat navždy na starém obsahu
            scheduler.schedule(1, now, null) { onStart { secondStarted.complete(Unit) } }

            assertTrue(
                withTimeoutOrNull(HANG_LIMIT.milliseconds) { secondStarted.await() } != null,
                "nový plán čeká na zaseknutý onEnd starého",
            )
            assertEquals(setOf(1), scheduler.runningIds())
        } finally {
            hang.complete(Unit)
            scheduler.shutdown()
        }
    }

    @Test
    fun `zaseknutý onStart nezablokuje cancelAndJoin navždy a dostane onEnd(StartFailed)`() = runBlocking {
        val scheduler = IntervalScheduler<Int>(callbackTimeout = CALLBACK_TIMEOUT.milliseconds)
        val hang = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val endReasons = ConcurrentLinkedQueue<EndReason>()
        try {
            // onStart visí (spouštění procesu čeká na zařízení, které nepřijde)
            scheduler.schedule(1, now, null) {
                onStart { entered.complete(Unit); hang.await() }
                onEnd { endReasons.add(it) }
            }
            withTimeout(2_000.milliseconds) { entered.await() }

            val cancelled = withTimeoutOrNull(HANG_LIMIT.milliseconds) { scheduler.cancelAndJoin(1) }
            assertTrue(cancelled != null, "cancelAndJoin čeká na zaseknutý onStart")
            // onStart po timeoutu = selhal → úklid přes onEnd
            assertContentEquals(listOf(EndReason.StartFailed), endReasons.toList())
            assertEquals(emptySet(), scheduler.activeIds())
        } finally {
            hang.complete(Unit)
            scheduler.shutdown()
        }
    }

    @Test
    fun `zaseknutý onStart bez cancelu ukončí interval po callbackTimeout`() = runBlocking {
        val scheduler = IntervalScheduler<Int>(callbackTimeout = CALLBACK_TIMEOUT.milliseconds)
        val hang = CompletableDeferred<Unit>()
        try {
            scheduler.schedule(1, now, null) { onStart { hang.await() } }
            delay((CALLBACK_TIMEOUT + SETTLE_TIME).milliseconds)

            assertFalse(scheduler.isActive(1), "interval s neproběhlým onStart nesmí viset v registru")
            assertEquals(emptySet(), scheduler.runningIds())
        } finally {
            hang.complete(Unit)
            scheduler.shutdown()
        }
    }

    // ============================================================
    //  ASYNC PŘÍPAD — paralelní běh akcí
    // ============================================================

    /**
     * Asynchronní (paralelní) případ.
     *
     * Plánujeme 5 intervalů, jejichž `onStart` všechny startují ve stejný čas.
     * Každý `onStart` simuluje I/O práci přes `delay(150ms)`. Pokud běží
     * paralelně, celkový čas ≈ 150 ms. Pokud by se serializovaly, byl by
     * to ≈ 750 ms.
     *
     * Měříme také peak concurrency (max počet souběžně běžících onStartů) —
     * při paralelní exekuci musí být ≥ 2.
     */
    @Test
    fun `async - více intervalů běží paralelně`() = runBlocking {
        val concurrent = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val completed = AtomicInteger(0)

        val intervalCount = 5
        val workDuration = MEDIUM_DELAY

        val startTime = now.plusNanos(SHORT_DELAY * 1_000_000)
        val endTime = startTime.plusSeconds(10)

        // Naplánuj N intervalů se stejným start časem
        repeat(intervalCount) { i ->
            scheduler.schedule(100 + i, startTime, endTime) {
                // Atomic update maxima — jen pokud je current vyšší než aktuální max
                onStart {
                    val current = concurrent.incrementAndGet()
                    maxConcurrent.updateAndGet { max -> maxOf(max, current) }
                    delay(workDuration) // simulace I/O
                    concurrent.decrementAndGet()
                    completed.incrementAndGet()
                }
            }
        }

        // Čekání: start delay + work duration + rezerva
        delay(SHORT_DELAY + workDuration + SETTLE_TIME)

        assertEquals(intervalCount, completed.get(), "Všechny intervaly musí dokončit onStart")
        assertTrue(
            maxConcurrent.get() >= 2,
            "Async intervaly musí běžet paralelně (max concurrent=${maxConcurrent.get()})",
        )
    }

    // ============================================================
    //  SYNC PŘÍPAD — exkluzivní běh přes single-thread dispatcher
    // ============================================================

    /**
     * Synchronní (exkluzivní) případ.
     *
     * IntervalScheduler sám o sobě sync sémantiku nevynucuje — to je
     * záměrné, aby zůstal generický. Sync exklusivita se realizuje přes
     * `withContext(syncDispatcher)` v callbacku, kde `syncDispatcher` je
     * single-thread executor.
     *
     * Test plánuje 4 intervaly se stejným start časem, každý vykoná
     * v kritické sekci 100 ms práce. Pokud sync funguje:
     *  - max concurrency v kritické sekci = 1
     *  - celkový čas ≈ 4 × 100 ms = 400 ms
     *  - pořadí dokončení odpovídá pořadí naplánování (FIFO single-thread)
     *
     * Pozn.: Pořadí spuštění onStart není striktně FIFO — všechny coroutiny
     * dorazí do `withContext(syncDispatcher)` přibližně současně a
     * dispatcher je řadí podle interní fronty. Test ověřuje pouze
     * **exklusivitu**, ne pořadí.
     */
    @Test
    fun `sync - více intervalů serializuje přes single-thread dispatcher`() = runBlocking {
        // Single-thread executor → na něm coroutines běží sériově.
        // V produkci tohle bude existující eventSyncExecutor z ClientAsyncConfig.
        val syncExecutor = Executors.newSingleThreadExecutor()
        val syncDispatcher = syncExecutor.asCoroutineDispatcher()

        try {
            val concurrent = AtomicInteger(0)
            val maxConcurrent = AtomicInteger(0)
            val completed = AtomicInteger(0)

            val intervalCount = 4
            val workDuration = 100L

            val startTime = now.plusNanos(SHORT_DELAY * 1_000_000)

            repeat(intervalCount) { i ->
                scheduler.schedule(200 + i, startTime, startTime.plusSeconds(10)) {
                    // KLÍČOVÁ ČÁST: wrap přes syncDispatcher zajišťuje exklusivitu.
                    // Bez withContext by callback běžel na Dispatchers.Default
                    // a sync sémantika by byla porušena.
                    // Bez delay (delay by suspendoval coroutine a uvolnil thread).
                    // Použijeme Thread.sleep, aby thread byl skutečně blokován —
                    // simuluje synchronní akci typu spuštění mpv/chromium.
                    onStart {
                        withContext(syncDispatcher) {
                            val current = concurrent.incrementAndGet()
                            maxConcurrent.updateAndGet { max -> maxOf(max, current) }

                            Thread.sleep(workDuration)

                            concurrent.decrementAndGet()
                            completed.incrementAndGet()
                        }
                    }
                }
            }

            // Čekání: start delay + (count × work) + rezerva
            delay(SHORT_DELAY + (intervalCount * workDuration) + SETTLE_TIME)

            assertEquals(
                intervalCount,
                completed.get(),
                "Všechny sync intervaly musí dokončit onStart",
            )
            assertEquals(
                1,
                maxConcurrent.get(),
                "Sync intervaly nesmí běžet paralelně (max concurrent=${maxConcurrent.get()})",
            )
        } finally {
            syncDispatcher.close()
            syncExecutor.shutdown()
        }
    }

    /**
     * Mix sync + async ve stejném scheduleru.
     *
     * Ověřuje, že IntervalScheduler je generický — ve stejné instanci
     * mohou koexistovat sync (přes withContext) a async (default) intervaly,
     * aniž by se navzájem blokovaly.
     */
    @Test
    fun `mix sync a async - sync se serializují, async běží paralelně`() = runBlocking {
        val syncExecutor = Executors.newSingleThreadExecutor()
        val syncDispatcher = syncExecutor.asCoroutineDispatcher()

        try {
            val syncConcurrent = AtomicInteger(0)
            val syncMaxConcurrent = AtomicInteger(0)
            val asyncConcurrent = AtomicInteger(0)
            val asyncMaxConcurrent = AtomicInteger(0)

            val workDuration = 100L
            val startTime = now.plusNanos(SHORT_DELAY * 1_000_000)

            // 3 sync intervaly
            repeat(3) { i ->
                scheduler.schedule(300 + i, startTime, startTime.plusSeconds(10)) {
                    onStart {
                        withContext(syncDispatcher) {
                            val cur = syncConcurrent.incrementAndGet()
                            syncMaxConcurrent.updateAndGet { maxOf(it, cur) }
                            Thread.sleep(workDuration)
                            syncConcurrent.decrementAndGet()
                        }
                    }
                }
            }

            // 3 async intervaly
            repeat(3) { i ->
                scheduler.schedule(400 + i, startTime, startTime.plusSeconds(10)) {
                    // Bez wrappingu — běží na Dispatchers.Default paralelně
                    onStart {
                        val cur = asyncConcurrent.incrementAndGet()
                        asyncMaxConcurrent.updateAndGet { maxOf(it, cur) }
                        delay(workDuration)
                        asyncConcurrent.decrementAndGet()
                    }
                }
            }

            // Čekání: dost dlouho na to, aby všechno doběhlo
            delay(SHORT_DELAY + (3 * workDuration) + SETTLE_TIME)

            assertEquals(1, syncMaxConcurrent.get(), "Sync intervaly se musí serializovat")
            assertTrue(
                asyncMaxConcurrent.get() >= 2,
                "Async intervaly musí běžet paralelně (max=${asyncMaxConcurrent.get()})",
            )
        } finally {
            syncDispatcher.close()
            syncExecutor.shutdown()
        }
    }

    // ============================================================
    //  LIFECYCLE / INTROSPECTION
    // ============================================================

    @Test
    fun `activeIds vrací aktuálně naplánované id`() = runBlocking {
        scheduler.schedule(1, now.plusSeconds(10), now.plusSeconds(20)) {
            onStart {}
        }
        scheduler.schedule(2, now.plusSeconds(10), null) {
            onStart {}
        }

        val ids = scheduler.activeIds()
        assertEquals(setOf(1, 2), ids)
    }

    @Test
    fun `runningIds obsahuje jen intervaly po onStart, ne čekající na start`() = runBlocking {
        // Běžící: start v minulosti → onStart proběhne hned.
        scheduler.schedule(1, now, null) {
            onStart {}
        }
        // Naplánovaný-ale-nespuštěný: start v budoucnu, čeká v delayUntil.
        scheduler.schedule(2, now.plusSeconds(10), now.plusSeconds(20)) {
            onStart {}
        }

        delay(SETTLE_TIME.milliseconds)

        // Oba jsou naplánované, ale běží jen ten první.
        assertEquals(setOf(1, 2), scheduler.activeIds())
        assertEquals(
            setOf(1),
            scheduler.runningIds(),
            "runningIds nesmí obsahovat interval čekající na svůj budoucí start",
        )
    }

    @Test
    fun `runningIds přestane obsahovat interval po onEnd`() = runBlocking {
        scheduler.schedule(1, now, null) {
            onStart {}
            onEnd {}
        }

        delay(SETTLE_TIME.milliseconds)
        assertEquals(setOf(1), scheduler.runningIds())

        scheduler.cancel(1)
        delay(SETTLE_TIME.milliseconds)
        assertTrue(scheduler.runningIds().isEmpty(), "Po cancelu/onEnd už nesmí být v runningIds")
    }

    @Test
    fun `isActive vrací true pro běžící, false jinak`() = runBlocking {
        scheduler.schedule(1, now, null) {
            onStart {}
        }

        delay(SETTLE_TIME.milliseconds)
        assertTrue(scheduler.isActive(1))
        assertFalse(scheduler.isActive(999))

        scheduler.cancel(1)
        delay(SETTLE_TIME.milliseconds)
        assertFalse(scheduler.isActive(1), "Po cancelu už nesmí být active")
    }

    @Test
    fun `shutdown zavolá onEnd(Shutdown) pro všechny aktivní intervaly`() = runBlocking {
        val ended = ConcurrentLinkedQueue<Int>()
        val endReasons = ConcurrentLinkedQueue<EndReason>()

        // Naplánuj 3 nekonečné intervaly
        repeat(3) { i ->
            scheduler.schedule(500 + i, now, null) {
                onStart {}
                onEnd {
                    ended.add(500 + i)
                    endReasons.add(it)
                }
            }
        }

        // Počkej až všechny dorazí do onStart
        delay(SETTLE_TIME.milliseconds)
        assertEquals(3, scheduler.activeIds().size)

        // Shutdown by měl zrušit všechny a spustit jejich onEnd v NonCancellable
        scheduler.shutdown()
        delay(SETTLE_TIME.milliseconds)

        assertEquals(
            setOf(500, 501, 502),
            ended.toSet(),
            "Shutdown musí zavolat onEnd pro všechny aktivní intervaly",
        )
        // plán po startu aplikace pokračuje — konzument ho nesmí označit za dokončený
        assertEquals(setOf(EndReason.Shutdown), endReasons.toSet())
    }

    @Test
    fun `shutdown počká na doběhnutí onEnd`() = runBlocking {
        val ended = AtomicBoolean(false)
        scheduler.schedule(1, now, null) {
            onStart {}
            onEnd {
                delay(SETTLE_TIME.milliseconds)
                ended.set(true)
            }
        }
        delay(SETTLE_TIME.milliseconds)

        // Spring po návratu z destroy ničí další beany — onEnd už musí mít za sebou
        scheduler.shutdown()
        assertTrue(ended.get(), "shutdown se vrátil dřív, než doběhl onEnd")
    }

    @Test
    fun `schedule po shutdown je no-op`() = runBlocking {
        // pozdní událost (push ze serveru) během zavírání Spring kontextu
        val started = AtomicBoolean(false)
        scheduler.shutdown()

        scheduler.schedule(1, now, null) { onStart { started.set(true) } }
        delay(SETTLE_TIME.milliseconds)

        assertFalse(started.get(), "po shutdown se onStart nesmí zavolat")
        assertFalse(scheduler.isActive(1))
    }

    @Test
    fun `schedule po shutdown nezanechá záznam v activeIds`() = runBlocking {
        scheduler.shutdown()
        scheduler.schedule(1, now, null) { onStart {} }
        delay(SETTLE_TIME.milliseconds)

        assertEquals(emptySet(), scheduler.activeIds())
    }

    @Test
    fun `přirozený konec intervalu odstraní záznam z activeIds`() = runBlocking {
        scheduler.schedule(1, now, now.plusNanos(500 * 1_000_000)) {
            onStart {}
            onEnd {}
        }

        delay(SETTLE_TIME.milliseconds)
        assertTrue(scheduler.isActive(1), "Během intervalu musí být active")

        // Počkej dost dlouho na natural end
        delay(500 + SETTLE_TIME)

        assertFalse(scheduler.isActive(1), "Po natural endu už nesmí být active")
        assertTrue(1 !in scheduler.activeIds(), "Po natural endu musí zmizet z activeIds")
    }

    companion object {
        /** Krátký delay pro rychlé akce a dispatch latency (~50 ms). */
        private const val SHORT_DELAY = 50L

        /** Střední delay pro běžící akce / čekání (~150 ms). */
        private const val MEDIUM_DELAY = 150L

        /** Pauza pro doběhnutí asynchronních operací (~250 ms). */
        private const val SETTLE_TIME = 250L

        /** Strop callbacků v testech zaseknutého onStart/onEnd (~500 ms). */
        private const val CALLBACK_TIMEOUT = 500L

        /** Strop, do kdy se musí scheduler zotavit ze zaseknutého callbacku (~2 s). */
        private const val HANG_LIMIT = 2_000L
    }
}
