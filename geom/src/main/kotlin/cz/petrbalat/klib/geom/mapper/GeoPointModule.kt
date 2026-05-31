package cz.petrbalat.klib.geom.mapper

import tools.jackson.databind.module.SimpleModule
import org.geolatte.geom.Point

class GeoPointModule : SimpleModule() {

    init {
        addDeserializer(Point::class.java, GeoPointDeserializer())
        addSerializer(Point::class.java, GeoPointSeserializer())
    }
}
