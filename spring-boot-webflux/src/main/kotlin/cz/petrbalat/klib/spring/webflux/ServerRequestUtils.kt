import cz.petrbalat.klib.jdk.tryOn
import org.springframework.web.reactive.function.server.ServerRequest
import java.net.URL

/**
 * vrátí aktuální url
 */
@Deprecated("use path()", replaceWith = ReplaceWith("path()"))
val ServerRequest.url: String
    get() = path()

/**
 * vrátí ip adresu clienta
 */
val ServerRequest.clientIp: String?
    get() = headers().header("X-Real-IP").firstOrNull() //pokud to jde z nginx
        ?: headers().host()?.address?.hostAddress


val ServerRequest.ajax: Boolean
    get() = headers().header("X-Requested-With").any {
        it == "XMLHttpRequest"
    }

val ServerRequest.headerMap: Map<String, List<String>>
    get() {
        val headers: ServerRequest.Headers = headers()
        return headers.asHttpHeaders().mapValues { headers.header(it.key) }
    }

val ServerRequest.referrer: URL?
    get() {
        val url = headers().header("Referer").firstOrNull() ?: return null
        return tryOn {
            URL(url)
        }
    }
