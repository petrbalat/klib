package cz.petrbalat.klib.process

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RequiresDockerTestImage
class UnshareProcessTest {
    private val prototype = UnshareProcessPrototype()
    private val started = mutableListOf<UnshareProcessHandle>()
    private val ownerProcesses = mutableListOf<Process>()
    private val orphanedWrapperPids = mutableSetOf<Long>()

    @TempDir
    lateinit var dir: File

    @AfterEach
    fun tearDown() {
        started.asReversed().forEach { runCatching { it.killWrapper() } }
        ownerProcesses.forEach { owner ->
            if (owner.isAlive) {
                owner.toHandle().descendants().forEach { it.destroyForcibly() }
                owner.destroyForcibly()
                runCatching { owner.waitFor(2, TimeUnit.SECONDS) }
            }
        }
        orphanedWrapperPids.forEach { pid -> ProcessHandle.of(pid).ifPresent { if (it.isAlive) it.destroyForcibly() } }
    }

    @Test
    fun `user pid namespace zachová primární identitu ale zahodí nemapované skupiny zařízení`() {
        assertEquals("pi", System.getProperty("user.name"))
        val output = File(dir, "context.txt")
        val script = """
            printf 'pid=%s\n' "${'$'}${'$'}"
            printf 'uid=%s\n' "${'$'}(id -u)"
            printf 'gid=%s\n' "${'$'}(id -g)"
            printf 'groups=%s\n' "${'$'}(id -Gn)"
            printf 'deviceGroupRead=%s\n' "${'$'}(test -r /tmp/klib-video-group-only && printf yes || printf no)"
            printf 'cwd=%s\n' "${'$'}PWD"
            printf 'env=%s\n' "${'$'}KLIB_TEST_VALUE"
            printf 'arg=%s\n' "${'$'}1"
            exec sleep 1000
        """.trimIndent()
        val spec = ProcessSpec(
            name = "unshare-context",
            command = listOf("sh", "-c", script, "sh", "argument with spaces"),
            environment = cdcEnvironment() + ("KLIB_TEST_VALUE" to "explicit-value"),
            workingDirectory = dir,
            output = ProcessOutput.ToFile(output),
            restart = RestartPolicy.NEVER,
        )
        val process = start(spec)
        val innerInit = process.awaitInnerInit()
        waitUntil { output.isFile && output.readText().contains("env=explicit-value") }

        val namespacePids = File("/proc/${innerInit.pid()}/status").readLines()
            .first { it.startsWith("NSpid:") }
            .substringAfter(':')
            .trim()
            .split(Regex("\\s+"))
        assertEquals("1", namespacePids.last(), "vnitřní tini musí být PID 1 nového PID namespace")

        val result = output.readLines().associate { it.substringBefore('=') to it.substringAfter('=', "") }
        assertTrue(result["pid"]?.toIntOrNull()?.let { it > 1 } == true, "příkaz běží jako potomek namespace initu")
        assertEquals("1000", result["uid"])
        assertEquals("1000", result["gid"])
        assertEquals(dir.absolutePath, result["cwd"])
        assertEquals("explicit-value", result["env"])
        assertEquals("argument with spaces", result["arg"])
        assertEquals("yes", result["deviceGroupRead"], "doplňková skupina musí zachovat přístup podle skutečných oprávnění")
        val groups = result["groups"].orEmpty().split(' ').toSet()
        assertTrue("pi" in groups, "primární skupina pi musí být zachována: $groups")
        val deviceGroups = setOf("audio", "video", "render", "dialout")
        assertEquals(
            setOf("pi", "nogroup"), groups,
            "map-current-user mapuje jen primární GID; skupiny zařízení $deviceGroups bez newgidmap zaniknou",
        )
        process.stop()
    }

    @Test
    fun `SIGTERM jde přímo do vnitřního tini a nulová lhůta zabije namespace bez SIGTERM`() {
        val positiveTerm = File(dir, "positive.term")
        val positiveReady = File(dir, "positive.ready")
        val positive = start(signalHandlerSpec("unshare-positive-stop", positiveTerm, positiveReady, 2.seconds))
        waitUntil { positiveReady.exists() }
        positive.stop()
        assertTrue(positiveTerm.exists(), "přímý SIGTERM vnitřnímu tini musí spustit handler potomka")

        val zeroTerm = File(dir, "zero.term")
        val zeroReady = File(dir, "zero.ready")
        val zero = start(signalHandlerSpec("unshare-zero-stop", zeroTerm, zeroReady, Duration.ZERO))
        waitUntil { zeroReady.exists() }
        zero.stop()
        assertFalse(zeroTerm.exists(), "Duration.ZERO nesmí doručit SIGTERM")
    }

    @Test
    fun `wrapper unshare SIGTERM nepředá ale jeho SIGKILL uklidí namespace`() {
        val ready = File(dir, "wrapper-term.ready")
        val process = start(ProcessSpec(
            name = "wrapper-term",
            command = listOf("sh", "-c", "printf ready > \"${'$'}1\"; exec sleep 1000", "sh", ready.absolutePath),
            restart = RestartPolicy.NEVER,
        ))
        waitUntil { ready.exists() }
        val child = process.awaitDescendant("sleep", "1000")

        process.wrapper.destroy()
        Thread.sleep(150)
        assertTrue(process.isAlive, "util-linux unshare čekající na dítě ignoruje SIGTERM")
        assertTrue(child.isAlive, "SIGTERM wrapperu se nesmí předat vnitřnímu procesu")

        process.killWrapper()
        waitUntil { !child.isAlive }
    }

