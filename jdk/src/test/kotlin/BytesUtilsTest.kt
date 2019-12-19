package cz.petrbalat.klib.jdk

import kotlin.test.Test
import kotlin.test.assertEquals

class BytesUtilsTest {

    @Test
    fun doubleToBytes() {
        val num: Double = 5615314.9431
        val bytes = num.toByteArray()
        assertEquals(num, toDouble(bytes))
    }

    @Test
    fun floadToBytes() {
        val num: Float = 56114.9431F
        val bytes = num.toByteArray()
        assertEquals(num, toFloat(bytes))
    }

    @Test
    fun intToBytes() {
        val num: Int = Int.MAX_VALUE
        val bytes = num.toByteArray()
        assertEquals(num, toInt(bytes))
    }

    @Test
    fun testLongToBytes() {
        val num: Long = Long.MIN_VALUE
        val bytes = num.toByteArray()
        assertEquals(num, toLong(bytes))
    }
}
