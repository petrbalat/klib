package cz.petrbalat.klib.nats

import cz.petrbalat.klib.spring.nats.streaming.NatsStreamingTemplate
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class StreamingTemplateTests {

	@Autowired
	private lateinit var streamTemplate: NatsStreamingTemplate

	@Test
	fun testPublishStream1() {
		streamTemplate.publish("test", TestDto("Hello streaming"))
		Thread.sleep(1000)
	}


}
