package cz.petrbalat.klib.jdk.math

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BigDecimalUtilTest {

    @Test
    fun isEquals() {
        assertTrue(BigDecimal("123.456") equalsTo  BigDecimal("123.456000"))
        assertFalse(BigDecimal("123.4561") equalsTo  BigDecimal("123.456000"))
    }

    @Test
    fun orZero() {
        var bigDecimal: BigDecimal? = 123.456.toBigDecimal()
        assertEquals(bigDecimal, bigDecimal.orZero())

        bigDecimal = null
        assertEquals(BigDecimal.ZERO, bigDecimal.orZero())
    }


    @Test
    fun howManyPercentOf(): Unit {
        val petZe100 = BigDecimal.ONE howManyPercentOf 100.toBigDecimal()
        assertTrue(BigDecimal.ONE equalsTo petZe100)

        val triZe100 = 3.toBigDecimal() howManyPercentOf 100.toBigDecimal()
        assertTrue(3.toBigDecimal() equalsTo triZe100)

        val jednoZ50 = 5.toBigDecimal() howManyPercentOf 50.toBigDecimal()
        assertTrue(10.toBigDecimal() equalsTo jednoZ50)

        val dvaZe2 = 2.toBigDecimal() howManyPercentOf 2.toBigDecimal()
        assertTrue(100.toBigDecimal() equalsTo dvaZe2)

        val stoZe5 = 20.toBigDecimal() howManyPercentOf 5.toBigDecimal()
        assertTrue(400.toBigDecimal() equalsTo stoZe5)

        val test = 2_519.toBigDecimal() howManyPercentOf 4_199.toBigDecimal()
        assertTrue(60.toBigDecimal() equalsTo test.roundHalfUp())
    }

    @Test
    fun percentOf(): Unit {
        val petZe100 = 5.toBigDecimal() percentOf 100.toBigDecimal()
        assertTrue(5.toBigDecimal() equalsTo petZe100)

        val jednoZ50 = BigDecimal.ONE percentOf 50.toBigDecimal()
        assertTrue(BigDecimal("0.5") equalsTo jednoZ50)

        val dvaZe2 = 2.toBigDecimal() percentOf 2.toBigDecimal()
        assertTrue(BigDecimal("0.04") equalsTo dvaZe2)

        val stoZe5 = 100.toBigDecimal() percentOf 5.toBigDecimal()
        assertTrue(5.toBigDecimal() equalsTo stoZe5)
    }


    @Test
    fun plusPercent(): Unit {
        val petPlus100procent = 5.toBigDecimal() plusPercent 100.toBigDecimal()
        assertTrue(10.toBigDecimal() equalsTo petPlus100procent)

        val jednoPlus50procent = BigDecimal("1") plusPercent 50.toBigDecimal()
        assertTrue(BigDecimal("1.5") equalsTo jednoPlus50procent)

        val dvaPlus2procenta = 2.toBigDecimal() plusPercent 2.toBigDecimal()
        assertTrue(BigDecimal("2.04") equalsTo dvaPlus2procenta)

        val stoZe5 = 100.toBigDecimal() plusPercent 5.toBigDecimal()
        assertTrue(105.toBigDecimal() equalsTo stoZe5)
    }

    @Test
    fun minusPercent(): Unit {
        val petMinus100procent = 5.toBigDecimal() minusPercent 100.toBigDecimal()
        assertTrue(BigDecimal.ZERO equalsTo petMinus100procent)

        val jednoMinus50procent = BigDecimal("1") minusPercent 50.toBigDecimal()
        assertTrue(BigDecimal("0.5") equalsTo jednoMinus50procent)

        val dvaMinus2procenta = 2.toBigDecimal() minusPercent 2.toBigDecimal()
        assertTrue(BigDecimal("1.96") equalsTo dvaMinus2procenta)

        val stoMinus5procent = 100.toBigDecimal() minusPercent 5.toBigDecimal()
        assertTrue(95.toBigDecimal() equalsTo stoMinus5procent)
    }


    @Test
    fun divide128(): Unit {
        val value: BigDecimal = 100.toBigDecimal() divide128 BigDecimal(3)
        assertTrue(100.toBigDecimal() equalsTo (value * BigDecimal(3)).roundHalfUp(2))
    }

    @Test
    fun testRound(): Unit {
        assertEquals(BigDecimal(13), BigDecimal(12.458).roundCeiling())
        assertEquals(BigDecimal("12.46"), BigDecimal(12.458).roundCeiling(2))
        assertEquals(BigDecimal(12), BigDecimal(12.408).roundFloor())
        assertEquals(BigDecimal("12.4"), BigDecimal(12.408).roundFloor(1))
        assertEquals(BigDecimal("12.40"), BigDecimal(12.408).roundFloor(2))
        assertEquals(BigDecimal(12), BigDecimal(12.458).roundHalfUp())
        assertEquals(BigDecimal(13), BigDecimal(12.508).roundHalfUp())
    }

}
