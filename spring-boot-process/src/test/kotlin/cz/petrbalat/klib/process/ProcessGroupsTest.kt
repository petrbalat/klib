package cz.petrbalat.klib.process

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Registr skupin bez Springu — eventy a vypnutí kontextu přes Spring testuje `ProcessAutoConfigurationTest`. */
class ProcessGroupsTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gaveUp = CopyOnWriteArrayList<String>()
    private val restarting = CopyOnWriteArrayList<String>()

    private val groups = processGroups("test") {
        shutdownTimeout = 5.seconds
        onRestarting { group, name, _ -> restarting += "${group.id}/$name" }
        onGaveUp { group, giveUp -> gaveUp += "${group.id}/${giveUp.name}" }
    }

    @AfterEach
    fun tearDown() {
        groups.close()
        scope.cancel()
    }

    @Test
    fun `listener dostane restart i vzdání se procesu skupiny`() {
        val group = groups.create("g-1", scope)
        group.start("player") {
            command("sh", "-c", "exit 1")
            restart { maxRestarts = 1; delay = 10.milliseconds }
        }

        waitUntil { gaveUp.isNotEmpty() }
        assertEquals(listOf("g-1/player"), restarting)
        assertEquals(listOf("g-1/player"), gaveUp)
    }

    @Test
    fun `close zastaví skupiny, které aplikace nezastavila`() {
        val group = groups.create("forgotten", scope)
        val process = group.start { command("sleep", "1000") }

        groups.close()
        assertTrue(group.isStopped)
        assertFalse(process.isAlive)
        assertNull(groups.find("forgotten"))
    }

    @Test
    fun `stop po zrušení scope se vrátí až po úklidu, ne hned`(@TempDir proc: File) {
        // konzument zruší scope akce (vedlejší joby) a pak skupinu zastaví — zrušení scope ji mezitím začne
        // zastavovat samo (stopOnScopeCompletion); explicitní stop se nesmí vrátit dřív, než to doběhne.
        // Falešný /proc se sirotkem skupiny a pomalý killer drží automatický stop v úklidu sirotků.
        File(proc, "4242").mkdirs()
        File(proc, "4242/environ").writeText("KLIB_PROCESS_TAG=test/action-7\u0000")
        val killed = CopyOnWriteArrayList<Long>()
        val tagging = ProcessTagging("test")
        val slowReaper = ProcessReaper(tagging, proc) { pid ->
            Thread.sleep(500)
            killed += pid
        }
        val groups = ProcessGroups(tagging, slowReaper)
        val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val group = groups.create("action-7", actionScope)

        actionScope.cancel()
        waitUntil { group.isStopped } // zastavuje se samo
        runBlocking { group.stop() }

        assertEquals(listOf(4242L), killed, "stop se vrátil před úklidem sirotků")
        assertNull(groups.find("action-7"), "skupina musí být po stop() pryč z registru")
    }

    @Test
    fun `id skupiny, která se ještě zastavuje, hlásí srozumitelnou chybu`() {
        val group = groups.create("action-42", scope)
        // ignoruje SIGTERM → stop trvá celý gracefulStop
        group.start {
            command("sh", fixture("ignore-term.sh"))
            gracefulStop = 2.seconds
        }
        val stopping = scope.launch { group.stop() }
        waitUntil { group.isStopped }

        val error = assertFailsWith<IllegalArgumentException> { groups.create("action-42", scope) }
        assertEquals("Process group action-42 is still stopping", error.message)

        runBlocking { stopping.join() }
        groups.create("action-42", scope)
    }
}
