package cz.petrbalat.klib.jdk.io

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * vrací true pokud se lze připojit k serveru
 */
fun checkUrl(host: String, port: Int, timeout: Int = 1000): Boolean {
    try {
        Socket().use {
            it.connect(InetSocketAddress(host, port), timeout)
            return true
        }
    } catch (ex: IOException) {
        return false // Either timeout or unreachable or failed DNS lookup.
    }
}
