package cz.petrbalat.klib.jdk.ssl

import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

object EmptyX509TrustManager : X509TrustManager {
    override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {
    }

    override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {
    }

    override fun getAcceptedIssuers(): Array<out X509Certificate>? = emptyArray()
}

object EmptyHostnameVerifier : HostnameVerifier {
    override fun verify(p0: String?, p1: SSLSession?): Boolean = true
}
