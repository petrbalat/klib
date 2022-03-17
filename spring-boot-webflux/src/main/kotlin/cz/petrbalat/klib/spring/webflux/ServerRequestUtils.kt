import org.springframework.http.server.reactive.ServerHttpRequest

/**
 * vrátí ip adresu clienta
 */
val ServerHttpRequest.clientIp: String?
    get() = remoteAddress?.address?.hostAddress


val Map<String, List<String>>.ajax: Boolean
    get() = this["X-Requested-With"]?.any {
        it == "XMLHttpRequest"
    } == true
