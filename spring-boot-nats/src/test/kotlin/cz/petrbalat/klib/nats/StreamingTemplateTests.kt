package cz.petrbalat.klib.nats

import cz.petrbalat.klib.spring.nats.streaming.NatsStreamingTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.*

@SpringBootTest
class StreamingTemplateTests {

	@Autowired
	private lateinit var streamTemplate: NatsStreamingTemplate

	@Test
	fun testPublishStream1() {
//		streamTemplate.publish("test.ex", TestDto("Hello streaming 1"))
//		streamTemplate.publish("test.ex", TestDto("Hello streaming 2"))
//		streamTemplate.publish("test.ex", TestDto("Hello streaming 3"))
		Thread.sleep(65_000)
	}


}
