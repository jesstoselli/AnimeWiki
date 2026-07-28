package com.example.animewiki.data.remote

import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class AniListRetryInterceptorTest {

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

        var attempts: Int = 0
            private set

        override fun request(): Request = TEST_REQUEST

        override fun proceed(request: Request): Response {
            attempts++
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
        val TEST_REQUEST: Request = Request.Builder()
            .url("https://graphql.anilist.co")
            .build()
    }
}
