package cz.petrbalat.klib.geom.mapper

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.NumericNode
import cz.petrbalat.klib.geom.GeoPoint
import cz.petrbalat.klib.geom.createGeoPoint
import org.geolatte.geom.Point

/**
 * Created by Petr Balat
 */
class GeoPointDeserializer : StdDeserializer<GeoPoint>(Point::class.java) {

    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext?): GeoPoint? {
        val node: JsonNode = parser.codec.readTree(parser)
        val lat: Number? = (node.get("lat") as? NumericNode)?.numberValue()
        val lon: Number? = (node.get("lon") as? NumericNode)?.numberValue()
        return if (lat === null || lon == null) null
        else createGeoPoint(lat = lat.toDouble(), lon = lon.toDouble())
    }
}
