package cz.petrbalat.klib.jdk.delegate

import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.time.Duration


inline fun <reified T> jsonFileDbByJackson(directory: File, mapper: JsonMapper) = JsonFileDbDelegate(
        T::class.java, directory,
        { value, writer -> mapper.writeValue(writer, value) },
        { reader -> mapper.readValue(reader, T::class.java) }
)

inline fun <reified T> cacheJsonFileDbByJackson(directory: File, mapper: JsonMapper,
                                                duration: Duration) = CacheJsonFileDbDelegate(
        T::class.java, directory,
        { value, writer -> mapper.writeValue(writer, value) },
        { reader -> mapper.readValue(reader, T::class.java) },
        duration
)
