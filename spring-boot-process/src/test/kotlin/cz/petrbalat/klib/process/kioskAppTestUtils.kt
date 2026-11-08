package cz.petrbalat.klib.process

import kotlinx.coroutines.CoroutineScope
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Živé procesy namespace podle názvu binárky (`chromium`, `soffice.bin`…) — bez wrapperu `unshare` a `tini`. */
fun ManagedProcess.liveDescendants(binary: String): List<ProcessHandle> =
    process?.toHandle()?.descendants()?.toList().orEmpty().filter {
        it.isAlive && it.info().command().orElse("").substringAfterLast('/') == binary
    }

/** Hlavní proces aplikace = přímý potomek vnitřního tini (PID 2 v namespace). */
fun ManagedProcess.namespaceMain(binary: String, timeout: Duration = 10.seconds): ProcessHandle {
    var main: ProcessHandle? = null
    waitUntil(timeout) {
        main = liveDescendants(binary).firstOrNull { handle ->
            handle.parent().map { it.info().command().orElse("").endsWith("/tini") }.orElse(false)
        }
        main != null
    }
    return checkNotNull(main) { "no $binary directly under namespace tini" }
}

fun startManaged(
    scope: CoroutineScope,
    name: String,
    command: List<String>,
    gracefulStop: Duration = 10.seconds,
    restart: RestartPolicy = RestartPolicy.NEVER,
    environment: Map<String, String> = emptyMap(),
    log: File? = null,
): ManagedProcess = ManagedProcess(
    ProcessSpec(
        command = command,
        name = name,
        environment = environment,
        output = log?.let { ProcessOutput.ToFile(it) } ?: ProcessOutput.Discard,
        restart = restart,
        gracefulStop = gracefulStop,
    ),
    scope,
).also { it.start() }
