package cz.petrbalat.klib.process.autoconfigure

import cz.petrbalat.klib.process.ProcessGroups
import cz.petrbalat.klib.process.ProcessReaper
import cz.petrbalat.klib.process.ProcessTagging
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.time.toKotlinDuration

// po registru metrik (Boot 4 / Boot 3 balíček) — jinak @ConditionalOnBean(MeterRegistry) nic nenajde
@AutoConfiguration(
    afterName = [
        "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration",
    ],
)
// bez owner se nezapne — modul jde použít jen kvůli runProcess / IntervalScheduler (a start nezabíjí cizí procesy)
@ConditionalOnProperty("klib.process.owner")
@EnableConfigurationProperties(ProcessProperties::class)
class ProcessAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun processTagging(properties: ProcessProperties) = ProcessTagging(properties.owner, properties.envName)

    @Bean
    @ConditionalOnMissingBean
    fun processReaper(tagging: ProcessTagging) = ProcessReaper(tagging)

    /**
     * Destroy = odvozené `close()` (AutoCloseable), záměrně ne `SmartLifecycle`: `Lifecycle.stop()` běží před
     * `@PreDestroy` aplikace a zabil by procesy dřív, než je aplikace zastaví sama. Destroy callback běží až po
     * beanech, které na registru závisí → je jen pojistkou pro skupiny, které aplikace nezastavila.
     */
    @Bean
    @ConditionalOnMissingBean
    fun processGroups(
        properties: ProcessProperties,
        tagging: ProcessTagging,
        reaper: ProcessReaper,
        publisher: ApplicationEventPublisher,
    ): ProcessGroups = ProcessGroups(
        tagging = tagging,
        reaper = reaper,
        defaults = properties.toDefaults(),
        listener = ProcessEventPublisher(publisher, tagging.owner),
        shutdownTimeout = properties.shutdownTimeout.toKotlinDuration(),
    ).also { if (properties.reapOrphansOnStartup) it.reapOrphans() }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry::class)
    class MetricsConfiguration {

        @Bean
        @ConditionalOnBean(MeterRegistry::class)
        @ConditionalOnMissingBean
        fun processMetrics(registry: MeterRegistry, tagging: ProcessTagging) = MicrometerProcessMetrics(registry, tagging.owner)
    }
}
