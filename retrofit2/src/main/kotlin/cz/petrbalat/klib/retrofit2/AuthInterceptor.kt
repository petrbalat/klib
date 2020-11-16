package cz.petrbalat.klib.retrofit2

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val valueProvider: () -> String?,
    private val accept: String? = "application/json; charset=utf-8",
    private val headerName: String = "authorization"
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var builder = chain.request().newBuilder()
        if (accept != null) {
            builder = builder.addHeader("accept", accept)
        }
        val value: String? = valueProvider()
        if (value != null) {
            builder = builder.addHeader(headerName, value)
        }
        return chain.proceed(builder.build())
    }
}
