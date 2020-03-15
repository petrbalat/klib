package cz.petrbalat.klib.geom

import java.text.NumberFormat
import java.util.*

data class LatLongDto(val lat: Double, val lon: Double,
                      val speed: Float? = null,
                      /**
                       * bearing, in degrees (0.0, 360.0]
                       */
                      val bearing: Float? = null,
                      val accuracy: Float? = null
) {
    override fun toString() = "${geopointDecimalFormatter.format(lat)},${geopointDecimalFormatter.format(lon)}"
}

fun parseGeoPointDto(str: String): LatLongDto? {
    val lat: Double = geopointDecimalFormatter.parse(str.substringBefore(",")).toDouble()
    val lon: Double = geopointDecimalFormatter.parse(str.substringAfter(",")).toDouble()
    return LatLongDto(lat = lat, lon = lon)
}

val geopointDecimalFormatter: NumberFormat by lazy {
    NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 6
    }
}
