package cz.petrbalat.klib.spring.mvc

import cz.petrbalat.klib.jdk.string.emptyToNull
import cz.petrbalat.klib.jdk.tryOrNull
import java.net.URL
import javax.servlet.http.HttpServletRequest

/**
 * vrátí aktuální url
 */
val HttpServletRequest.url: String
    get() = servletPath.emptyToNull() ?: pathInfo.orEmpty()

/**
 * vrátí ip adresu clienta
 */
val HttpServletRequest.clientIp: String
    get() = getHeader("X-Real-IP") //pokud to jde z nginx
            ?: remoteAddr


val HttpServletRequest.ajax: Boolean get() = getHeader("X-Requested-With") == "XMLHttpRequest"

val HttpServletRequest.headerMap: Map<String,List<String>> get() = headerNames.toList().associateWith {
    getHeaders(it)?.toList().orEmpty()
}

val HttpServletRequest.referrer: URL? get() = getHeader("Referer")?.let {
    tryOrNull {
        URL(it)
    }
}
