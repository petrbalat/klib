package cz.petrbalat.klib.process

import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

/**
 * Cílený úklid procesů podle tagu v env ([ProcessTagging]) — dědí ho i odpojení potomci (`setsid`,
 * `chrome_crashpad_handler`, `ssh` pod autossh). Procesy jiného uživatele (pod `sudo`) nejsou čitelné a přeskočí se;
 * bez `/proc` (macOS, Windows) no-op.
 */
class ProcessReaper(
    private val tagging: ProcessTagging,
    private val procDirectory: File = File("/proc"),
    private val killer: (pid: Long) -> Unit = ::killPid,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /** SIGKILL všem procesům skupiny [groupId]; vrací počet zabitých. */
    fun kill(groupId: String): Int {
        val tag = tagging.tag(groupId)
        return killWhere { it == tag }
    }

    /** Všechny procesy tohoto vlastníka — pojistka při startu aplikace (sirotci z minulého běhu JVM). */
    fun killAllOwned(): Int = killWhere { tagging.isOwned(it) }

    private fun killWhere(matches: (tag: String) -> Boolean): Int {
        val killed: List<Long> = killByEnv(tagging.envName, procDirectory, killer, matches)
        if (killed.isNotEmpty()) logger.info("Killed tagged processes {}", killed)
        return killed.size
    }
}

internal val PROC_DIRECTORY = File("/proc")

/** SIGKILL procesům, jejichž env [envName] splňuje [matches] — i potomkům mimo strom; vrací jejich PID. Bez `/proc` no-op. */
internal fun killByEnv(
    envName: String,
    procDirectory: File = PROC_DIRECTORY,
    killer: (pid: Long) -> Unit = ::killPid,
    matches: (value: String) -> Boolean,
): List<Long> = findProcessesByEnv(procDirectory, envName, matches).onEach { pid -> runCatching { killer(pid) } }

private fun killPid(pid: Long) {
    ProcessHandle.of(pid).ifPresent { it.destroyForcibly() }
}

/** PID procesů (mimo vlastní JVM), jejichž env [envName] splňuje [matches]; bez `/proc` prázdný seznam. */
internal fun findProcessesByEnv(procDirectory: File, envName: String, matches: (value: String) -> Boolean): List<Long> {
    val self = ProcessHandle.current().pid()
    return procDirectory.listFiles().orEmpty()
        .mapNotNull { it.name.toLongOrNull() }
        .filter { pid ->
            if (pid == self) return@filter false
            val value = readProcessTag(File(procDirectory, pid.toString()), envName) ?: return@filter false
            matches(value)
        }
}

/** Hodnota [envName] z `/proc/<pid>/environ`; null = bez tagu, proces skončil nebo není čitelný. */
internal fun readProcessTag(procPidDirectory: File, envName: String): String? {
    val environ: ByteArray = try {
        File(procPidDirectory, "environ").readBytes()
    } catch (e: IOException) {
        return null
    }
    val prefix = "$envName="
    return environ.toString(Charsets.UTF_8)
        .splitToSequence('\u0000')
        .firstOrNull { it.startsWith(prefix) }
        ?.removePrefix(prefix)
}
