package cz.petrbalat.klib.process.cycle

import cz.petrbalat.klib.process.RestartPolicy
import cz.petrbalat.klib.process.processGroups
import cz.petrbalat.klib.process.waitUntil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * Testy pro [SegmentCyclePlayer] — cyklus „proces na N sekund, pak další“.
 *
 * # Vrstvení testů
 *
 * Doplňuje `IntervalSchedulerTest`: ten testuje **kdy** interval startuje a končí,
 * tento soubor testuje **co se děje uvnitř** jednoho běžícího cyklu (střídání procesů segmentů).
 *
 * # Přístup
 *
 * Stejně jako `IntervalSchedulerTest` běží testy v reálném čase s krátkými
 * durationy — viz zdůvodnění tam (virtual time by skryl timing races).
 * Segmenty spouštějí skutečný neškodný proces (`sleep 60`), takže se ověřuje
 * i reálný lifecycle [cz.petrbalat.klib.process.ManagedProcess] (spawn → SIGTERM). Délku běhu vždy řídí
 * player, ne proces sám. Minimum délky segmentu (produkčně 5 s) je pro testy
 * sníženo přes konstruktor.
 */
class SegmentCyclePlayerTest {

    private lateinit var scope: CoroutineScope
    private lateinit var player: SegmentCyclePlayer

    /** každé přehrání — Unconfined: kolektor běží hned ve vlákně, které nowPlaying mění, nic nepřeskočí */
    private val played = CopyOnWriteArrayList<NowPlaying>()

    /** některé přehrání segmentu má živý proces */
    private val Segment.isAlive: Boolean get() = played.any { it.segment === this && it.process.isAlive }

    @BeforeEach
    fun setUp() {
        scope = CoroutineScope(SupervisorJob())
        player = SegmentCyclePlayer(scope, minSegment = MIN_SEGMENT.milliseconds)
        scope.launch(Dispatchers.Unconfined) { player.nowPlaying.filterNotNull().collect { played += it } }
    }

    @AfterEach
    fun tearDown() {
        // Zruší scope instance včetně případného běžícího cyklu (finally zastaví proces).
        scope.cancel()
    }

    // ============================================================
    //  ZÁKLADNÍ FUNKČNOST CYKLU
    // ============================================================

    @Test
    fun `spadlý proces segment ukončí dřív, nečeká se celá délka`() = runBlocking {
        val events = ConcurrentLinkedQueue<String>()
        val crashing = segment(BLOCKING_MS) {
            command("sh", "-c", "exit 1")
            prepare { events.add("crash") }
        }
        val next = segment(SEGMENT_MS) { prepare { events.add("next") } }

        player.start("test-crash") { listOf(crashing, next) }

        delay((SEGMENT_MS + SETTLE_TIME).milliseconds)
        player.stop()

        assertEquals(listOf("crash", "next"), events.toList().take(2), "po pádu procesu musí hned přijít další segment")
    }

    @Test
    fun `cyklus prochází segmenty v pořadí a opakuje se`() = runBlocking {
        val events = ConcurrentLinkedQueue<String>()
        val a = segment(SEGMENT_MS) { prepare { events.add("a") } }
        val b = segment(SEGMENT_MS) { prepare { events.add("b") } }

        player.start("test-cycle") { listOf(a, b) }

        delay((4 * SEGMENT_MS + SETTLE_TIME).milliseconds)
        player.stop()

        val list: List<String?> = events.toList()
        assertTrue(list.size >= 3, "Za dobu testu musí proběhnout aspoň 1,5 cyklu (events=$list)")
        list.forEachIndexed { i, event ->
            val expected = if (i % 2 == 0) "a" else "b"
            assertEquals(expected, event, "Segmenty se musí střídat a-b-a-b (events=$list)")
        }
    }

    @Test
    fun `provider segmentů se volá znovu na začátku každého cyklu`() = runBlocking {
        val calls = AtomicInteger(0)

        player.start("test-cycle") {
            calls.incrementAndGet()
            listOf(segment(SEGMENT_MS))
        }

        delay((3 * SEGMENT_MS + SETTLE_TIME).milliseconds)
        player.stop()

        // Directory akce spoléhá na re-read zdroje každý cyklus.
        assertTrue(calls.get() >= 2, "Provider se musí volat každý cyklus (calls=${calls.get()})")
    }

    @Test
    fun `vždy běží nejvýše jeden proces`() = runBlocking {
        val segments = List(3) { segment(SEGMENT_MS) }
        player.start("test-cycle") { segments }

        // Vzorkování po dobu ~celého cyklu — nikdy nesmí běžet dva procesy naráz.
        var maxAlive = 0
        val deadline = System.currentTimeMillis() + 3 * SEGMENT_MS
        while (System.currentTimeMillis() < deadline) {
            maxAlive = maxOf(maxAlive, segments.count { it.isAlive })
            delay(10.milliseconds)
        }
        player.stop()

        assertEquals(1, maxAlive, "Souběžně smí běžet právě jeden proces (max=$maxAlive)")
    }

