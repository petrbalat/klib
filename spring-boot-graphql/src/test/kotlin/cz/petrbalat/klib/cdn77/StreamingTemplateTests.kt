package cz.petrbalat.klib.cdn77

import cz.petrbalat.klib.spring.nats.streaming.NatsStreamingTemplate
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.Ignore
import kotlin.test.Test

//@SpringBootTest
@Ignore
class StreamingTemplateTests {

	@Autowired
	private lateinit var streamTemplate: NatsStreamingTemplate

	@Test
	fun testPublishStream1() {
		streamTemplate.publish("test", TestDto("Hello streaming"))
		Thread.sleep(1000)
	}


}
