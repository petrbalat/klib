package cz.petrbalat.klib.jdk.delegate

import java.io.File
import java.io.Reader
import java.io.Writer
import java.time.Duration
import kotlin.reflect.KProperty

/**
 * Wrapper over JsonFileDbDelegate with cache
 */
class CacheJsonFileDbDelegate<T>(clazz: Class<T>,
                                 directory: File,
                                 serialize: (value: T, writer: Writer) -> Unit,
                                 deserialize: (reader: Reader) -> T,
                                 duration: Duration) {

    private var db: T by JsonFileDbDelegate(clazz, directory, serialize, deserialize)

    private var cache: T by cacheDelegate(duration) {
        this.db
    }

    operator fun getValue(thisRef: Any, property: KProperty<*>): T = cache

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        db = value
        cache = db
    }
}
