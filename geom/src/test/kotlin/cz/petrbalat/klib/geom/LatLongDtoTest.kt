package cz.petrbalat.klib.geom

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class LatLongDtoTest {

    @Test
    fun testToString() {
        assertEquals("50.172301,14.425711", zdibyGeoPoint.toDto().toString())
    }
}
