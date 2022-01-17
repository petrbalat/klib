package cz.petrbalat.klib.jdk.xml

import java.io.InputStream
import javax.xml.stream.XMLEventReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.events.XMLEvent

/**
 * read xml by StAX as Sequence of XmlElementDto
 */
fun readXmlAsSequence(stream: InputStream): Sequence<XmlElementDto> {
    val xmlInputFactory: XMLInputFactory = XMLInputFactory.newInstance()
    val xmlEventReader: XMLEventReader = xmlInputFactory.createXMLEventReader(stream)

    return generateSequence {
        while (xmlEventReader.hasNext()) {
            val xmlEvent: XMLEvent = xmlEventReader.nextEvent()
            if (xmlEvent.isStartElement) {
                val startElement = xmlEvent.asStartElement()
                val name: String = startElement.name?.localPart?.toString().orEmpty()
                val data: String? = xmlEventReader.readCharactersToEndElement()
                return@generateSequence XmlElementDto(name = name, data = data)
            }
        }

        return@generateSequence null
    }
}

private fun XMLEventReader.readCharactersToEndElement(): String? {
    var text: String? = null
    do {
        val event = nextEvent()
        if (event.isCharacters) {
            val readNext = event.asCharacters().toString()
            text = text?.let { "$it$readNext" } ?: readNext
        }
    } while (peek().isCharacters)

    return text
}

data class XmlElementDto(val name: String, val data: String?)
