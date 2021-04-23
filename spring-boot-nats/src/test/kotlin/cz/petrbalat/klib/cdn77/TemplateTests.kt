package cz.petrbalat.klib.cdn77

import cz.petrbalat.klib.spring.nats.NatsTemplate
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.Ignore
import kotlin.test.Test

//@SpringBootTest
@Ignore
class TemplateTests {

    @Autowired
    private lateinit var template: NatsTemplate

    @Test
    fun testPublish1() {
        template.publish("test.1", TestDto("Hello Peter"))

        Thread.sleep(1000)
    }


}
