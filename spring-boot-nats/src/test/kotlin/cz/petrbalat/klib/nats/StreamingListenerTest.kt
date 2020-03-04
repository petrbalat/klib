package cz.petrbalat.klib.nats

import cz.petrbalat.klib.spring.nats.NatsComponent
import cz.petrbalat.klib.spring.nats.streaming.NatsStreamingListener
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

@NatsComponent
class StreamingListenerTest  {

    private val logger = LoggerFactory.getLogger(javaClass)

    var counter = 1

    @NatsStreamingListener("test.ex", durableName = "aaa")
    suspend fun onTestStream(data: TestDto) {
        if (data.name.contains("2") && counter < 3) {
            counter++
            throw RuntimeException(data.toString())
        }

        delay(200)

        logger.info("Delivered stream $data")
    }

}

