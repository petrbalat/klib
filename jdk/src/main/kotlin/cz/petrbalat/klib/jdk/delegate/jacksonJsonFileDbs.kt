package cz.petrbalat.klib.jdk.delegate

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.time.Duration


inline fun <reified T> jsonFileDbByJackson(directory: File, mapper: ObjectMapper) = JsonFileDbDelegate(
        T::class.java, directory,
        { value, writer -> mapper.writeValue(writer, value) },
        { reader -> mapper.readValue(reader, T::class.java) }
)

inline fun <reified T> cacheJsonFileDbByJackson(directory: File, mapper: ObjectMapper,
                                                duration: Duration) = CacheJsonFileDbDelegate(
        T::class.java, directory,
        { value, writer -> mapper.writeValue(writer, value) },
        { reader -> mapper.readValue(reader, T::class.java) },
        duration
)
