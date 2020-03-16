package cz.petrbalat.klib.spring.nats.streaming

import com.fasterxml.jackson.databind.ObjectMapper
import io.nats.streaming.AckHandler
import io.nats.streaming.StreamingConnection

/**
 * @author Petr Balat
 */
class MapperNatsStreamingTemplate(private val connection: StreamingConnection,
                                  private val mapper: ObjectMapper): NatsStreamingTemplate {
    override fun publish(subject: String, body: Any) {
        val bytes = mapper.writeValueAsBytes(body)
        connection.publish(subject, bytes)
    }

    override fun publish(subject: String, body: Any, ackHandler: AckHandler): String {
        val bytes = mapper.writeValueAsBytes(body)
        return connection.publish(subject, bytes, ackHandler)
    }
}
