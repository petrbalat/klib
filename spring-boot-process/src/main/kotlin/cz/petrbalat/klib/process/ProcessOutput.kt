package cz.petrbalat.klib.process

import java.io.File

/** Kam jde výstup [ManagedProcess] — vždy OS redirect, nikdy roura do JVM (tu by nikdo nečetl a proces by se zablokoval). */
sealed interface ProcessOutput {
    /** OS redirect do /dev/null — výstup nejde přes rouru, takže ji nemá kdo zaplnit */
    data object Discard : ProcessOutput

    /** stdout+stderr; větší log se při (re)startu odsune do `<log>.1` — padající proces by jinak zaplnil disk */
    data class ToFile(val file: File, val maxBytes: Long = DEFAULT_MAX_BYTES) : ProcessOutput {
        companion object {
            const val DEFAULT_MAX_BYTES: Long = 10L * 1024 * 1024
        }
    }
}

/** Log větší než [maxBytes] přesune do `<log>.1` (přepíše předchozí) — na disku tak zůstanou max. 2× [maxBytes]. */
fun rotateLog(logFile: File, maxBytes: Long) {
    if (logFile.length() <= maxBytes) return
    runCatching { logFile.renameTo(File(logFile.path + ".1").also { it.delete() }) }
}
