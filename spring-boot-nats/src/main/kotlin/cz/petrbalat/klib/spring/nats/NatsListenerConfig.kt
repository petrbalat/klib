package cz.petrbalat.klib.spring.nats

import com.fasterxml.jackson.databind.ObjectMapper
import io.nats.client.Connection
import io.nats.client.ConnectionListener
import io.nats.client.ConnectionListener.Events
import io.nats.client.Dispatcher
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


/**
 * @author Petr Balat
 *
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Connection::class)
@kotlin.ExperimentalStdlibApi
class NatsListenerConfig(private val connection: Connection,
                         private val mapper: ObjectMapper) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnMissingBean
    fun template(): NatsTemplate = MapperNatsTemplate(connection, mapper)

    @Configuration(proxyBeanMethods = false)
    class SubscribeNatsConfig(context: ApplicationContext, connection: Connection, mapper: ObjectMapper) {
        private val logger = LoggerFactory.getLogger(javaClass)

        init {
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
                        val result: Any? = method.invokeMessage(message, bean, mapper, logger)

                        if (annotation.reply) {
                            val replyBody: ByteArray = mapper.writeValueAsBytes(result)
                            connection.publish(message.replyTo, replyBody)
                        }
                    }
                    if (queue == null) {
                        logger.info("Nats subscribe on subject $subject")
                        val disp = dispatcher.subscribe(subject)
                    } else {
                        logger.info("Nats subscribe on subject $subject, queue $queue")
                        val disp = dispatcher.subscribe(subject, queue)
                    }
                }
            }
        }
    }

}

