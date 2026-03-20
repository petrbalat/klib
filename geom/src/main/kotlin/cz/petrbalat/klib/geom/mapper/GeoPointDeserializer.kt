package cz.petrbalat.klib.geom.mapper

import cz.petrbalat.klib.geom.GeoPoint
import cz.petrbalat.klib.geom.createGeoPoint
import org.geolatte.geom.Point
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.node.NumericNode

/**
 * Created by Petr Balat
 */
class GeoPointDeserializer : StdDeserializer<GeoPoint>(Point::class.java) {

    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): GeoPoint? {
        val node: JsonNode = ctxt.readTree(parser)
        val lat: Number? = (node.get("lat") as? NumericNode)?.numberValue()
        val lon: Number? = (node.get("lon") as? NumericNode)?.numberValue()
        return if (lat === null || lon == null) null
        else createGeoPoint(lat = lat.toDouble(), lon = lon.toDouble())
    }
}