    @Test
    fun `konec launcheru uklidí setsid potomka při obou návratových kódech`() {
        listOf(0, 7).forEach { exitCode ->
            val ready = File(dir, "launcher-$exitCode.ready")
            val script = """
                setsid sh -c 'exec sleep 1000' >/dev/null 2>&1 </dev/null &
                printf ready > "${'$'}2"
                sleep 0.2
                exit "${'$'}1"
            """.trimIndent()
            val process = start(ProcessSpec(
                name = "unshare-launcher-$exitCode",
                command = listOf("sh", "-c", script, "sh", exitCode.toString(), ready.absolutePath),
                workingDirectory = dir,
                restart = RestartPolicy.NEVER,
            ))
            waitUntil { ready.exists() }
            val detached = process.awaitDescendant("sleep", "1000")
            process.awaitExit()
            assertEquals(exitCode, process.wrapper.exitValue())
            waitUntil { !detached.isAlive }
        }
    }

    @Test
    fun `ukončení namespace neovlivní sousední namespace`() {
        val first = start(sleeperSpec("unshare-isolated-first"))
        val second = start(sleeperSpec("unshare-isolated-second"))
        val firstSleep = first.awaitDescendant("sleep", "1000")
        val secondSleep = second.awaitDescendant("sleep", "1000")

        first.stop()
        waitUntil { !firstSleep.isAlive }
        assertTrue(secondSleep.isAlive, "zastavení jednoho PID namespace nesmí ukončit sousední")
        second.stop()
    }

    @Test
    fun `vnitřní tini reapuje osiřelé zombie`() {
        val script = "for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do (sleep 0.05) & done; exec sleep 1000"
        val process = start(ProcessSpec(
            name = "unshare-reaping",
            command = listOf("sh", "-c", script),
            restart = RestartPolicy.NEVER,
        ))
        val initPid = process.awaitInnerInit().pid()
        Thread.sleep(300)
        assertTrue(zombieChildren(initPid).isEmpty(), "tini PID 1 musí sklízet zombie z osiřelých potomků")
        process.stop()
    }

    @Test
    fun `pád JVM vlastníka sám nezastaví unshare ale zabití wrapperu uklidí namespace`() {
        val details = File(dir, "owner-processes.txt")
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        val owner = ProcessBuilder(
            java, "-cp", System.getProperty("java.class.path"), UnshareJvmOwner::class.java.name, details.absolutePath,
        ).start()
        ownerProcesses += owner
        waitUntil(10.seconds) { details.isFile && details.readLines().size == 2 }
        val (wrapperPid, childPid) = details.readLines().map(String::toLong)
        orphanedWrapperPids += wrapperPid

        owner.destroyForcibly()
        assertTrue(owner.waitFor(5, TimeUnit.SECONDS), "vlastnická JVM musí být zabita")
        waitUntil { isPidAlive(wrapperPid) && isPidAlive(childPid) }

        ProcessHandle.of(wrapperPid).orElseThrow().destroyForcibly()
        waitUntil { !isPidAlive(wrapperPid) && !isPidAlive(childPid) }
    }

    private fun start(spec: ProcessSpec): UnshareProcessHandle = prototype.start(spec).also { started += it }

    private fun signalHandlerSpec(name: String, term: File, ready: File, gracefulStop: Duration) = ProcessSpec(
        name = name,
        command = listOf(
            "sh", "-c",
            "trap 'printf term > \"${'$'}1\"; exit 0' TERM; printf ready > \"${'$'}2\"; while :; do sleep 0.05; done",
            "sh", term.absolutePath, ready.absolutePath,
        ),
        restart = RestartPolicy.NEVER,
        gracefulStop = gracefulStop,
    )

    private fun sleeperSpec(name: String) = ProcessSpec(
        name = name,
        command = listOf("sh", "-c", "exec sleep 1000"),
        restart = RestartPolicy.NEVER,
    )

    private fun zombieChildren(parentPid: Long): List<String> = File("/proc").listFiles().orEmpty()
        .filter { it.name.toLongOrNull() != null }
        .mapNotNull { process ->
            val status = try {
                File(process, "status").readLines()
            } catch (_: IOException) {
                return@mapNotNull null
            }
            val state = status.firstOrNull { it.startsWith("State:") }.orEmpty()
            val ppid = status.firstOrNull { it.startsWith("PPid:") }?.substringAfter(':')?.trim()?.toLongOrNull()
            process.name.takeIf { state.contains("zombie", ignoreCase = true) && ppid == parentPid }
        }

    companion object {
        private fun cdcEnvironment() = mapOf(
            "DISPLAY" to ":0",
            "WAYLAND_DISPLAY" to "wayland-0",
            "XDG_RUNTIME_DIR" to "/run/user/1000",
            "DBUS_SESSION_BUS_ADDRESS" to "unix:path=/run/user/1000/bus",
            "DBUS_SYSTEM_BUS_ADDRESS" to "unix:path=/run/dbus/system_bus_socket",
            "QT_QPA_PLATFORM" to "wayland",
            "SAL_USE_VCLPLUGIN" to "gtk3",
            "SYS_NET_ADDRESS" to "unshare-test-net",
            "SYS_MAC_ADDRESS" to "02:00:00:00:00:01",
            "SYS_IMAGE_VERSION" to "unshare-test",
        )
    }
}
