package cz.petrbalat.klib.process.autoconfigure

import cz.petrbalat.klib.process.ProcessGroups
import cz.petrbalat.klib.process.checkPidNamespaceSupport
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
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
// bez owner se nezapne — slouží jako identifikátor metrik a událostí
@ConditionalOnProperty("klib.process.owner")
@EnableConfigurationProperties(ProcessProperties::class)
class ProcessAutoConfiguration {

    /**
     * Destroy = odvozené `close()` (AutoCloseable), záměrně ne `SmartLifecycle`: `Lifecycle.stop()` běží před
     * `@PreDestroy` aplikace a zabil by procesy dřív, než je aplikace zastaví sama. Destroy callback běží až po
     * beanech, které na registru závisí → je jen pojistkou pro skupiny, které aplikace nezastavila.
     */
    @Bean
    @ConditionalOnMissingBean
    fun processGroups(
        properties: ProcessProperties,
        publisher: ApplicationEventPublisher,
    ): ProcessGroups = ProcessGroups(
        defaults = properties.toDefaults(),
        listener = ProcessEventPublisher(publisher, properties.owner),
        shutdownTimeout = properties.shutdownTimeout.toKotlinDuration(),
    )

    /**
     * Kontrola namespace po startu — jen ERROR do logu, ne pád: AOT trénink v `docker build` běží s výchozím
     * seccompem, kde `unshare --user` neprojde, a klient má i bez akcí hlásit stav na server.
     */
    @Bean
    fun pidNamespaceStartupCheck() = ApplicationRunner {
        runCatching { checkPidNamespaceSupport() }.onFailure {
            LoggerFactory.getLogger(ProcessAutoConfiguration::class.java)
                .error("Managed processes will not start — {}", it.message)
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry::class)
    class MetricsConfiguration {

        @Bean
        @ConditionalOnBean(MeterRegistry::class)
        @ConditionalOnMissingBean
        fun processMetrics(registry: MeterRegistry, properties: ProcessProperties) = MicrometerProcessMetrics(registry, properties.owner)
    }
}
