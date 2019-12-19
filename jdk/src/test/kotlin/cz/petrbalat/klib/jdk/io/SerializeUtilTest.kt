package cz.petrbalat.klib.jdk.cz.petrbalat.klib.jdk.io

import cz.petrbalat.klib.jdk.io.clone
import cz.petrbalat.klib.jdk.io.fromString
import cz.petrbalat.klib.jdk.io.serializeToString
import java.io.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SerializeUtilTest {

    @Test
    fun testFromString() {
        val user = TestSerDto(age = 4, name = "Ondra")
        val str = serializeToString(user)
        val userDes = fromString(str)
        assertEquals(user, userDes)
    }


    @Test
    fun testClone() {
        val obj = TestSerDto("a")
        val clone = clone(obj)
        assertEquals(obj, clone)
        assertFalse(obj === clone)
    }


    data class TestSerDto(val name: String, val age: Int? = null) : Serializable
}
