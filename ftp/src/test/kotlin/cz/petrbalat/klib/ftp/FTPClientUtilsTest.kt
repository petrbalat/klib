package cz.petrbalat.klib.ftp

import org.apache.commons.net.ftp.FTPClient
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class FTPClientUtilsTest {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Test
    fun testUse() {
        val names = FTPClient().use(logger, hostname = "push-x.cdn77.com", user = "user_x", password = "xxx") {
            it.changeWorkingDirectory("/www")
            it.listNames()
        }.orEmpty()
        assertEquals(1, names.size)
    }

}
