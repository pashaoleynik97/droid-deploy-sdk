package com.pashaoleynik.droiddeploy.network

import com.pashaoleynik.droiddeploy.logs.Logger
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

internal class ApiKeyAuthenticator(
    private val baseUrlProvider: () -> HttpUrl,
    private val apiKeyProvider: () -> String,
    private val tokenStore: InMemoryTokenStore,
    private val logger: Logger
) : Authenticator {

    companion object {
        private const val TAG = "ApiKeyAuthenticator"
        private const val RETRY_HEADER = "X-DroidDeploy-Retry"
    }

    private val mutex = Mutex()
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Don't retry if this is an auth endpoint
        if (response.request.url.encodedPath.contains("/api/v1/auth/")) {
            logger.d(TAG, "Not retrying auth endpoint")
            return null
        }

        // Don't retry if already retried
        if (response.request.header(RETRY_HEADER) != null) {
            logger.d(TAG, "Already retried, giving up")
            return null
        }

        // Only handle 401
        if (response.code != 401) {
            return null
        }

        logger.d(TAG, "Got 401, attempting to refresh token")

        // Use runBlocking to make the coroutine synchronous
        // This is acceptable here since Authenticator interface is synchronous
        return runBlocking {
            mutex.withLock {
                try {
                    val apiKey = apiKeyProvider()
                    if (apiKey.isEmpty()) {
                        logger.e(TAG, "API key is empty, cannot authenticate")
                        return@runBlocking null
                    }

                    // Attempt to get new token
                    val newToken = fetchNewToken(apiKey)
                    if (newToken != null) {
                        tokenStore.setToken(newToken)
                        logger.d(TAG, "Successfully refreshed token")

                        // Retry the original request with new token
                        return@runBlocking response.request.newBuilder()
                            .header("Authorization", "Bearer $newToken")
                            .header(RETRY_HEADER, "1")
                            .build()
                    } else {
                        logger.e(TAG, "Failed to refresh token")
                        return@runBlocking null
                    }
                } catch (e: Exception) {
                    logger.e(TAG, "Error during token refresh", e)
                    return@runBlocking null
                }
            }
        }
    }

    private fun fetchNewToken(apiKey: String): String? {
        return try {
            val client = OkHttpClient()
            val baseUrl = baseUrlProvider()

            val requestBody = ApiKeyLoginRequest(apiKey)
            val adapter = moshi.adapter(ApiKeyLoginRequest::class.java)
            val json = adapter.toJson(requestBody)

            val url = baseUrl.newBuilder()
                .addPathSegments("api/v1/auth/apikey")
                .build()

            val request = Request.Builder()
                .url(url)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val responseAdapter = moshi.adapter<RestResponse<ApiTokenDto>>(
                        com.squareup.moshi.Types.newParameterizedType(
                            RestResponse::class.java,
                            ApiTokenDto::class.java
                        )
                    )
                    val parsed = responseAdapter.fromJson(responseBody)
                    parsed?.data?.accessToken
                } else {
                    null
                }
            } else {
                logger.e(TAG, "Token refresh failed with code: ${response.code}")
                null
            }
        } catch (e: Exception) {
            logger.e(TAG, "Exception during token fetch", e)
            null
        }
    }
}
