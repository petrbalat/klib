package cz.petrbalat.klib.retrofit2

import okhttp3.Credentials
import okhttp3.Interceptor

class BasicAuthInterceptor(
    username: String,
    password: String,
    private val headerName: String = "Authorization",
) : Interceptor {

    private val credentials: String = Credentials.basic(username, password)

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request().newBuilder()
            .header(headerName, credentials)
            .build()
        return chain.proceed(request)
    }
}
