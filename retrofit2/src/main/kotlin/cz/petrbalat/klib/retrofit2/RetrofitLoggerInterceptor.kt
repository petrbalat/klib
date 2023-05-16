package cz.petrbalat.klib.retrofit2

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.Logger

fun OkHttpClient.Builder.addLoggerInterceptor(logger: Logger, level: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BODY): OkHttpClient.Builder {
    return this.addInterceptor(HttpLoggingInterceptor {
        logger.info(it)
    }.apply {
        this.level = level
    })
}
