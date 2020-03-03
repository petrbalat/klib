package cz.petrbalat.klib.spring.nats

import com.fasterxml.jackson.databind.ObjectMapper
import io.nats.client.Connection
import io.nats.client.Message
import kotlinx.coroutines.future.await
import java.time.Duration
import java.util.concurrent.CompletableFuture

class MapperNatsTemplate(private val connection: Connection,
                         private val mapper: ObjectMapper) : NatsTemplate {

    override fun publish(subject: String, body: Any, replyTo: String?) {
        val bytes = mapper.writeValueAsBytes(body)
        connection.publish(subject, replyTo, bytes)
    }

    override suspend fun <T> request(subject: String, body: Any, returnClazz: Class<T>): T {
        val message: Message = requestAsync(subject, body).await()
        return mapper.readValue(message.data, returnClazz)
    }

    override fun requestAsync(subject: String, body: Any): CompletableFuture<Message> {
        val bytes = mapper.writeValueAsBytes(body)
        return connection.request(subject, bytes)
    }

    override fun requestSync(subject: String, body: Any, timeout: Duration): Message {
        val bytes = mapper.writeValueAsBytes(body)
        return connection.request(subject, bytes, timeout)
    }
}
