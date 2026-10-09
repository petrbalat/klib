package cz.petrbalat.klib.process

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Háčky registru — restart a vzdání se watchdogu procesu některé skupiny. Volají se synchronně z vlákna watchdogu
 * a po [ProcessGroup.stop] už ne. [gaveUp] smí skupinu zastavit (i přes `runBlocking`); jinak nesmí dlouho
 * blokovat — zdržel by restart.
 */
interface ProcessGroupsListener {
    fun restarting(group: ProcessGroup, name: String, restarting: ProcessState.Restarting) {}
    fun gaveUp(group: ProcessGroup, giveUp: GiveUp) {}
}

/**
 * Továrna + registr skupin procesů; bez Springu, ve Spring Bootu bean z autokonfigurace (`klib.process.*`).
 * [close] je pojistka pro skupiny, které aplikace nezastavila — zastaví je souběžně, nejvýš [shutdownTimeout].
 */
class ProcessGroups(
    private val tagging: ProcessTagging,
    private val reaper: ProcessReaper = ProcessReaper(tagging),
    private val defaults: ProcessDefaults = ProcessDefaults(),
    private val listener: ProcessGroupsListener = object : ProcessGroupsListener {},
    private val shutdownTimeout: Duration = 8.seconds,
) : AutoCloseable {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val _groups = MutableStateFlow<Map<String, ProcessGroup>>(emptyMap())

    /** živé skupiny — po `stop()` se z registru odeberou */
    val groups: StateFlow<Map<String, ProcessGroup>> = _groups.asStateFlow()

    private val groupListener = object : ProcessGroupListener {
        override fun restarted(group: ProcessGroup, name: String, restarting: ProcessState.Restarting) =
            listener.restarting(group, name, restarting)

        override fun gaveUp(group: ProcessGroup, giveUp: GiveUp) = listener.gaveUp(group, giveUp)

        override fun stopped(group: ProcessGroup) {
            _groups.update { if (it[group.id] === group) it - group.id else it }
        }
    }

    /**
     * Nová skupina; watchdogy procesů běží ve [scope]. Id musí být mezi živými skupinami unikátní — i vůči skupině,
     * která se ještě zastavuje. Skončí-li [scope] bez [ProcessGroup.stop], skupina se zastaví a z registru zmizí sama.
     */
    fun create(id: String, scope: CoroutineScope): ProcessGroup {
        val group = ProcessGroup(id, tagging, scope, reaper, defaults, groupListener)
        _groups.update {
            val existing = it[id]
            require(existing == null) {
                if (existing?.isStopped == true) "Process group $id is still stopping" else "Process group $id already exists"
            }
            it + (id to group)
        }
        group.stopOnScopeCompletion()
        return group
    }

    fun find(id: String): ProcessGroup? = _groups.value[id]

    /** Zabije sirotky vlastníka z minulého běhu JVM (procesy s jeho tagem); vrací počet. */
    fun reapOrphans(): Int {
        val killed = reaper.killAllOwned()
        if (killed > 0) logger.warn("Killed {} orphaned processes of owner {}", killed, tagging.owner)
        return killed
    }

    /** Zastaví skupiny, které aplikace nezastavila — souběžně, nejvýš [shutdownTimeout]. */
    override fun close() {
        val remaining: Collection<ProcessGroup> = _groups.value.values
        if (remaining.isEmpty()) return
        logger.info("Stopping {} process groups left running: {}", remaining.size, remaining.map { it.id })
        runBlocking {
            // stopy jako sourozenci, ne děti timeoutu — po limitu jen přestaneme čekat (zbytek dorazí SIGKILL kontejneru)
            val stops = remaining.map { group -> CoroutineScope(Dispatchers.IO).async { group.stop() } }
            withTimeoutOrNull(shutdownTimeout) { stops.awaitAll() }
                ?: logger.warn("Process groups did not stop within {}", shutdownTimeout)
        }
    }
}
