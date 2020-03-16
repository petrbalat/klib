package cz.petrbalat.klib.spring.nats

import com.fasterxml.jackson.databind.ObjectMapper
import io.nats.client.Connection
import kotlinx.coroutines.future.await
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * @author Petr Balat
 *
 */
class MapperNatsTemplate(private val connection: Connection,
                         private val mapper: ObjectMapper) : NatsTemplate {

    override fun publish(subject: String, body: Any, replyTo: String?) {
        val bytes = mapper.writeValueAsBytes(body)
        connection.publish(subject, replyTo, bytes)
    }

    override suspend fun <T> request(subject: String, body: Any, returnClazz: Class<T>): T {
        return requestAsync(subject, body, returnClazz).await()
    }

    override fun <T> requestAsync(subject: String, body: Any, returnClazz: Class<T>): CompletableFuture<T> {
        val bytes = mapper.writeValueAsBytes(body)
        return connection.request(subject, bytes).thenApply {
            mapper.readValue(it.data, returnClazz)
        }
    }

    override fun <T> requestSync(subject: String, body: Any, timeout: Duration, returnClazz: Class<T>): T {
        val bytes = mapper.writeValueAsBytes(body)
        val message = connection.request(subject, bytes, timeout)
        return mapper.readValue(message.data, returnClazz)
    }
}
