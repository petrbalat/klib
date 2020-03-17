package cz.petrbalat.klib.jdk.delegate

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Created by Petr Balat
 */
class CacheDelegateTest {

    private var count = 1

    var cacheProperty: String by cacheDelegate(Duration.ofSeconds(1)) {
        (count++).toString()
    }

    @Test
    fun testGet() {
        assertEquals("1", cacheProperty)
        Thread.sleep(500)
        assertEquals("1", cacheProperty)
        Thread.sleep(500)//po  sekundách se inicializuje znovu
        assertEquals("2", cacheProperty)
        assertEquals("2", cacheProperty)
        assertEquals("2", cacheProperty)
        Thread.sleep(1000)//po sekundách se inicializuje znovu
        assertEquals("3", cacheProperty)
        assertEquals("3", cacheProperty)
        cacheProperty = ""
        assertEquals("4", cacheProperty)
    }

}
