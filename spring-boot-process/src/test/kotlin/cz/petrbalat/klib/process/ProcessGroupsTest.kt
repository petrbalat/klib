package cz.petrbalat.klib.process

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Registr skupin bez Springu — eventy a vypnutí kontextu přes Spring testuje `ProcessAutoConfigurationTest`. */
@RequiresPidNamespace
class ProcessGroupsTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gaveUp = CopyOnWriteArrayList<String>()
    private val restarting = CopyOnWriteArrayList<String>()

    private val groups = processGroups {
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
    fun `stop po zrušení scope čeká na ukončení procesu`() {
        val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val group = groups.create("action-7", actionScope)
        val process = group.start { command("sleep", "1000") }
        val pid = assertIs<ProcessState.Running>(process.state.value).pid

        actionScope.cancel()
        waitUntil { group.isStopped } // zastavuje se samo
        runBlocking { group.stop() }

        assertFalse(isPidAlive(pid))
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
