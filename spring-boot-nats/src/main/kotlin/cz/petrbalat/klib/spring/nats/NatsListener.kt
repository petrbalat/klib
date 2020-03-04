package cz.petrbalat.klib.spring.nats

import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * Annotation for class
 */
@Target(AnnotationTarget.CLASS)
@Retention
@Component
@Lazy(false)
annotation class NatsComponent

/**
 * Annotation for method
 */
@Target(AnnotationTarget.FUNCTION)
@Retention
annotation class NatsListener(val subject: String,

                              /**
                               * When a message is published to the group NATS will deliver it to a one-and-only-one subscriber.
                               */
                              val queue: String = "",
                              val reply: Boolean = false)
