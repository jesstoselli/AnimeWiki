package com.example.animewiki.data.remote

import okhttp3.Interceptor
import okhttp3.Response

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
        var retry = 0
        while (true) {
            val response = chain.proceed(chain.request())
            val retryable = response.code == 429 || response.code in 500..599
            if (!retryable || retry == maxRetries) return response

            val fallback = baseDelayMillis * (1L shl retry)
            val delayMillis = if (response.code == 429) {
                response.header("Retry-After")?.toLongOrNull()?.times(1_000)
                    ?: response.header("X-RateLimit-Reset")?.toLongOrNull()
                        ?.let { ((it - nowEpochSeconds()).coerceAtLeast(0)) * 1_000 }
                    ?: fallback + jitter(fallback)
            } else {
                fallback + jitter(fallback)
            }
            response.close()
            sleep(delayMillis)
            retry++
        }
    }
}
