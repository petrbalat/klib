package cz.petrbalat.klib.jdk.collections

import cz.petrbalat.klib.jdk.math.isEquals
import java.math.BigDecimal
import kotlin.test.*

/**
 * Created by balat on 25.03.2017.
 */
class CollectionUtilsTest {

    private val testList = listOf(1, 2, 3, 4, 5)


    @Test
    fun next() {
        assertEquals(4, testList.next(3))
        assertEquals(3, testList.next(2))
        assertEquals(null, testList.next(5))
    }

    @Test
    fun previous() {
        assertEquals(2, testList.previous(3))
        assertEquals(4, testList.previous(5))
        assertEquals(null, testList.previous(1))
    }

    @Test
    fun random() {
        assertNotNull(testList.random())
        assertNotNull(testList.random())
        assertNotNull(testList.random())

        assertNotEquals(testList.random(), testList.random())
    }


    @Test
    fun sumByBigDecimal() {
        val one = listOf(BigDecimal.ZERO, BigDecimal.ONE)
        assertEquals(1.toBigDecimal(), one.sumByBigDecimal { it })
        assertEquals(11.toBigDecimal(), (one + BigDecimal.TEN).sumByBigDecimal { it })
        assertTrue(3.5.toBigDecimal() isEquals (one + 2.5.toBigDecimal()).sumByBigDecimal { it })
    }

}
