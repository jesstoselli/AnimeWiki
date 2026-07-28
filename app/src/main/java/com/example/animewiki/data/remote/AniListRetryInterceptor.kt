package com.example.animewiki.data.remote

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException

class AniListRetryInterceptor(
    private val maxRetries: Int = 3,
    private val baseDelayMillis: Long = 1_000,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val jitter: (Long) -> Long = { bound ->
        if (bound <= 1) 0 else kotlin.random.Random.nextLong(bound)
    }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val canRetry = request.isReadOnlyGraphQlQuery()
        var retry = 0
        while (true) {
            val response = chain.proceed(request)
            val retryable = response.code == 429 || response.code in 500..599
            if (!canRetry || !retryable || retry == maxRetries) return response

            val fallback = baseDelayMillis * (1L shl retry)
            val delayMillis = if (response.code == 429) {
                response.header("Retry-After")?.toLongOrNull()
                    ?.toDelayMillisOrNull()
                    ?: response.header("X-RateLimit-Reset")?.toLongOrNull()
                        ?.toResetDelayMillisOrNull(nowEpochSeconds())
                    ?: fallback + jitter(fallback)
            } else {
                fallback + jitter(fallback)
            }
            response.close()
            sleep(delayMillis)
            retry++
        }
    }

    private fun Request.isReadOnlyGraphQlQuery(): Boolean {
        val requestBody = body ?: return false
        if (method != "POST" || requestBody.isDuplex() || requestBody.isOneShot()) return false

        return try {
            val buffer = Buffer()
            requestBody.writeTo(buffer)
            val payload = Json.parseToJsonElement(buffer.readUtf8()) as? JsonObject
                ?: return false
            val document = (payload["query"] as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?: return false
            document.trimStart().startsWith("query ")
        } catch (_: IOException) {
            false
        } catch (_: SerializationException) {
            false
        }
    }

    private fun Long.toDelayMillisOrNull(): Long? =
        takeIf { it in 0..Long.MAX_VALUE / MILLIS_PER_SECOND }
            ?.times(MILLIS_PER_SECOND)

    private fun Long.toResetDelayMillisOrNull(now: Long): Long? {
        if (this < 0) return null
        val secondsUntilReset = try {
            Math.subtractExact(this, now).coerceAtLeast(0)
        } catch (_: ArithmeticException) {
            return null
        }
        return secondsUntilReset.toDelayMillisOrNull()
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