    @Test
    fun `proces se zastaví po uplynutí délky segmentu a naváže další`() = runBlocking {
        val first = segment(2 * SEGMENT_MS)
        val second = segment(10_000)
        player.start("test-cycle") { listOf(first, second) }

        delay(SEGMENT_MS.milliseconds) // uprostřed prvního segmentu
        assertTrue(first.isAlive, "Během segmentu musí proces běžet")
        assertFalse(second.isAlive, "Druhý segment ještě nesmí běžet")

        delay((SEGMENT_MS + SETTLE_TIME).milliseconds) // po konci prvního segmentu
        assertFalse(first.isAlive, "Po uplynutí délky musí být proces zastaven")
        assertTrue(second.isAlive, "Musí navázat další segment")

        player.stop()
    }

    @Test
    fun `nulová délka segmentu se zvedne na minimum - žádná těsná smyčka`() = runBlocking {
        val starts = AtomicInteger(0)
        val seg = segment(0) { prepare { starts.incrementAndGet() } }

        player.start("test-cycle") { listOf(seg) }
        delay((4 * MIN_SEGMENT).milliseconds)
        player.stop()

        // Bez flooru by proběhly stovky spawn/kill iterací.
        assertTrue(starts.get() in 1..8, "Floor musí bránit těsné smyčce (starts=${starts.get()})")
    }

    // ============================================================
    //  UKONČENÍ (stop / zrušení scope instance)
    // ============================================================

    @Test
    fun `stop zastaví běžící proces i smyčku`() = runBlocking {
        val calls = AtomicInteger(0)
        val seg = segment(10_000)
        player.start("test-cycle") {
            calls.incrementAndGet()
            listOf(seg)
        }

        delay(SETTLE_TIME.milliseconds)
        assertTrue(seg.isAlive, "Proces musí během segmentu běžet")

        player.stop()
        // stop joinuje cyklus → jeho finally už proces zastavil.
        assertFalse(seg.isAlive, "Po návratu ze stop nesmí proces běžet")

        val callsAfterStop = calls.get()
        delay(SETTLE_TIME.milliseconds)
        assertEquals(callsAfterStop, calls.get(), "Po stop se smyčka nesmí točit dál")
    }

    @Test
    fun `druhý start běžícího cyklu selže, po stop projde`() = runBlocking {
        val seg = segment(10_000)
        player.start("test-cycle") { listOf(seg) }
        delay(SETTLE_TIME.milliseconds)

        // dva cykly by hrály souběžně a první by stop už nezastavil
        assertFailsWith<IllegalStateException> { player.start("second") { listOf(segment(10_000)) } }
        assertTrue(seg.isAlive)

        player.stop()
        val next = segment(10_000)
        player.start("test-cycle") { listOf(next) }
        delay(SETTLE_TIME.milliseconds)
        assertTrue(next.isAlive)
    }

    @Test
    fun `zrušení scope instance zruší cyklus a zastaví proces`() = runBlocking {
        val seg = segment(10_000)
        player.start("test-cycle") { listOf(seg) }

        delay(SETTLE_TIME.milliseconds)
        assertTrue(seg.isAlive, "Proces musí během segmentu běžet")

        scope.cancel() // supervisor ruší scope instance před jejím stopem
        delay(SETTLE_TIME.milliseconds)

        assertFalse(seg.isAlive, "Po zrušení scope nesmí proces běžet")
    }

    // ============================================================
    //  whilePlaying — souběžná akce segmentu
    // ============================================================

    @Test
    fun `whilePlaying běží po dobu segmentu a na konci se zruší`() = runBlocking {
        val sideStarted = AtomicBoolean(false)
        val sideCancelled = AtomicBoolean(false)

        val seg = segment(2 * SEGMENT_MS) {
            whilePlaying {
                sideStarted.set(true)
                try {
                    awaitCancellation()
                } finally {
                    sideCancelled.set(true)
                }
            }
        }
        player.start("test-cycle") { listOf(seg) }

        delay(SEGMENT_MS.milliseconds) // uprostřed segmentu
        assertTrue(sideStarted.get(), "whilePlaying musí během segmentu běžet")
        assertFalse(sideCancelled.get(), "whilePlaying nesmí být zrušena uprostřed segmentu")

        delay((2 * SEGMENT_MS + SETTLE_TIME).milliseconds) // po konci segmentu
        assertTrue(sideCancelled.get(), "whilePlaying se musí na konci segmentu zrušit")

        player.stop()
    }

