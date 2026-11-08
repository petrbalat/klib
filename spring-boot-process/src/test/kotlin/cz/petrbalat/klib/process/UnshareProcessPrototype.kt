package cz.petrbalat.klib.process

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Testovací adapter, který spustí jeden [ProcessSpec] v odděleném user/PID namespace. */
internal class UnshareProcessPrototype {
    fun start(spec: ProcessSpec): UnshareProcessHandle {
        require(spec.restart == RestartPolicy.NEVER) { "Prototype starts one command; restart policy stays in Kotlin" }

        val directory = spec.workingDirectory?.absoluteFile ?: File(System.getProperty("user.dir"))
        val command = listOf(
            "unshare", "--user", "--map-current-user", "--pid", "--fork", "--kill-child",
            "/usr/bin/tini", "-g", "--",
        ) + spec.command
        val builder = ProcessBuilder(command).directory(directory)
        builder.environment().putAll(spec.environment)
        when (val output = spec.output) {
            ProcessOutput.Discard -> {
                builder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
                builder.redirectError(ProcessBuilder.Redirect.DISCARD)
            }
            is ProcessOutput.ToFile -> {
                rotateLog(output.file, output.maxBytes)
                output.file.absoluteFile.parentFile?.mkdirs()
                builder.redirectOutput(ProcessBuilder.Redirect.appendTo(output.file.absoluteFile))
                builder.redirectError(ProcessBuilder.Redirect.appendTo(output.file.absoluteFile))
            }
        }
        return UnshareProcessHandle(builder.start(), spec.gracefulStop)
    }
}

internal class UnshareProcessHandle(
    internal val wrapper: Process,
    private val gracefulStop: Duration,
) {
    val isAlive: Boolean get() = wrapper.isAlive

    fun awaitInnerInit(timeout: Duration = 5.seconds): ProcessHandle {
        var result: ProcessHandle? = null
        waitUntil(timeout) {
            result = wrapper.toHandle().children().toList().firstOrNull { it.isAlive && it.commandName() == "tini" }
            result != null
        }
        return checkNotNull(result) { "unshare did not start its inner tini" }
    }

    fun awaitDescendant(command: String, argument: String? = null, timeout: Duration = 5.seconds): ProcessHandle {
        var result: ProcessHandle? = null
        waitUntil(timeout) {
            result = wrapper.toHandle().descendants().toList().firstOrNull {
                it.isAlive && it.commandName() == command && (argument == null || argument in it.arguments())
            }
            result != null
        }
        return checkNotNull(result) { "unshare process has no live $command descendant" }
    }

    fun descendants(): List<ProcessHandle> = wrapper.toHandle().descendants().toList()

    fun stop(): Int {
        if (wrapper.isAlive) {
            if (gracefulStop == Duration.ZERO) {
                wrapper.destroyForcibly()
            } else {
                awaitInnerInit().destroy()
                if (!wrapper.waitFor(gracefulStop.inWholeMilliseconds.coerceAtLeast(1), TimeUnit.MILLISECONDS)) {
                    wrapper.destroyForcibly()
                }
            }
        }
        awaitExit()
        return wrapper.exitValue()
    }

    fun killWrapper() {
        if (wrapper.isAlive) wrapper.destroyForcibly()
        awaitExit()
    }

    fun awaitExit(timeout: Duration = 5.seconds) {
        check(wrapper.waitFor(timeout.inWholeMilliseconds.coerceAtLeast(1), TimeUnit.MILLISECONDS)) {
            "unshare wrapper ${wrapper.pid()} did not exit within $timeout"
        }
    }

    private fun ProcessHandle.commandName(): String = info().command().orElse("").substringAfterLast('/')

    private fun ProcessHandle.arguments(): String = info().arguments().orElse(emptyArray<String>()).joinToString(" ")
}

/** Pomocná JVM pro ověření, že `--kill-child` váže potomka na `unshare`, nikoli na spouštějící JVM. */
object UnshareJvmOwner {
    @JvmStatic
    fun main(args: Array<String>) {
        val wrapper = ProcessBuilder(
            "unshare", "--user", "--map-current-user", "--pid", "--fork", "--kill-child",
            "/usr/bin/tini", "-g", "--", "sh", "-c", "exec sleep 1000",
        ).start()
        var init: ProcessHandle? = null
        var child: ProcessHandle? = null
        waitUntil(10.seconds) {
            init = wrapper.toHandle().children().toList().firstOrNull { it.isAlive && it.info().command().orElse("").endsWith("/tini") }
            child = wrapper.toHandle().descendants().toList().firstOrNull {
                it.isAlive && it.info().command().orElse("").endsWith("/sleep")
            }
            init != null && child != null
        }
        File(args.single()).writeText("${wrapper.pid()}\n${checkNotNull(child).pid()}\n")
        while (true) Thread.sleep(1_000)
    }
}
