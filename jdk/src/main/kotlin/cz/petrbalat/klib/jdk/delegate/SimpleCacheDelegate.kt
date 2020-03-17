package cz.petrbalat.klib.jdk.delegate

import java.time.Duration
import kotlin.reflect.KProperty

/**
 * Cache delegate
 */
class SimpleCacheDelegate<T>(private val periodMillis: Long,
                             lock: Any? = null,
                             private val initializer: (oldData:T?) -> T) {

    private val lock = lock ?: this

    private var value: T? = null

    private var lastGet: Long = System.currentTimeMillis()

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = synchronized(lock) {
        val curentMillisec = System.currentTimeMillis()
        var value = value
        if (value == null || curentMillisec - lastGet >= periodMillis) {
            value = initializer(value)
            this.value = value
            lastGet = curentMillisec
            return value
        }
        return value
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value_: Any?) {
        value = initializer(value)
    }
}

/**
 * cache delegate - if any value is set, the value is taken from the initializer
 */
fun <T> cacheDelegate(duration: Duration, lock: Any? = null, initializer: (oldData:T?) -> T)
        = SimpleCacheDelegate(duration.toMillis(), lock, initializer)
