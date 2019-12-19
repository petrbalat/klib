package cz.petrbalat.klib.jdk.io

import java.io.*
import java.util.*

/**
 * Created by Petr on 9. 11. 2014.
 */
fun fromString(str: String): Any {
    val data = Base64.getDecoder().decode(str.toByteArray())
    ObjectInputStream(ByteArrayInputStream(data)).use { ois -> return ois.readObject() }
}

fun fromByteArray(bytes: ByteArray): Any =
        ObjectInputStream(ByteArrayInputStream(bytes)).use { ois -> return ois.readObject() }

fun serializeToString(o: Serializable): String {
    val baos = ByteArrayOutputStream()
    ObjectOutputStream(baos).use {
        it.writeObject(o);it.flush()
    }
    return String(Base64.getEncoder().encode(baos.toByteArray()))
}

fun toByteArray(o: Serializable): ByteArray {
    val baos = ByteArrayOutputStream()
    ObjectOutputStream(baos).use {
        it.writeObject(o);it.flush()
    }
    return baos.toByteArray()
}

fun clone(obj: Serializable?): Any? {
    return if (obj == null) {
        null
    } else {
        val byteArray = toByteArray(obj)
        fromByteArray(byteArray)
    }
}
