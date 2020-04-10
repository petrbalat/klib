package cz.petrbalat.klib.nats

import cz.petrbalat.klib.spring.nats.NatsComponent
import cz.petrbalat.klib.spring.nats.NatsListener
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

@NatsComponent
class Listener {

    private val logger = LoggerFactory.getLogger(javaClass)

    @NatsListener("test.>")
    fun onTest(data: TestDto) {
        logger.info("Delivered $data")
    }

    @NatsListener("test.>")
    suspend fun onTest2(data: TestDto) {
        delay(1000)
        logger.info("Delivered suspend $data")
    }

}

