package cz.petrbalat.klib.nats

import cz.petrbalat.klib.spring.nats.NatsComponent
import cz.petrbalat.klib.spring.nats.streaming.NatsStreamingListener
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

@NatsComponent
class StreamingListenerTest  {

    private val logger = LoggerFactory.getLogger(javaClass)

    @NatsStreamingListener("test", durableName = "aaa", manualAcks = true)
    fun onTestStream(data: TestDto) {
//       throw RuntimeException(data.toString())

        logger.info("Delivered stream $data")
    }

    @NatsStreamingListener("test", durableName = "bbb", manualAcks = true)
    suspend fun onTestStream2(data: TestDto) {
//       throw RuntimeException(data.toString())
        delay(200)

        logger.info("Delivered suspend stream $data")
    }

}

