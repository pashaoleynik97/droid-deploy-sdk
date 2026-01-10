package com.pashaoleynik.droiddeploy.network

import com.pashaoleynik.droiddeploy.logs.Logger
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Integration test for the OkHttp authentication flow including:
 * - AuthHeaderInterceptor: adds Authorization header when token is available
 * - ApiKeyAuthenticator: fetches new token when receiving 401/403
 * - Request retry with new token
 */
class OkHttpAuthenticationFlowTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var tokenStore: InMemoryTokenStore
    private lateinit var logger: Logger
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var moshi: Moshi

    private val testApiKey = "test-api-key-12345"
    private val testToken = "test-access-token-67890"
    private val testNewToken = "test-new-access-token-99999"

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        tokenStore = InMemoryTokenStore()
        logger = Logger(debugLogsEnabled = true, debugLogsListener = { log ->
            println("[${log.level}] ${log.tag}: ${log.message}")
        })

        moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        // Build OkHttpClient with the same configuration as production
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthHeaderInterceptor(tokenStore, logger))
            .authenticator(
                ApiKeyAuthenticator(
                    baseUrlProvider = { mockWebServer.url("/") },
                    apiKeyProvider = { testApiKey },
                    tokenStore = tokenStore,
                    logger = logger
                )
            )
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    /**
     * Happy path test:
     * 1. Initial request without token -> 401 response
     * 2. Authenticator fetches new token via /api/v1/auth/apikey
     * 3. Request is retried with new token -> 200 response
     */
    @Test
    fun `test authentication flow - happy path`() = runTest {
        // Arrange: Setup mock responses
        // 1. First request to /test-endpoint returns 401 (no token)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"success":false,"message":"Unauthorized"}""")
        )

        // 2. Auth request to /api/v1/auth/apikey returns new token
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "success": true,
                        "data": {
                            "accessToken": "$testNewToken"
                        }
                    }
                    """.trimIndent()
                )
        )

        // 3. Retry of original request with new token returns 200
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"data":"test-response"}""")
        )

        // Act: Make a request to a protected endpoint
        val request = Request.Builder()
            .url(mockWebServer.url("/api/v1/test-endpoint"))
            .get()
            .build()

        val response = okHttpClient.newCall(request).execute()

        // Assert: Verify the flow
        assertEquals(200, response.code)
        assertEquals(testNewToken, tokenStore.getToken())

        // Verify all 3 requests were made
        assertEquals(3, mockWebServer.requestCount)

        // Verify request 1: initial request without Authorization header
        val request1 = mockWebServer.takeRequest()
        assertEquals("/api/v1/test-endpoint", request1.path)
        assertEquals(null, request1.getHeader("Authorization"))

        // Verify request 2: auth request to get token
        val request2 = mockWebServer.takeRequest()
        assertEquals("/api/v1/auth/apikey", request2.path)
        val requestBody = request2.body.readUtf8()
        assertTrue(requestBody.contains(testApiKey))

        // Verify request 3: retry with new token
        val request3 = mockWebServer.takeRequest()
        assertEquals("/api/v1/test-endpoint", request3.path)
        assertEquals("Bearer $testNewToken", request3.getHeader("Authorization"))
        assertNotNull(request3.getHeader("X-DroidDeploy-Retry"))
    }

    /**
     * Test that requests with existing valid token include Authorization header
     */
    @Test
    fun `test request with existing token includes Authorization header`() = runTest {
        // Arrange: Set token in store
        tokenStore.setToken(testToken)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"data":"test-response"}""")
        )

        // Act: Make a request
        val request = Request.Builder()
            .url(mockWebServer.url("/api/v1/test-endpoint"))
            .get()
            .build()

        val response = okHttpClient.newCall(request).execute()

        // Assert: Verify Authorization header was sent
        assertEquals(200, response.code)
        assertEquals(1, mockWebServer.requestCount)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("Bearer $testToken", recordedRequest.getHeader("Authorization"))
    }

    /**
     * Test that auth endpoints don't get Authorization header
     */
    @Test
    fun `test auth endpoint does not include Authorization header`() = runTest {
        // Arrange: Set token in store
        tokenStore.setToken(testToken)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "success": true,
                        "data": {
                            "accessToken": "$testNewToken"
                        }
                    }
                    """.trimIndent()
                )
        )

        // Act: Make a request to auth endpoint
        val request = Request.Builder()
            .url(mockWebServer.url("/api/v1/auth/apikey"))
            .post(
                """{"apiKey":"$testApiKey"}""".toRequestBody(
                    "application/json".toMediaType()
                )
            )
            .build()

        val response = okHttpClient.newCall(request).execute()

        // Assert: Verify Authorization header was NOT sent
        assertEquals(200, response.code)
        assertEquals(1, mockWebServer.requestCount)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals(null, recordedRequest.getHeader("Authorization"))
    }

    /**
     * Test that authenticator doesn't retry if already retried once
     */
    @Test
    fun `test authenticator does not retry twice`() = runTest {
        // Arrange: Setup mock responses
        // 1. First request returns 401
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"success":false,"message":"Unauthorized"}""")
        )

        // 2. Auth request returns new token
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "success": true,
                        "data": {
                            "accessToken": "$testNewToken"
                        }
                    }
                    """.trimIndent()
                )
        )

        // 3. Retry also returns 401 (token invalid)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"success":false,"message":"Unauthorized"}""")
        )

        // Act: Make a request
        val request = Request.Builder()
            .url(mockWebServer.url("/api/v1/test-endpoint"))
            .get()
            .build()

        val response = okHttpClient.newCall(request).execute()

        // Assert: Should fail with 401 (not retry again)
        assertEquals(401, response.code)

        // Should have made 3 requests total (initial + auth + retry), but NOT a second retry
        assertEquals(3, mockWebServer.requestCount)
    }

    /**
     * Test that 403 response does NOT trigger authentication
     * (OkHttp's Authenticator is only triggered for 401, not 403)
     */
    @Test
    fun `test 403 response does not trigger authentication`() = runTest {
        // Arrange: Setup mock response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"success":false,"message":"Forbidden"}""")
        )

        // Act: Make a request
        val request = Request.Builder()
            .url(mockWebServer.url("/api/v1/test-endpoint"))
            .get()
            .build()

        val response = okHttpClient.newCall(request).execute()

        // Assert: Should return 403 without attempting authentication
        assertEquals(403, response.code)
        assertEquals(1, mockWebServer.requestCount) // Only the initial request
    }

    /**
     * Test that non-401/403 responses don't trigger authentication
     */
    @Test
    fun `test 400 response does not trigger authentication`() = runTest {
        // Arrange: Setup mock response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"success":false,"message":"Bad Request"}""")
        )

        // Act: Make a request
        val request = Request.Builder()
            .url(mockWebServer.url("/api/v1/test-endpoint"))
            .get()
            .build()

        val response = okHttpClient.newCall(request).execute()

        // Assert: Should return 400 without attempting authentication
        assertEquals(400, response.code)
        assertEquals(1, mockWebServer.requestCount) // Only the initial request
    }
}
