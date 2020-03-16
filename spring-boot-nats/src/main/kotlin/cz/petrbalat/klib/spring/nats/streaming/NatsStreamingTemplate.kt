package cz.petrbalat.klib.spring.nats.streaming

import io.nats.streaming.AckHandler

/**
 *  Serialize to json a send to nats streaming
 *  @author Petr Balat
 */
interface NatsStreamingTemplate {

    /**
     * serialize to json a send @see
     */
    fun publish(subject: String, body: Any)

    /**
     * serialize to json a send @see
     */
    fun publish(subject: String, body: Any, ackHandler: AckHandler): String
}
