package cz.petrbalat.klib.spring.webflux

import org.springframework.web.reactive.function.client.*
import org.springframework.web.reactive.function.client.WebClient.Builder
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import reactor.util.retry.RetryBackoffSpec
import java.time.Duration
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Predicate

/**
 * retry fir WebClient
 * 
 *  WebClient.builder().withRetryableRequests()...
 *
 */
fun Builder.withRetryableRequests(maxAttempts: Long = 3, duraction: Duration = Duration.ofSeconds(2)): Builder =
    this.filter(ExchangeFilterFunction { request, next ->
        val retrySpec = retryBackoffSpec(maxAttempts, duraction)
        next.exchange(request).flatMap { clientResponse ->
            Mono.just(clientResponse)
                .filter { clientResponse.statusCode().isError }
                .flatMap { clientResponse.createException() }
                .flatMap<ClientResponse> { Mono.error(it) }
                .thenReturn(clientResponse)
        }.retryWhen(retrySpec)
    })

fun retryBackoffSpec(maxAttempts: Long, duraction: Duration): RetryBackoffSpec = Retry.backoff(maxAttempts, duraction)
    .filter(Predicate { it is WebClientResponseException })
    .doBeforeRetry(Consumer { })
    .onRetryExhaustedThrow(BiFunction { retryBackoffSpec, retrySignal -> retrySignal?.failure() })