    @Test
    fun `výjimka ve whilePlaying neshodí cyklus`() = runBlocking {
        val events = ConcurrentLinkedQueue<String>()
        val bad = segment(SEGMENT_MS) {
            prepare { events.add("bad") }
            whilePlaying { error("simulovaná chyba souběžné akce") }
        }
        val ok = segment(SEGMENT_MS) { prepare { events.add("ok") } }

        player.start("test-cycle") { listOf(bad, ok) }
        delay((2 * SEGMENT_MS + SETTLE_TIME).milliseconds)
        player.stop()

        assertTrue("ok" in events, "Cyklus musí pokračovat dalším segmentem (events=${events.toList()})")
    }

    // ============================================================
    //  CHYBOVÉ STAVY — cyklus musí přežít vadný vstup
    // ============================================================

    @Test
    fun `výjimka v prepare neshodí segment - proces se přesto spustí`() = runBlocking {
        val seg = segment(10_000) { prepare { error("simulovaná chyba přípravy") } }

        player.start("test-cycle") { listOf(seg) }
        delay(SETTLE_TIME.milliseconds)

        assertTrue(seg.isAlive, "Proces musí běžet i po chybě v prepare")
        player.stop()
    }

    @Test
    fun `neexistující příkaz neshodí cyklus - pokračuje se dalším segmentem`() = runBlocking {
        val events = ConcurrentLinkedQueue<String>()
        val broken = segment(SEGMENT_MS) {
            command("neexistujici-prikaz-xyz")
            prepare { events.add("broken") }
        }
        val ok = segment(SEGMENT_MS) { prepare { events.add("ok") } }

        player.start("test-cycle") { listOf(broken, ok) }
        delay((MIN_SEGMENT + 2 * SEGMENT_MS + SETTLE_TIME).milliseconds)
        player.stop()

        assertTrue("broken" in events, "Vadný segment se musel zkusit spustit")
        assertTrue("ok" in events, "Po vadném segmentu musí cyklus pokračovat (events=${events.toList()})")
    }

    @Test
    fun `prázdný seznam segmentů - další pokus po emptyRetry`() = runBlocking {
        val calls = AtomicInteger(0)
        val seg = segment(10_000)

        player.start("test-cycle", emptyRetry = EMPTY_RETRY.milliseconds) {
            if (calls.incrementAndGet() <= 2) emptyList() else listOf(seg)
        }

        delay((EMPTY_RETRY / 2).milliseconds) // během čekání po prvním prázdném výsledku
        assertEquals(1, calls.get(), "Během emptyRetry se provider nesmí volat znovu")
        assertFalse(seg.isAlive, "Během čekání nesmí nic běžet")

        delay((2 * EMPTY_RETRY + SETTLE_TIME).milliseconds)
        assertTrue(seg.isAlive, "Po neprázdném výsledku se musí začít přehrávat")

        player.stop()
    }

    @Test
    fun `výjimka v provideru neshodí cyklus - chová se jako prázdný výsledek`() = runBlocking {
        val calls = AtomicInteger(0)
        val seg = segment(10_000)

        player.start("test-cycle", emptyRetry = EMPTY_RETRY.milliseconds) {
            if (calls.incrementAndGet() == 1) error("simulovaná chyba čtení adresáře")
            listOf(seg)
        }

        delay((EMPTY_RETRY + SETTLE_TIME).milliseconds)

        assertTrue(calls.get() >= 2, "Po chybě provideru musí přijít další pokus (calls=${calls.get()})")
        assertTrue(seg.isAlive, "Po úspěšném pokusu se musí začít přehrávat")

        player.stop()
    }

    // ============================================================
    //  BLOKUJÍCÍ I/O — spadlý síťový mount nesmí zablokovat přepnutí akce
    // ============================================================

    @Test
    fun `blokující prepare nezdrží stop`() = runBlocking {
        val entered = CountDownLatch(1)
        // touch / úklid lock souborů na spadlém CIFS/NFS mountu visí minuty
        val seg = segment(10_000) { prepare { entered.countDown(); Thread.sleep(BLOCKING_MS) } }
        player.start("test-cycle") { listOf(seg) }
        assertTrue(entered.await(2, TimeUnit.SECONDS), "prepare se nezavolal")

        val took = measureTime { player.stop() }
        assertTrue(took < 1.seconds, "stop čekal na blokující prepare ($took)")

        // mount se vzpamatuje — zastavený cyklus už segment nespustí
        delay((BLOCKING_MS + SETTLE_TIME).milliseconds)
        assertFalse(seg.isAlive, "segment se spustil po stop")
    }

