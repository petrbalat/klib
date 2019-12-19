package cz.petrbalat.klib.jdk.string

import kotlin.test.*

class StringUtilTest {

    @Test
    fun removeDiakritiku() {
        assertEquals("aABcescrzyaieuu123.png", "aABcěščřžýáíéúů123.png".removeDiakritiku())

        assertEquals("ESCRZYAIEUU", "ĚŠČŘŽÝÁÍÉÚŮ".removeDiakritiku())
    }

    @Test
    fun removeNotAlowedInFileName() {
        assertEquals("aab--.png", removeNotAlowedInFileName("aa(b)-/*-.png"))
        assertEquals("this_is_a_filename.jpg", removeNotAlowedInFileName("this_is_a_filename.jpg"))
        assertEquals("this_is_not_a_filename.jpg", removeNotAlowedInFileName("this%is%not%a%filename.jpg","_"))
    }

    @Test
    fun randomString() {
        val strings = randomString(4)
        assertEquals(4, strings.length)
        assertNotEquals(randomString(4), strings)

        assertEquals(6, randomString(6).length)
    }

    @Test
    fun emptyToNull() {
        assertNotNull("x".isNullOrEmpty())
        assertNull("".emptyToNull())
    }

    @Test
    fun AlphanumComparatorTest() {
        val sortedList = listOf("aa123 bb", "aa23").sortedWith(AlphanumComparator)
        assertEquals("aa23", sortedList.first())
        assertEquals("aa123 bb", sortedList.last())

        val sortedListNormal = listOf("aa123 bb", "aa23").sorted()
        assertEquals("aa23", sortedListNormal.last())
        assertEquals("aa123 bb", sortedListNormal.first())
    }


    @Test
    fun removeNonAlphaNorNumeric() {
        assertEquals("x64ěšúśdfK", "x64ěš.§ú.§śdf.§§K:?_*&|".removeNonAlphaNorNumeric())
        assertEquals("x64esusdfK", "x64ěš.§ú.§śdf.§§K".removeNonAlphaNorNumeric().removeDiakritiku())
    }

    @Test
    fun testParseEmail() {
        assertTrue(parseEmail("x").none())

        val gmail = parseEmail("asdf, petr.balat@gmail.com").toList()
        assertEquals("petr.balat@gmail.com", gmail.single())

        val gmailAndSeznam = parseEmail("petr.balat@seznam.cz, petr.balat@gmail.com, ble ble").toList()
        assertEquals("petr.balat@seznam.cz", gmailAndSeznam[0])
        assertEquals("petr.balat@gmail.com", gmailAndSeznam[1])
    }
}
