package cz.petrbalat.klib.jdk.concurrent

import java.util.concurrent.atomic.AtomicBoolean

/**
 * spustí pokud je lock nataven na false a po ukončení ho zase vypne
 */
inline fun runAtomic(lock: AtomicBoolean, action: () -> Unit) {
    if (lock.compareAndSet(false, true)) {
        try {
            action()
        } finally {
            lock.set(false)
        }
    }
}

inline fun <T> runAtomic(lock: AtomicBoolean, action: () -> T): T? = if (lock.compareAndSet(false, true)) {
    try {
        action()
    } finally {
        lock.set(false)
    }
} else null
