package cz.petrbalat.klib.jdk

import cz.petrbalat.klib.jdk.support.TestEnum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommonUtilsTest {

    @Test
    fun toBooleanInt() {
        assertEquals(1, true.toBooleanInt())
        assertEquals(0, false.toBooleanInt())
    }

    @Test
    fun relativelyEqual() {
        assertTrue(0.0 relativelyEqual 0.0)
        assertFalse(5.123456799 relativelyEqual 5.1234567)
        assertTrue(5.123456799 relativelyEqual 5.123456799)
        assertTrue(5.123456799999 relativelyEqual 5.123456799999)
    }


    @Test
    fun enumFullName() {
        val aa: Enum<TestEnum> = TestEnum.PRVEK1
        assertEquals("cz.petrbalat.klib.jdk.support.TestEnum.PRVEK1", aa.fullName)
        assertEquals("cz.petrbalat.klib.jdk.support.TestEnum.PRVEK2", TestEnum.PRVEK2.fullName)
    }
}
