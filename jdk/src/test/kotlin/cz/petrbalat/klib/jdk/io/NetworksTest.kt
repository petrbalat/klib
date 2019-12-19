package cz.petrbalat.klib.jdk.cz.petrbalat.klib.jdk.io

import cz.petrbalat.klib.jdk.io.checkUrl
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Created by Petr on 29. 4. 2015.
 */
class NetworksTest {

    @Test
    fun testCheckUrl() {
        assertFalse(checkUrl("127.0.0.1", 8080))
    }
}
