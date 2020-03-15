package cz.petrbalat.klib.geom


import org.geolatte.geom.G2D
import org.geolatte.geom.Point
import org.geolatte.geom.crs.CoordinateReferenceSystems
import kotlin.math.*

/**
 * A position in a 2D geographic coordinate reference system.
 */
typealias GeoPoint = Point<G2D>

/**
 * zeměpisná šířka (Praha 50° 05' severní šířky)
 */
val GeoPoint.lat get() = position.lat

/**
 * zeměpisná délka (14° 25' východní délky)
 */
val GeoPoint.lon get() = position.lon

fun GeoPoint.toDto() = LatLongDto(lon = lon, lat = lat)
fun LatLongDto.toGeoPoint() = createGeoPoint(lon = lon, lat = lat)

fun createGeoPoint(lon: Double, lat: Double): GeoPoint {
    val position = G2D(lon, lat)
    return Point(position, CoordinateReferenceSystems.WGS84)
}

/**
 * vzdálenost v [m] mezi body
 */
infix fun GeoPoint.distance(point: GeoPoint): Double = distance(lat1 = this.lat, lon1 = this.lon, lat2 = point.lat, lon2 = point.lon)

/**
 * vzdálenost v [m] mezi body
 */
infix fun GeoPoint.distance(point: LatLongDto): Double = distance(lat1 = this.lat, lon1 = this.lon, lat2 = point.lat, lon2 = point.lon)

/**
 * vzdálenost v [m] mezi body
 */
infix fun LatLongDto.distance(point: LatLongDto): Double = distance(lat1 = this.lat, lon1 = this.lon, lat2 = point.lat, lon2 = point.lon)

/**
 * vzdálenost v [m] mezi body
 */
infix fun LatLongDto.distance(point: GeoPoint): Double = distance(lat1 = this.lat, lon1 = this.lon, lat2 = point.lat, lon2 = point.lon)

/*
 * https://stackoverflow.com/questions/3694380/calculating-distance-between-two-points-using-latitude-longitude
 */
private fun distance(lat1: Double, lat2: Double,
                     lon1: Double, lon2: Double,
                     el1: Double = 0.0, el2: Double = 0.0): Double {
    val Radius = 6371 // Radius of the earth
    val latDistance = Math.toRadians(lat2 - lat1)
    val lonDistance = Math.toRadians(lon2 - lon1)
    val a = (sin(latDistance / 2) * sin(latDistance / 2)
            + (cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2))
            * sin(lonDistance / 2) * sin(lonDistance / 2)))
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    var distance = Radius * c * 1000 // convert to meters
    val height = el1 - el2
    distance = distance.pow(2.0) + height.pow(2.0)
    return sqrt(distance)
}

