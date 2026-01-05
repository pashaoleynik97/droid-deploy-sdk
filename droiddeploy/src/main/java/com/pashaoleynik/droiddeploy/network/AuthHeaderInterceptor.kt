package com.pashaoleynik.droiddeploy.network

import okhttp3.Interceptor
import okhttp3.Response

internal class AuthHeaderInterceptor(
    private val tokenStore: InMemoryTokenStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Don't add auth header for auth endpoints
        if (originalRequest.url.encodedPath.contains("/api/v1/auth/")) {
            return chain.proceed(originalRequest)
        }

        val token = tokenStore.getToken()
        if (token.isNullOrEmpty()) {
            return chain.proceed(originalRequest)
        }

        val newRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(newRequest)
    }
}
