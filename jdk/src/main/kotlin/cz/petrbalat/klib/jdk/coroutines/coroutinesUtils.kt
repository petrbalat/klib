package cz.petrbalat.klib.jdk.coroutines

import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.delay

/**
 * zkusí n krát
 */
@ExperimentalTime
suspend fun tryAgain(pause: Duration, maxAttempt: Int = 2, attempt: () -> Boolean): Boolean = (1..maxAttempt).any {
    val result = attempt()
    if (!result) {
        delay(pause)
    }
    result
}
