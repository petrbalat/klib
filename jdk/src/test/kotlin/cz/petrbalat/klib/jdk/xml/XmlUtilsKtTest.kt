package cz.petrbalat.klib.jdk.cz.petrbalat.klib.jdk.xml

import cz.petrbalat.klib.jdk.xml.XmlElementDto
import cz.petrbalat.klib.jdk.xml.readXmlAsSequence
import java.io.ByteArrayInputStream
import java.nio.file.Paths
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertTrue

internal class XmlUtilsKtTest {

    @Test
    fun testRreadXmlAsSequence() {
        val bytes= Paths.get("src/test/resources/test.xml").readBytes()
        val items: List<XmlElementDto> = readXmlAsSequence(ByteArrayInputStream(bytes)).toList()
        items.forEach { (name, data) ->
            println("$name : $data")
        }
        assertTrue(items.contains(XmlElementDto("ICO", "03408086")))
        assertTrue(items.contains(XmlElementDto("DIC", "CZ03408086")))
        assertTrue(items.contains(XmlElementDto("OF", "Urban & Hejduk s.r.o.")))

    }
}
