package cz.petrbalat.klib.spring.nats.streaming

import io.nats.streaming.protobuf.StartPosition

/**
 * Annotation for method
 */
@Target(AnnotationTarget.FUNCTION)
@Retention
annotation class NatsStreamingListener(val subject: String,

                                       /**
                                        * if empty it wont be fill
                                        */
                                       val durableName: String,
                                       val startAt: StartPosition = StartPosition.LastReceived,
                                       val manualAcks: Boolean = true,
                                       val autoAck: Boolean = true,
                                       val queue: String = "")
