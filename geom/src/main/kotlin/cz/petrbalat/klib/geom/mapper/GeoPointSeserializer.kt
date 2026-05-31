package cz.petrbalat.klib.geom.mapper

import cz.petrbalat.klib.geom.GeoPoint
import cz.petrbalat.klib.geom.lat
import cz.petrbalat.klib.geom.lon
import org.geolatte.geom.Point
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer

/**
 * Created by Petr balat
 */
class GeoPointSeserializer : ValueSerializer<Point<*>>() {
    override fun serialize(value: Point<*>?, json: JsonGenerator, serializers: SerializationContext?) {
        val geo = value as? GeoPoint
        if (geo == null) {
            json.writeNull()
            return
        }

        json.writeStartObject()
        json.writeNumberProperty("lat", geo.lat)
        json.writeNumberProperty("lon", geo.lon)
        json.writeEndObject()
    }
}

