package cz.petrbalat.klib.process

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Čeká na stav místo pollingu; po [timeout] test spadne. */
fun <T> StateFlow<T>.await(timeout: Duration = 5.seconds, predicate: (T) -> Boolean): T =
    runBlocking { withTimeout(timeout) { first(predicate) } }

/** Polling pro podmínky bez StateFlow (soubory, /proc). */
fun waitUntil(timeout: Duration = 5.seconds, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeout.inWholeMilliseconds
    while (!condition()) {
        check(System.currentTimeMillis() < deadline) { "condition not met within $timeout" }
        Thread.sleep(20)
    }
}

fun File.lineCount(): Int = if (exists()) readLines().size else 0

/** Skript z `src/test/resources/fixtures` — spouští se přes `sh`, práva po kopírování resources nehrají roli. */
fun fixture(name: String): String =
    File(checkNotNull(object {}.javaClass.getResource("/fixtures/$name")) { "fixture $name" }.toURI()).path

fun isPidAlive(pid: Long): Boolean = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
