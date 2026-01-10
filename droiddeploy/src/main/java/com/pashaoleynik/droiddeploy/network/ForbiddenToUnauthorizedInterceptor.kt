package com.pashaoleynik.droiddeploy.network

import com.pashaoleynik.droiddeploy.logs.Logger
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Temporary interceptor that converts 403 Forbidden responses to 401 Unauthorized.
 * This allows OkHttp's Authenticator to trigger and fetch/refresh the token.
 *
 * IMPORTANT: This MUST be added as a network interceptor (addNetworkInterceptor)
 * so that the conversion happens BEFORE OkHttp's internal RetryAndFollowUpInterceptor
 * checks the response code for authentication.
 *
 * TODO: Remove this interceptor once the server is fixed to return 401 for unauthenticated requests.
 */
internal class ForbiddenToUnauthorizedInterceptor(
    private val logger: Logger
) : Interceptor {

    companion object {
        private const val TAG = "ForbiddenToUnauthorizedInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // Don't convert 403 to 401 if this is already a retry attempt
        // (identified by the X-DroidDeploy-Retry header)
        if (request.header("X-DroidDeploy-Retry") != null) {
            logger.d(TAG, "Skipping 403->401 conversion for retry request")
            return response
        }

        // Convert 403 to 401 to trigger the Authenticator
        if (response.code == 403) {
            logger.d(TAG, "Converting 403 Forbidden to 401 Unauthorized to trigger authentication")

            // Create a new response with 401 code
            return response.newBuilder()
                .code(401)
                .message("Unauthorized")
                .body("".toResponseBody(response.body?.contentType()))
                .build()
        }

        return response
    }
}
