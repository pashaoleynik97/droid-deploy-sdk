package com.pashaoleynik.droiddeploy.network

import okhttp3.Interceptor
import okhttp3.Response

internal class HostSelectionInterceptor(
    private val hostStore: HostStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val currentHost = hostStore.get()

        val newUrl = originalRequest.url.newBuilder()
            .scheme(currentHost.scheme)
            .host(currentHost.host)
            .port(currentHost.port)
            .build()

        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }
}
