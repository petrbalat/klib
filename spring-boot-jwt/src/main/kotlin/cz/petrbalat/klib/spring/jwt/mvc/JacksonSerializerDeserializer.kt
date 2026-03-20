package cz.petrbalat.klib.spring.jwt.mvc

import io.jsonwebtoken.io.Deserializer
import io.jsonwebtoken.io.Serializer
import tools.jackson.databind.json.JsonMapper

class JacksonSerializer(private val mapper: JsonMapper) : Serializer<MutableMap<String, *>?> {
    override fun serialize(map: MutableMap<String, *>?): ByteArray {
        return mapper.writeValueAsBytes(map)
    }
}

class JacksonDeserializer(private val mapper: JsonMapper) : Deserializer<MutableMap<String, *>?> {

    override fun deserialize(bytes: ByteArray): MutableMap<String, *>? {
        val clazz: Class<Any> = MutableMap::class.java as Class<Any>
        return mapper.readValue(bytes, clazz) as MutableMap<String, *>
    }
}
