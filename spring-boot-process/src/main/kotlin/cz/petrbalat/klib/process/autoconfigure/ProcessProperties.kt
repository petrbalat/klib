package cz.petrbalat.klib.process.autoconfigure

import cz.petrbalat.klib.process.ProcessDefaults
import cz.petrbalat.klib.process.RestartPolicy
import org.springframework.boot.context.properties.ConfigurationProperties
import java.io.File
import java.time.Duration
import kotlin.time.toKotlinDuration

/** `klib.process.*` — java.time.Duration kvůli bindingu (`5s`, `2m`). */
@ConfigurationProperties("klib.process")
data class ProcessProperties(
    /** povinné — vlastník událostí a metrik, např. "cdc-client" */
    val owner: String,
    val logsDirectory: File? = null,
    val restart: Restart = Restart(),
    val gracefulStop: Duration = Duration.ofSeconds(5),
    /** strop pro zastavení skupin, které aplikace při vypnutí nezastavila */
    val shutdownTimeout: Duration = Duration.ofSeconds(8),
) {
    data class Restart(
        val maxRestarts: Int = 30,
        val delay: Duration = Duration.ofSeconds(3),
        val stableRun: Duration = Duration.ofMinutes(10),
    ) {
        fun toPolicy() = RestartPolicy(maxRestarts, delay.toKotlinDuration(), stableRun.toKotlinDuration())
    }

    fun toDefaults() = ProcessDefaults(restart.toPolicy(), gracefulStop.toKotlinDuration(), logsDirectory)
}
