package cz.petrbalat.klib.nats

import cz.petrbalat.klib.spring.nats.NatsTemplate
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class TemplateTests {

    @Autowired
    private lateinit var template: NatsTemplate

    @Test
    fun testPublish1() {
        template.publish("test.1", TestDto("Hello Peter"))

        Thread.sleep(1000)
    }


}
