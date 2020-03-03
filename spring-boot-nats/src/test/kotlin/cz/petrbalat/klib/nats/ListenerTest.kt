package cz.petrbalat.klib.nats

import cz.petrbalat.klib.spring.nats.NatsComponent
import cz.petrbalat.klib.spring.nats.NatsListener
import org.slf4j.LoggerFactory

@NatsComponent
class ListenerTest {

    private val logger = LoggerFactory.getLogger(javaClass)

    @NatsListener("test.>")
    fun onTest(data: TestDto) {
        logger.info("Delivered $data")
    }

}

