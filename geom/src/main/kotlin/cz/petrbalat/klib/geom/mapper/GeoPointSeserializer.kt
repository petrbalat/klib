package cz.petrbalat.klib.geom.mapper

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import cz.petrbalat.klib.geom.GeoPoint
import cz.petrbalat.klib.geom.lat
import cz.petrbalat.klib.geom.lon
import org.geolatte.geom.Point

/**
 * Created by Petr balat
 */
class GeoPointSeserializer : JsonSerializer<Point<*>>() {
    override fun serialize(value: Point<*>?, json: JsonGenerator, serializers: SerializerProvider?) {
        val geo = value as? GeoPoint
        if (geo == null) {
            json.writeNull()
            return
        }

        json.writeStartObject()
        json.writeNumberField("lat", geo.lat)
        json.writeNumberField("lon", geo.lon)
        json.writeEndObject()
    }
}

