package cz.petrbalat.klib.spring.nats

import com.fasterxml.jackson.databind.ObjectMapper
import io.nats.client.Connection
import io.nats.client.Dispatcher
import io.nats.client.Message
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration
import java.util.concurrent.CompletableFuture

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Connection::class)
@kotlin.ExperimentalStdlibApi
class NatsListenerConfig(private val connection: Connection,
                         private val mapper: ObjectMapper) : ApplicationContextAware {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnMissingBean
    fun template(): NatsTemplate = MapperNatsTemplate(connection, mapper)

    override fun setApplicationContext(context: ApplicationContext) {
        val natsBeans: Map<String, Any> = context.getBeansWithAnnotation(NatsComponent::class.java)
        logger.info("Found ${natsBeans.size} nats beans")

        natsBeans.forEach { (name: String, bean: Any) ->
            val methos = bean.javaClass.methods.mapNotNull {
                val ann = it.declaredAnnotations.firstOrNull {
                    it.annotationClass == NatsListener::class
                } as? NatsListener ?: return@mapNotNull null

                ann to it
            }
            if (methos.isEmpty()) {
                logger.warn("Not found method with annotation ${NatsListener::class.simpleName} in bean name $name")
            }
            methos.forEach { (annotation, method) ->
                logger.info("Nats listen in bean $name on method ${method.name}")

                val subject: String = annotation.subject
                val queue: String? = annotation.queue.takeIf { it.isNotBlank() }
                val dispatcher: Dispatcher = connection.createDispatcher { message ->
                    var reply = false
                    var result: Any? = null
                    try {
                        result = method.invokeMessage(message, bean, mapper)
                        reply = true
                    } catch (th: Throwable) {
                        logger.error("Exception durink invoke nats listener in bean $name, method $name", th)
                        throw th
                    }

                    try {
                        if (reply && annotation.reply) {
                            val replyBody: ByteArray = mapper.writeValueAsBytes(result)
                            connection.publish(message.replyTo, replyBody)
                        }
                    } catch (th: Throwable) {
                        logger.error("Exception durink reply in bean $name, method $name", th)
                        throw th
                    }
                }
                if (queue == null) {
                    dispatcher.subscribe(subject)
                } else {
                    dispatcher.subscribe(subject, queue)
                }
            }
        }
    }

}

