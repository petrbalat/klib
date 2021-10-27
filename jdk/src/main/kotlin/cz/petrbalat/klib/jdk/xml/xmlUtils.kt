package cz.petrbalat.klib.jdk.xml

import java.io.InputStream
import javax.xml.stream.XMLInputFactory

/**
 * read xml by StAX as Sequence of XmlElementDto
 */
fun readXmlAsSequence(stream: InputStream): Sequence<XmlElementDto>  {
    val xmlInputFactory: XMLInputFactory = XMLInputFactory.newInstance()
    val xmlEventReader = xmlInputFactory.createXMLEventReader(stream)

    return generateSequence {
        while (xmlEventReader.hasNext()) {
            val xmlEvent = xmlEventReader.nextEvent()
            if (xmlEvent.isStartElement) {
                val startElement = xmlEvent.asStartElement()
                val name: String = startElement.name?.localPart?.toString().orEmpty()
                val data: String? = xmlEventReader.nextEvent()?.asCharacters()?.toString()
                return@generateSequence XmlElementDto(name = name, data = data)
            }
        }

        return@generateSequence null
    }
}

data class XmlElementDto(val name: String, val data: String?)
