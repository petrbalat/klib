package cz.petrbalat.klib.jdk.http

import kotlinx.coroutines.future.await
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * User: Petr Balat
 * Date: 5.11.2014
 */

val httpClient: HttpClient by lazy {
    HttpClient.newBuilder().build()
}

suspend fun fetchStream(uri: URI, client: HttpClient = httpClient): HttpResponse<InputStream> {
    val request: HttpRequest = HttpRequest.newBuilder().uri(uri).build()
    val bodyHandler = HttpResponse.BodyHandlers.ofInputStream()
    return client.sendAsync(request, bodyHandler).await()
}

suspend fun URI.fetchStream(): InputStream = if (this.toString().startsWith("file:/")) File(this).inputStream()
    else fetchStream(this).body()
