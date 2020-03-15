package cz.petrbalat.klib.geom

import kotlin.test.Test
import kotlin.test.assertEquals

internal class PostgisUtilsTest {

    @Test
    fun testDistance() {
        //zdiby domov -> praha centrum
        assertEquals(9442, (zdibyGeoPoint distance centrumPrahaGeoPoint).toInt())
        assertEquals(9442, (zdibyGeoPoint.toDto() distance centrumPrahaGeoPoint).toInt())
        assertEquals(9442, (zdibyGeoPoint distance centrumPrahaGeoPoint.toDto()).toInt())
        assertEquals(9442, (zdibyGeoPoint.toDto() distance centrumPrahaGeoPoint.toDto()).toInt())
        assertEquals(9442, (centrumPrahaGeoPoint distance zdibyGeoPoint).toInt())
        assertEquals(9442, (centrumPrahaGeoPoint.toDto() distance zdibyGeoPoint).toInt())
        assertEquals(9442, (centrumPrahaGeoPoint distance zdibyGeoPoint.toDto()).toInt())
        assertEquals(9442, (centrumPrahaGeoPoint.toDto() distance zdibyGeoPoint.toDto()).toInt())

        //brandýs domov -> praha centrum
        assertEquals(19967, (brandysGeoPoint distance centrumPrahaGeoPoint).toInt())
        assertEquals(19967, (brandysGeoPoint.toDto() distance centrumPrahaGeoPoint).toInt())
        assertEquals(19967, (brandysGeoPoint distance centrumPrahaGeoPoint.toDto()).toInt())
        assertEquals(19967, (brandysGeoPoint.toDto() distance centrumPrahaGeoPoint.toDto()).toInt())
        assertEquals(19967, (centrumPrahaGeoPoint distance brandysGeoPoint).toInt())
        assertEquals(19967, (centrumPrahaGeoPoint.toDto() distance brandysGeoPoint).toInt())
        assertEquals(19967, (centrumPrahaGeoPoint distance brandysGeoPoint.toDto()).toInt())
        assertEquals(19967, (centrumPrahaGeoPoint.toDto() distance brandysGeoPoint.toDto()).toInt())

        //brandýs -> zdiby
        assertEquals(16080, (zdibyGeoPoint.toDto() distance brandysGeoPoint.toDto()).toInt())
    }
}
