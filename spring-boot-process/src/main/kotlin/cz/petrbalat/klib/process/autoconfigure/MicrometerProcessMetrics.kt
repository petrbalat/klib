package cz.petrbalat.klib.process.autoconfigure

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.event.EventListener

/** Počítadla restartů a vzdání se z událostí skupin. Na Micrometer sahá jen tato třída — bez něj se nenačte. */
class MicrometerProcessMetrics(registry: MeterRegistry, owner: String) {
    private val restarts: Counter = Counter.builder("klib.process.restarts")
        .description("Restarts of managed processes after a crash")
        .tag("owner", owner)
        .register(registry)
    private val gaveUp: Counter = Counter.builder("klib.process.gave_up")
        .description("Managed processes whose watchdog ran out of restarts")
        .tag("owner", owner)
        .register(registry)

    @EventListener
    fun onRestarted(event: ProcessRestartedEvent) = restarts.increment()

    @EventListener
    fun onGaveUp(event: ProcessGaveUpEvent) = gaveUp.increment()
}
