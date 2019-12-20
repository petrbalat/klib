package cz.petrbalat.klib.jdk.math

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

internal class BDTest {

    val zero = BD(BigDecimal.ZERO)
    val ten = 10.toBD()
    val two = 2.toBD()

    @Test
    fun equalsTo() {
        val tenPoint = "10.0".toBigDecimal()
        assertNotEquals(ten.value, tenPoint)
        assertTrue(ten equalsTo tenPoint.toBD())
        assertTrue(ten equalsTo tenPoint)
        assertFalse(ten equalsTo two)
    }

    @Test
    fun compareTo() {
        assertTrue(ten > 1.toBD())
        assertFalse(zero > 1.toBD())
        assertTrue(zero < 1.toBD())
        assertFalse(zero > two)
    }

    @Test
    fun plus() {
        assertTrue(11.toBD() equalsTo ten + 1.toBD())
        assertFalse(10.toBD() equalsTo ten + 1.toBD())
        assertTrue(12.toBD() equalsTo ten + two)
        assertTrue("4.9".toBD() equalsTo "1.2".toBD() + "3.7".toBD())
    }

    @Test
    fun minus() {
        assertTrue(9.toBD() equalsTo ten - 1.toBD())
        assertFalse(10.toBD() equalsTo ten - 1.toBD())
        assertTrue(8.toBD() equalsTo ten - two)
        assertTrue("-2.5".toBD() equalsTo "1.2".toBD() - "3.7".toBD())
    }

    @Test
    fun div() {
        assertTrue(ten equalsTo ten / 1.toBD())
        assertTrue(5.toBD() equalsTo ten / two)
        assertFalse(10.toBD() equalsTo ten / two)
        assertTrue("1.2".toBD() equalsTo "4.8".toBD() / "4".toBD())
    }

    @Test
    fun times() {
        assertTrue(30.toBD() equalsTo ten * 3.toBD())
        assertFalse(10.toBD() equalsTo ten * 2.toBD())
        assertTrue(20.toBD() equalsTo ten * two)
        assertTrue("4.44".toBD() equalsTo "1.2".toBD() * "3.7".toBD())
    }

    @Test
    fun orZero() {
        val bd: BD? = null
        assertTrue(0.toBD() equalsTo bd.orZEro())
        assertFalse(10.toBD() equalsTo two.orZEro())
        assertTrue(2.toBD() equalsTo two.orZEro())
    }

    @Test
    fun roundHalfUp() {
        assertTrue(1.toBD() equalsTo 1.25.toBD().roundHalfUp())
        assertTrue("1.3".toBD() equalsTo 1.25.toBD().roundHalfUp(1))
    }
}
