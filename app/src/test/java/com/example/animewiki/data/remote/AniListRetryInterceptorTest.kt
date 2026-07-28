package com.example.animewiki.data.remote

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import com.apollographql.mockserver.MockResponse
import com.apollographql.mockserver.MockServer
import com.example.animewiki.graphql.GenreCollectionQuery
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class AniListRetryInterceptorTest {

    @Test
    fun `Apollo current query body is recognized and preserved across retries`() =
        runTest {
            val server = MockServer.Builder().build()
            val observedRequests = mutableListOf<Request>()
            val sleeps = mutableListOf<Long>()
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(
                    AniListRetryInterceptor(
                        sleep = sleeps::add,
                        jitter = { 0 }
                    )
                )
                .addInterceptor { chain ->
                    observedRequests += chain.request()
                    chain.proceed(chain.request())
                }
                .build()
            val client = ApolloClient.Builder()
                .serverUrl(server.url())
                .okHttpClient(okHttpClient)
                .build()
            try {
                server.enqueue(
                    MockResponse.Builder()
                        .statusCode(503)
                        .body("unavailable")
                        .build()
                )
                server.enqueue(
                    MockResponse.Builder()
                        .body("""{"data":{"genres":["Action"]}}""")
                        .build()
                )

                client.query(GenreCollectionQuery()).execute()
                val request = observedRequests.first()
                val body = request.bodyUtf8()
                val recordedRequests = List(2) { server.takeRequest() }

                assertEquals("POST", request.method)
                assertFalse(requireNotNull(request.body).isDuplex())
                assertFalse(request.body!!.isOneShot())
                assertTrue(body.contains(""""query":"query GenreCollection { genres: GenreCollection }""""))
                assertEquals(listOf(1_000L), sleeps)
                assertEquals(2, observedRequests.size)
                assertTrue(observedRequests.all { it === request })
                assertTrue(observedRequests.all { it.bodyUtf8() == body })
                assertTrue(recordedRequests.all { it.body.utf8() == body })
            } finally {
                client.close()
                server.close()
            }
        }

    @Test
    fun `200 returns after one attempt without sleeping`() {
        val scriptedResponse = testResponse(200)
        val chain = ScriptedChain(scriptedResponse.response)
        val sleeps = mutableListOf<Long>()
        val interceptor = AniListRetryInterceptor(sleep = sleeps::add)

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(1, chain.attempts)
        assertEquals(emptyList<Long>(), sleeps)
        assertFalse(scriptedResponse.body.isClosed)
        response.close()
    }

    @Test
    fun `404 returns after one attempt without sleeping`() {
        val scriptedResponse = testResponse(404)
        val chain = ScriptedChain(scriptedResponse.response)
        val sleeps = mutableListOf<Long>()
        val interceptor = AniListRetryInterceptor(sleep = sleeps::add)

        val response = interceptor.intercept(chain)

        assertEquals(404, response.code)
        assertEquals(1, chain.attempts)
        assertEquals(emptyList<Long>(), sleeps)
        assertFalse(scriptedResponse.body.isClosed)
        response.close()
    }

    @Test
    fun `mutation response is never retried and its request body is preserved`() {
        val mutationPayload =
            """{"operationName":"UpdateAnime","variables":{},"query":"mutation UpdateAnime { updateAnime }"}"""
        val mutationRequest = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(mutationPayload.toRequestBody(APPLICATION_JSON))
            .build()
        val first = testResponse(503)
        val second = testResponse(200)
        val chain = ScriptedChain(first.response, second.response).apply {
            request = mutationRequest
        }
        val sleeps = mutableListOf<Long>()
        val interceptor = AniListRetryInterceptor(sleep = sleeps::add)

        val response = interceptor.intercept(chain)

        assertEquals(503, response.code)
        assertEquals(1, chain.attempts)
        assertEquals(emptyList<Long>(), sleeps)
        assertFalse(first.body.isClosed)
        assertSame(mutationRequest.body, chain.proceededRequests.single().body)
        assertEquals(mutationPayload, chain.proceededRequests.single().bodyUtf8())
        response.close()
    }

    @Test
    fun `503 closes the intermediate response and retries with jittered fallback`() {
        val first = testResponse(503)
        val second = testResponse(200)
        val chain = ScriptedChain(first.response, second.response)
        val sleeps = mutableListOf<Long>()
        val interceptor = AniListRetryInterceptor(
            sleep = sleeps::add,
            jitter = { bound -> bound / 4 }
        )

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(2, chain.attempts)
        assertEquals(listOf(1_250L), sleeps)
        assertTrue(first.body.isClosed)
        assertFalse(second.body.isClosed)
        assertTrue(chain.proceededRequests.all { it === TEST_REQUEST })
        assertTrue(chain.proceededRequests.all { it.bodyUtf8() == APOLLO_QUERY_PAYLOAD })
        response.close()
    }

    @Test
    fun `four 503 responses return the fourth response after three retries`() {
        val responses = List(4) { testResponse(503) }
        val chain = ScriptedChain(*responses.map { it.response }.toTypedArray())
        val sleeps = mutableListOf<Long>()
        val interceptor = AniListRetryInterceptor(
            maxRetries = 3,
            sleep = sleeps::add,
            jitter = { 0 }
        )

        val response = interceptor.intercept(chain)

        assertEquals(503, response.code)
        assertEquals(4, chain.attempts)
        assertEquals(listOf(1_000L, 2_000L, 4_000L), sleeps)
        assertTrue(responses.take(3).all { it.body.isClosed })
        assertFalse(responses.last().body.isClosed)
        response.close()
    }

    @Test
    fun `429 prefers Retry-After seconds over reset and fallback`() {
        val first = testResponse(
            429,
            "Retry-After" to "7",
            "X-RateLimit-Reset" to "120"
        )
        val second = testResponse(200)
        val chain = ScriptedChain(first.response, second.response)
        val sleeps = mutableListOf<Long>()
        val interceptor = AniListRetryInterceptor(
            nowEpochSeconds = { 115 },
            sleep = sleeps::add,
            jitter = { 999 }
        )

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(2, chain.attempts)
        assertEquals(listOf(7_000L), sleeps)
        assertTrue(first.body.isClosed)
        response.close()
    }

    @Test
    fun `negative Retry-After falls through to rate limit reset`() {
        val first = testResponse(
            429,
            "Retry-After" to "-1",
            "X-RateLimit-Reset" to "120"
        )
        val second = testResponse(200)
        val chain = ScriptedChain(first.response, second.response)
        val sleeps = mutableListOf<Long>()
        val interceptor = AniListRetryInterceptor(
            nowEpochSeconds = { 115 },
            sleep = sleeps::add,
            jitter = { 999 }
        )

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(listOf(5_000L), sleeps)
        assertTrue(first.body.isClosed)
        response.close()
    }

    @Test
    fun `oversized Retry-After falls through to rate limit reset`() {
        val first = testResponse(
            429,
            "Retry-After" to Long.MAX_VALUE.toString(),
            "X-RateLimit-Reset" to "120"
        )
        val second = testResponse(200)
        val chain = ScriptedChain(first.response, second.response)
        val sleeps = mutableListOf<Long>()
        val interceptor = AniListRetryInterceptor(
            nowEpochSeconds = { 115 },
            sleep = sleeps::add,
            jitter = { 999 }
        )

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(listOf(5_000L), sleeps)
        assertTrue(first.body.isClosed)
        response.close()
    }

    @Test
    fun `429 uses rate limit reset relative to the current epoch time`() {
        val first = testResponse(429, "X-RateLimit-Reset" to "120")
        val second = testResponse(200)
        val chain = ScriptedChain(first.response, second.response)
        val sleeps = mutableListOf<Long>()
        val interceptor = AniListRetryInterceptor(
            nowEpochSeconds = { 115 },
            sleep = sleeps::add,
            jitter = { 999 }
        )

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(2, chain.attempts)
        assertEquals(listOf(5_000L), sleeps)
        assertTrue(first.body.isClosed)
        response.close()
    }

    @Test
    fun `negative rate limit reset falls through to exponential fallback`() {
        val first = testResponse(429, "X-RateLimit-Reset" to "-1")
        val second = testResponse(200)
        val chain = ScriptedChain(first.response, second.response)
        val sleeps = mutableListOf<Long>()
        val interceptor = AniListRetryInterceptor(
            nowEpochSeconds = { 115 },
            sleep = sleeps::add,
            jitter = { 0 }
        )

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(listOf(1_000L), sleeps)
        assertTrue(first.body.isClosed)
        response.close()
    }

    @Test
    fun `oversized rate limit reset falls through to exponential fallback`() {
        val first = testResponse(
            429,
            "X-RateLimit-Reset" to Long.MAX_VALUE.toString()
        )
        val second = testResponse(200)
        val chain = ScriptedChain(first.response, second.response)
        val sleeps = mutableListOf<Long>()
        val interceptor = AniListRetryInterceptor(
            nowEpochSeconds = { 115 },
            sleep = sleeps::add,
            jitter = { 0 }
        )

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(listOf(1_000L), sleeps)
        assertTrue(first.body.isClosed)
        response.close()
    }

    @Test
    fun `429 without timing headers uses exponential fallback for attempts zero through two`() {
        val responses = List(4) { testResponse(429) }
        val chain = ScriptedChain(*responses.map { it.response }.toTypedArray())
        val sleeps = mutableListOf<Long>()
        val interceptor = AniListRetryInterceptor(
            maxRetries = 3,
            sleep = sleeps::add,
            jitter = { 0 }
        )

        val response = interceptor.intercept(chain)

        assertEquals(429, response.code)
        assertEquals(4, chain.attempts)
        assertEquals(listOf(1_000L, 2_000L, 4_000L), sleeps)
        assertTrue(responses.take(3).all { it.body.isClosed })
        assertFalse(responses.last().body.isClosed)
        response.close()
    }

    private fun Request.bodyUtf8(): String {
        val buffer = Buffer()
        requireNotNull(body).writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun testResponse(
        code: Int,
        vararg headers: Pair<String, String>
    ): TestResponse {
        val body = CloseTrackingResponseBody()
        val response = Response.Builder()
            .request(TEST_REQUEST)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("scripted response")
            .body(body)
            .apply {
                headers.forEach { (name, value) -> addHeader(name, value) }
            }
            .build()
        return TestResponse(response, body)
    }

    private data class TestResponse(
        val response: Response,
        val body: CloseTrackingResponseBody
    )

    private class CloseTrackingResponseBody : ResponseBody() {
        var isClosed = false
            private set

        private val trackingSource = object : ForwardingSource(Buffer()) {
            override fun close() {
                isClosed = true
                super.close()
            }
        }.buffer()

        override fun contentType(): MediaType? = null

        override fun contentLength(): Long = 0

        override fun source(): BufferedSource = trackingSource
    }

    private class ScriptedChain(
        vararg responses: Response
    ) : Interceptor.Chain {
        private val responses = ArrayDeque(responses.toList())

        var request: Request = TEST_REQUEST

        val proceededRequests = mutableListOf<Request>()

        var attempts: Int = 0
            private set

        override fun request(): Request = request

        override fun proceed(request: Request): Response {
            attempts++
            proceededRequests += request
            return responses.removeFirst()
        }

        override fun connection(): Connection? = null

        override fun call(): Call = error("call() is not used by AniListRetryInterceptor")

        override fun connectTimeoutMillis(): Int = 0

        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun readTimeoutMillis(): Int = 0

        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun writeTimeoutMillis(): Int = 0

        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private companion object {
        val APPLICATION_JSON: MediaType = "application/json".toMediaType()

        const val APOLLO_QUERY_PAYLOAD: String =
            """{"operationName":"GenreCollection","variables":{},"query":"query GenreCollection { genres: GenreCollection }"}"""

        val TEST_REQUEST: Request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(APOLLO_QUERY_PAYLOAD.toRequestBody(APPLICATION_JSON))
            .build()
    }
}
