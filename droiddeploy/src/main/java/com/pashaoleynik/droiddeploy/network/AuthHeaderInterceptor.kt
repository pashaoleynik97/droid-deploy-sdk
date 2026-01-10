package com.pashaoleynik.droiddeploy.network

import com.pashaoleynik.droiddeploy.logs.Logger
import okhttp3.Interceptor
import okhttp3.Response

internal class AuthHeaderInterceptor(
    private val tokenStore: InMemoryTokenStore,
    private val logger: Logger
) : Interceptor {

    companion object {
        private const val TAG = "AuthHeaderInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val url = originalRequest
            .url
            .newBuilder()
            .build()

        // Don't add auth header for auth endpoints
        if (url.encodedPath.contains("/api/v1/auth/")) {
            return chain.proceed(originalRequest)
        }

        val token = tokenStore.getToken()
        if (token.isNullOrEmpty()) {
            logger.w(TAG, "No token available, proceeding without Authorization header")
            return chain.proceed(originalRequest)
        }

        logger.d(TAG, "Adding Authorization header with token (length: ${token.length})")

        val newRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .url(url)
            .build()

        logger.d(TAG, "Request has Authorization header: ${newRequest.header("Authorization") != null}")

        return chain.proceed(newRequest)
    }
}