    @Test
    fun `blokující provider segmentů nezdrží stop`() = runBlocking {
        val entered = CountDownLatch(1)
        // listFiles() na spadlém síťovém mountu
        player.start("test-cycle") {
            entered.countDown()
            Thread.sleep(BLOCKING_MS)
            listOf(segment(10_000))
        }
        assertTrue(entered.await(2, TimeUnit.SECONDS), "provider se nezavolal")

        val took = measureTime { player.stop() }
        assertTrue(took < 1.seconds, "stop čekal na blokující provider ($took)")
    }

    // ============================================================
    //  DSL, nowPlaying a cyklus skupiny
    // ============================================================

    @Test
    fun `segment v DSL - proces bez restartu, prepare a whilePlaying`() {
        val segment: Segment = segment(30.seconds) {
            command("mpv", "--fs", "/media/a.mp4")
            env("DISPLAY", ":0")
            prepare {}
            whilePlaying {}
        }
        assertEquals(30.seconds, segment.duration)
        assertEquals(listOf("mpv", "--fs", "/media/a.mp4"), segment.spec.command)
        assertEquals(RestartPolicy.NEVER, segment.spec.restart)
        assertEquals(mapOf("DISPLAY" to ":0"), segment.spec.environment)
        assertTrue(segment.whilePlaying != null)
    }

    @Test
    fun `nowPlaying ukazuje právě hraný segment, po stop null`() = runBlocking {
        val seg: Segment = segment(10_000)
        player.start("test-cycle") { listOf(seg) }
        waitUntil { player.nowPlaying.value != null }

        val now = player.nowPlaying.value!!
        assertTrue(now.segment === seg)
        assertTrue(now.process.isAlive)

        player.stop()
        assertNull(player.nowPlaying.value)
        assertFalse(now.process.isAlive)
    }

    @Test
    fun `stop skupiny ukončí playCycle a další segment už nezačne`() = runBlocking {
        val group = processGroups("cycle-test").create("action-1", scope)
        val prepared = AtomicInteger()
        val tagFile = File.createTempFile("tag", ".txt").apply { deleteOnExit() }
        val cycle = scope.launch(Dispatchers.IO) {
            group.playCycle("cycle", minSegment = MIN_SEGMENT.milliseconds) {
                listOf(
                    segment(10.seconds) {
                        command("sh", "-c", "printf \"\$KLIB_PROCESS_TAG\" > '${tagFile.path}'; exec sleep 60")
                        prepare { prepared.incrementAndGet() }
                    },
                )
            }
        }
        waitUntil { tagFile.readText().isNotEmpty() }
        assertEquals("cycle-test/action-1", tagFile.readText())

        // dřív cyklus po stop skupiny (reaper zabil segment → „pád“) hned spustil další segment
        group.stop()
        withTimeout(5.seconds) { cycle.join() }
        delay(SETTLE_TIME.milliseconds)
        assertEquals(1, prepared.get())
    }

    @Test
    fun `stop skupiny z whilePlaying nečeká sám na sebe`() = runBlocking {
        val group = processGroups("cycle-test").create("action-2", scope)
        val stopped = CompletableDeferred<Unit>()
        val segmentPlayer = SegmentCyclePlayer(scope, MIN_SEGMENT.milliseconds, group)
        segmentPlayer.start("cycle") {
            listOf(
                segment(10.seconds) {
                    command("sleep", "60")
                    whilePlaying {
                        try {
                            group.stop()
                        } finally {
                            stopped.complete(Unit)
                        }
                    }
                },
            )
        }

        withTimeout(5.seconds) { stopped.await() }
        assertTrue(group.isStopped)
        waitUntil { segmentPlayer.nowPlaying.value == null }
    }

    // ============================================================
    //  Pomocné
    // ============================================================

    /** Segment s neškodným dlouhým procesem (`sleep 60`) — délku běhu řídí player; [block] ho může upravit. */
    private fun segment(durationMs: Long, block: SegmentBuilder.() -> Unit = {}): Segment = segment(durationMs.milliseconds) {
        command("sleep", "60")
        block()
    }

    companion object {
        /** Testovací minimum délky segmentu (produkční default je 5 s). */
        private const val MIN_SEGMENT = 50L

        /** Délka typického testovacího segmentu (~150 ms). */
        private const val SEGMENT_MS = 150L

        /** Interval dalšího pokusu při prázdném/vadném zdroji segmentů. */
        private const val EMPTY_RETRY = 300L

        /** Jak dlouho visí blokující I/O (prepare, provider) v testech spadlého mountu. */
        private const val BLOCKING_MS = 3_000L

        /** Pauza pro doběhnutí asynchronních operací (~250 ms). */
        private const val SETTLE_TIME = 250L
    }
}
