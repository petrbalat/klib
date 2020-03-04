package cz.petrbalat.klib.nats

import cz.petrbalat.klib.spring.nats.NatsTemplate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class TemplateTests {

    @Autowired
    private lateinit var template: NatsTemplate

    @Test
    fun testPublish1() = runBlocking {
        template.publish("aaa", TestDto("Hello Peter"))

        withTimeout(2000) {
            val result: TestDto = template.request("test.echo", TestDto("Hello Peter"), TestDto::class.java)
            println(result)
        }
    }


}
