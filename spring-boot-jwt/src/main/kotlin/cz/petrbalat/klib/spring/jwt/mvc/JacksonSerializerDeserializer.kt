package cz.petrbalat.klib.spring.jwt.mvc

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.io.Deserializer
import io.jsonwebtoken.io.Serializer

class JacksonSerializer(private val mapper: ObjectMapper) : Serializer<MutableMap<String, *>?> {
    override fun serialize(map: MutableMap<String, *>?): ByteArray {
        return mapper.writeValueAsBytes(map)
    }
}

class JacksonDeserializer(private val mapper: ObjectMapper) : Deserializer<MutableMap<String, *>?> {

    override fun deserialize(bytes: ByteArray): MutableMap<String, *>? {
        val clazz: Class<Any> = MutableMap::class.java as Class<Any>
        return mapper.readValue(bytes, clazz) as MutableMap<String, *>
    }
}
