package cz.petrbalat.klib.spring.nats

import io.nats.client.Message
import java.time.Duration
import java.util.concurrent.CompletableFuture

interface NatsTemplate {

    fun publish(subject: String, body: Any, replyTo: String? = null)

    suspend fun <T> request(subject: String, body: Any, returnClazz: Class<T>) : T

    fun requestAsync(subject: String, body: Any) : CompletableFuture<Message>

    fun requestSync(subject: String, body: Any, timeout: Duration) : Message
}
