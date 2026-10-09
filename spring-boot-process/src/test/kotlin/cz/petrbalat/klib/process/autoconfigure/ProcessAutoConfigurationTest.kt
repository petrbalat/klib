package cz.petrbalat.klib.process.autoconfigure

import cz.petrbalat.klib.process.ProcessGroups
import cz.petrbalat.klib.process.ProcessReaper
import cz.petrbalat.klib.process.ProcessState
import cz.petrbalat.klib.process.ProcessTagging
import cz.petrbalat.klib.process.RestartPolicy
import cz.petrbalat.klib.process.await
import cz.petrbalat.klib.process.isPidAlive
import cz.petrbalat.klib.process.start
import cz.petrbalat.klib.process.waitUntil
import cz.petrbalat.klib.process.withGroup
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.event.EventListener
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class ProcessAutoConfigurationTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ProcessAutoConfiguration::class.java))
        .withPropertyValues("klib.process.owner=it-test")

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `binding klib-process`() {
        runner.withPropertyValues(
            "klib.process.env-name=APP_TAG",
            "klib.process.logs-directory=/tmp/klib-logs",
            "klib.process.restart.max-restarts=3",
            "klib.process.restart.delay=100ms",
            "klib.process.restart.stable-run=1m",
            "klib.process.graceful-stop=1s",
            "klib.process.reap-orphans-on-startup=false",
        ).run { context ->
            val tagging = context.getBean(ProcessTagging::class.java)
            assertEquals("it-test", tagging.owner)
            assertEquals("APP_TAG", tagging.envName)

            val defaults = context.getBean(ProcessProperties::class.java).toDefaults()
            assertEquals(RestartPolicy(maxRestarts = 3, delay = 100.milliseconds, stableRun = 1.minutes), defaults.restart)
            assertEquals(File("/tmp/klib-logs"), defaults.logsDirectory)

            val group = context.getBean(ProcessGroups::class.java).create("g", scope)
            assertEquals(mapOf("APP_TAG" to "it-test/g"), group.environment)
            assertEquals(File("/tmp/klib-logs/x.log"), group.logFile("x"))
        }
    }

    @Test
    fun `bez owner se autokonfigurace nezapne a kontext nastartuje`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ProcessAutoConfiguration::class.java))
            .run { context ->
                assertNull(context.startupFailure)
                assertFalse(context.containsBean("processGroups"))
                assertTrue(context.getBeansOfType(ProcessReaper::class.java).isEmpty())
            }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    fun `sirotek vlastníka z minulého běhu po startu kontextu nežije`() {
        val orphan = ProcessBuilder("sleep", "1000").apply { environment()["KLIB_PROCESS_TAG"] = "it-test/old-1" }.start()
        val foreign = ProcessBuilder("sleep", "1000").apply { environment()["KLIB_PROCESS_TAG"] = "it-other/old-1" }.start()
        try {
            runner.run { context ->
                assertNull(context.startupFailure)
                waitUntil { !orphan.isAlive }
                assertTrue(foreign.isAlive)
            }
        } finally {
            orphan.destroyForcibly()
            foreign.destroyForcibly()
        }
    }

    @Test
    fun `zavření kontextu zastaví skupinu, kterou aplikace nezastavila`() {
        lateinit var pid: Number
        runner.run { context ->
            val process = context.getBean(ProcessGroups::class.java).create("forgotten", scope)
                .start { command("sleep", "1000") }
            pid = assertIs<ProcessState.Running>(process.state.value).pid
            assertTrue(isPidAlive(pid.toLong()))
        }
        assertFalse(isPidAlive(pid.toLong()), "proces po zavření kontextu neběží")
    }

    @Test
    fun `PreDestroy aplikace zastaví svou skupinu dřív než pojistka ProcessGroups`() {
        val log = CopyOnWriteArrayList<String>()
        runner.withBean(Recorder::class.java, { Recorder(scope, log) })
            .withBean(GroupOwner::class.java)
            .run { context -> context.getBean(GroupOwner::class.java).start() }
        assertEquals(listOf("alive-at-predestroy=true", "stopped"), log)
    }

    @Test
    fun `ProcessGaveUpEvent dorazí právě jednou, po stop se nepublikuje`() {
        runner.withBean(GaveUpListener::class.java).run { context ->
            val groups = context.getBean(ProcessGroups::class.java)
            val listener = context.getBean(GaveUpListener::class.java)

            val crashing = groups.create("crash-1", scope)
            crashing.start("player") {
                command("sh", "-c", "exit 1")
                restart { maxRestarts = 1; delay = 10.milliseconds }
            }
            waitUntil { listener.events.isNotEmpty() }
            Thread.sleep(200)
            val event = listener.events.single()
            assertEquals("it-test", event.owner)
            assertEquals("crash-1", event.groupId)
            assertEquals("player", event.giveUp.name)
            assertEquals(1, event.giveUp.restarts)

            runBlocking {
                groups.withGroup("stopped-1") { start { command("sleep", "10"); noRestart() } }
            }
            Thread.sleep(200)
            assertEquals(1, listener.events.size)
        }
    }

    @Test
    fun `registr skupin - find a groups, po stop skupina zmizí`() {
        runner.run { context ->
            val groups = context.getBean(ProcessGroups::class.java)
            val group = groups.create("reg-1", scope)
            assertEquals(group, groups.find("reg-1"))
            assertEquals(setOf("reg-1"), groups.groups.value.keys)

            runBlocking { group.stop() }
            assertNull(groups.find("reg-1"))
            groups.groups.await { it.isEmpty() }
            // id je po stop znovu volné
            assertNotNull(groups.create("reg-1", scope))
        }
    }

    @Test
    fun `zrušený scope bez stop skupinu zastaví a odebere z registru`() {
        runner.run { context ->
            val groups = context.getBean(ProcessGroups::class.java)
            val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val group = groups.create("action-42", actionScope)
            val process = group.start { command("sleep", "1000") }
            val pid = assertIs<ProcessState.Running>(process.state.value).pid

            actionScope.cancel()
            groups.groups.await { it.isEmpty() }
            assertTrue(group.isStopped)
            assertFalse(isPidAlive(pid))
            // id je znovu volné
            assertNotNull(groups.create("action-42", scope))
        }
    }

    @Test
    fun `metriky jen s MeterRegistry v kontextu`() {
        runner.run { context -> assertFalse(context.containsBean("processMetrics")) }

        runner.withBean(MeterRegistry::class.java, { SimpleMeterRegistry() }).run { context ->
            assertNotNull(context.getBean(MicrometerProcessMetrics::class.java))
            val group = context.getBean(ProcessGroups::class.java).create("m-1", scope)
            group.start {
                command("sh", "-c", "exit 1")
                restart { maxRestarts = 2; delay = 10.milliseconds }
            }

            val registry = context.getBean(MeterRegistry::class.java)
            waitUntil { registry.counter("klib.process.gave_up", "owner", "it-test").count() == 1.0 }
            assertEquals(2.0, registry.counter("klib.process.restarts", "owner", "it-test").count())
        }
    }

    @Test
    fun `listener ProcessGaveUpEvent smí skupinu synchronně zastavit`() {
        runner.withBean(StoppingGaveUpListener::class.java).run { context ->
            val groups = context.getBean(ProcessGroups::class.java)
            val listener = context.getBean(StoppingGaveUpListener::class.java)

            // aplikace po vyčerpání restartů ukončí akci a ukáže chybovou obrazovku
            val group = groups.create("gave-up-stop", scope)
            group.start { command("sh", "-c", "exit 1"); noRestart() }

            listener.entered.get(5, TimeUnit.SECONDS)
            val stopped = runCatching { listener.stopped.get(5, TimeUnit.SECONDS) }
            assertTrue(stopped.isSuccess, "stop skupiny v listeneru se zasekl (čeká na watchdog, ze kterého je volaný)")
            assertTrue(group.isStopped)
            assertNull(groups.find("gave-up-stop"))
        }
    }

    class StoppingGaveUpListener(private val groups: ProcessGroups) {
        val entered = CompletableFuture<Unit>()
        val stopped = CompletableFuture<Unit>()

        @EventListener
        fun onGaveUp(event: ProcessGaveUpEvent) {
            entered.complete(Unit)
            runBlocking { groups.find(event.groupId)?.stop() }
            stopped.complete(Unit)
        }
    }

    class GaveUpListener {
        val events = CopyOnWriteArrayList<ProcessGaveUpEvent>()

        @EventListener
        fun onGaveUp(event: ProcessGaveUpEvent) {
            events += event
        }
    }

    /** Bean aplikace závislý na ProcessGroups — při vypnutí zastaví svou skupinu sám. */
    class Recorder(val scope: CoroutineScope, val log: MutableList<String>)

    class GroupOwner(private val groups: ProcessGroups, private val recorder: Recorder) {
        private lateinit var pid: Number

        fun start() {
            val process = groups.create("owned", recorder.scope).start { command("sleep", "1000") }
            pid = assertIs<ProcessState.Running>(process.state.value).pid
        }

        @PreDestroy
        fun shutdown() {
            recorder.log += "alive-at-predestroy=${isPidAlive(pid.toLong())}"
            runBlocking { groups.find("owned")?.stop() }
            recorder.log += "stopped"
        }
    }
}
