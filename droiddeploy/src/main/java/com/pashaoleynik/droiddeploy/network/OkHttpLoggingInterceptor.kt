package com.pashaoleynik.droiddeploy.network

import com.pashaoleynik.droiddeploy.logs.Logger
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.http.promisesBody
import okio.Buffer
import java.nio.charset.Charset

internal class OkHttpLoggingInterceptor(
    private val logger: Logger
) : Interceptor {

    companion object {
        private const val TAG = "OkHttp"
        private val UTF8 = Charset.forName("UTF-8")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Log request
        logger.d(TAG, "--> ${request.method} ${request.url}")

        // Log headers (redact Authorization)
        request.headers.forEach { (name, value) ->
            val logValue = if (name.equals("Authorization", ignoreCase = true)) {
                "[REDACTED]"
            } else {
                value
            }
            logger.d(TAG, "$name: $logValue")
        }

        // Log request body if present
        request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            val contentType = body.contentType()
            val charset = contentType?.charset(UTF8) ?: UTF8
            if (buffer.size > 0) {
                logger.d(TAG, "Body: ${buffer.readString(charset)}")
            }
        }

        logger.d(TAG, "--> END ${request.method}")

        // Proceed with request
        val startTime = System.currentTimeMillis()
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            logger.e(TAG, "<-- HTTP FAILED: ${e.message}", e)
            throw e
        }
        val duration = System.currentTimeMillis() - startTime

        // Log response
        logger.d(TAG, "<-- ${response.code} ${response.message} ${request.url} (${duration}ms)")

        response.headers.forEach { (name, value) ->
            logger.d(TAG, "$name: $value")
        }

        // Log response body if present
        if (response.promisesBody()) {
            val source = response.body?.source()
            source?.request(Long.MAX_VALUE)
            val buffer = source?.buffer

            val contentType = response.body?.contentType()
            val charset = contentType?.charset(UTF8) ?: UTF8

            if (buffer != null && buffer.size > 0) {
                val bodyString = buffer.clone().readString(charset)
                logger.d(TAG, "Body: $bodyString")
            }
        }

        logger.d(TAG, "<-- END HTTP")

        return response
    }
}
