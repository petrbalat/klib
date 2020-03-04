package cz.petrbalat.klib.spring.nats.streaming

import com.fasterxml.jackson.databind.ObjectMapper
import cz.petrbalat.klib.spring.nats.*
import io.nats.client.Connection
import io.nats.streaming.Options
import io.nats.streaming.StreamingConnection
import io.nats.streaming.StreamingConnectionFactory
import io.nats.streaming.SubscriptionOptions
import io.nats.streaming.protobuf.StartPosition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(StreamingConnection::class)
@ConditionalOnProperty("clusterId", prefix = NATS_PREFIX)
@AutoConfigureAfter(NatsListenerConfig::class)
@kotlin.ExperimentalStdlibApi
class NatsStreamingListenerConfig(private val mapper: ObjectMapper) : ApplicationContextAware {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnMissingBean
    fun streaming(connection: Connection,
                  @Value(NATS_CLUSTER_ID) clusterId: String,
                  @Value(NATS_CLIENT_ID) clientId: String
    ): StreamingConnection {
        val options = Options.Builder()
                .clusterId(clusterId)
                .clientId(clientId)
                .natsConn(connection)
                .build()
        return StreamingConnectionFactory(options).createConnection()
    }

    @Bean
    @ConditionalOnMissingBean
    fun streamTemplate(connection: StreamingConnection): NatsStreamingTemplate
            = MapperNatsStreamingTemplate(connection, mapper)

    override fun setApplicationContext(context: ApplicationContext) {
        val natsBeans: Map<String, Any> = context.getBeansWithAnnotation(NatsComponent::class.java)
        logger.info("Found ${natsBeans.size} nats streaming beans")

        val connection: StreamingConnection = context.getBean()

        natsBeans.forEach { (name: String, bean: Any) ->
            val methos = bean.javaClass.methods.mapNotNull {
                val ann = it.declaredAnnotations.firstOrNull {
                    it.annotationClass == NatsStreamingListener::class
                } as? NatsStreamingListener ?: return@mapNotNull null

                ann to it
            }
            if (methos.isEmpty()) {
                logger.warn("Not found method with annotation ${NatsStreamingListener::class.simpleName} in bean name $name")
            }
            methos.forEach { (annotation, method) ->
                logger.info("Stream listen in bean $name on method ${method.name}")

                val options: SubscriptionOptions = SubscriptionOptions.Builder()
                        .let {
                            val durableName = annotation.durableName.takeIf { it.isNotBlank() }
                            if (durableName != null) it.durableName(durableName)
                            else it
                        }
                        .let {
                            if (annotation.manualAcks) it.manualAcks()
                            else it
                        }
                        .let {
                            when (annotation.startAt) {
                                StartPosition.LastReceived -> it.startWithLastReceived()
                                StartPosition.First -> it.deliverAllAvailable()
                                else -> it
                            }
                        }
                        .build()
                val queue: String? = annotation.queue.takeIf { it.isNotBlank() }
                val sub = connection.subscribe(annotation.subject, queue, { message ->
                    try {
                        method.invokeMessage(message, bean, mapper)
                        if (annotation.manualAcks && annotation.autoAck) {
                            message.ack()
                        }
                    } catch (th: Throwable) {
                        logger.error("Exception durink invoke nats listener in bean $name, method $name", th)
                        throw th
                    }
                }, options)
            }
        }
    }

}

