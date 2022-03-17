package cz.petrbalat.klib.spring.webflux

import org.springframework.http.codec.multipart.FilePart
import reactor.core.publisher.Mono
import java.io.InputStream
import java.io.SequenceInputStream

fun FilePart.nameAndStream(): Pair<String, Mono<InputStream>> {
    val inputStream: Mono<InputStream> = content().reduce(ZeroInputStream) { s: InputStream, d -> SequenceInputStream(s, d.asInputStream()) }
    return filename() to inputStream
}

object ZeroInputStream : InputStream() {
    override fun read() = -1
}
