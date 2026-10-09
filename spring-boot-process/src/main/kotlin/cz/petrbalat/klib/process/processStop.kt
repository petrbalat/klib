package cz.petrbalat.klib.process

import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.streams.toList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger("cz.petrbalat.klib.process.ProcessStop")

/**
 * SIGTERM → [gracefulStop] → SIGKILL, pak SIGKILL potomkům (renderery Chromia, soffice.bin).
 * Zabije jen vlastní strom — globální `pkill` by trefil cizí procesy. Blokující. [Duration.ZERO] = rovnou SIGKILL.
 */
fun Process.stopGracefully(gracefulStop: Duration = 5.seconds) {
    if (!isAlive) return

    // sesbírat PŘED destroy — po smrti rodiče je handle nevrátí
    val descendants: List<ProcessHandle> = descendants().toList()

    logger.info("Stopping process {} (SIGTERM, +{} descendants)", pid(), descendants.size)
    destroy()
    if (!waitFor(gracefulStop.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
        if (gracefulStop.isPositive()) logger.warn("Process {} ignored SIGTERM for {} → SIGKILL", pid(), gracefulStop)
        destroyForcibly()
        // po návratu proces opravdu neběží (SIGKILL doručí jádro asynchronně)
        waitFor(SIGKILL_WAIT_SECONDS, TimeUnit.SECONDS)
    }

    // potomky natvrdo — graceful šanci dostali se smrtí rodiče
    descendants.filter { it.isAlive }.forEach { it.destroyForcibly() }
}

private const val SIGKILL_WAIT_SECONDS = 2L
