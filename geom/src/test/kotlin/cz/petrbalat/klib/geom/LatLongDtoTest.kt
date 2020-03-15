package cz.petrbalat.klib.geom

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class LatLongDtoTest {

    @Test
    fun testToString() {
        assertEquals("50.172301,14.425711", zdibyGeoPoint.toDto().toString())
    }

    @Test
    fun testparseGeoPointDto() {
        val str = "151.111111,10.234568"
        assertEquals(str, LatLongDto(151.111111111, 10.23456789).toString())
        assertEquals(parseGeoPointDto(str), LatLongDto(151.111111, 10.234568))
    }

}
