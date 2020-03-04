package cz.petrbalat.klib.nats

import cz.petrbalat.klib.spring.nats.NatsComponent
import cz.petrbalat.klib.spring.nats.NatsListener
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

@NatsComponent
class ListenerTest {

    private val logger = LoggerFactory.getLogger(javaClass)

    @NatsListener("aaa")
    fun onTest(data: TestDto) {
        logger.info("Delivered $data")
    }

    @NatsListener("test.>", reply = true)
    suspend fun onTest2(data: TestDto): TestDto {
        delay(1000)
        logger.info("Delivered suspend $data")

        return data.copy(name = "reply")
    }

}

