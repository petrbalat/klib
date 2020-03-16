package cz.petrbalat.klib.spring.nats

import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 *
 * @author Petr Balat
 */
interface NatsTemplate {

    fun publish(subject: String, body: Any, replyTo: String? = null)

    suspend fun <T> request(subject: String, body: Any, returnClazz: Class<T>) : T

    fun <T> requestAsync(subject: String, body: Any, returnClazz: Class<T>) : CompletableFuture<T>

    fun <T> requestSync(subject: String, body: Any, timeout: Duration, returnClazz: Class<T>) : T
}
