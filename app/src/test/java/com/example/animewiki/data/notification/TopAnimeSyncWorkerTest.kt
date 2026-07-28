package com.example.animewiki.data.notification

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.interceptor.ApolloInterceptor
import com.apollographql.apollo.interceptor.ApolloInterceptorChain
import com.apollographql.mockserver.MockResponse
import com.apollographql.mockserver.MockServer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class TopAnimeSyncWorkerTest {

    private val context: Context = mockk(relaxed = true) {
        every { applicationContext } returns this
    }
    private val params: WorkerParameters = mockk(relaxed = true)
    private val notificationHelper: NotificationHelper = mockk(relaxed = true)

    @Test
    fun `worker queries non adult AniList top and notifies with mapped AniList item`() = runTest {
        withServer(validTopResponse(includeErrors = true)) { server, client ->
            val result = worker(client).doWork()
            val variables = Json.parseToJsonElement(
                server.takeRequest().body.utf8()
            ).jsonObject.getValue("variables").jsonObject

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(1, variables.getValue("page").jsonPrimitive.int)
            assertEquals(1, variables.getValue("perPage").jsonPrimitive.int)
            assertEquals(false, variables.getValue("isAdult").jsonPrimitive.boolean)
            verify(exactly = 1) {
                notificationHelper.showWeeklyTopAnime(
                    animeId = 52991,
                    title = "Frieren: Beyond Journey's End",
                    score = "9.20"
                )
            }
        }
    }

    @Test
    fun `worker retries when top page has no usable mapped item`() = runTest {
        withServer(emptyTopResponse()) { _, client ->
            val result = worker(client).doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            verify(exactly = 0) {
                notificationHelper.showWeeklyTopAnime(any(), any(), any())
            }
        }
    }

    @Test
    fun `worker retries on GraphQL failure`() = runTest {
        withServer("""{"data":null,"errors":[{"message":"ranking failed"}]}""") { _, client ->
            val result = worker(client).doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            verify(exactly = 0) {
                notificationHelper.showWeeklyTopAnime(any(), any(), any())
            }
        }
    }

    @Test
    fun `worker propagates cancellation instead of converting it to retry`() = runTest {
        val client = ApolloClient.Builder()
            .serverUrl("https://example.test/graphql")
            .addInterceptor(CancellingInterceptor(CancellationException("cancel worker")))
            .build()
        try {
            val thrown = try {
                worker(client).doWork()
                fail("Expected cancellation")
                null
            } catch (error: CancellationException) {
                error
            }

            assertEquals("cancel worker", thrown?.message)
            verify(exactly = 0) {
                notificationHelper.showWeeklyTopAnime(any(), any(), any())
            }
        } finally {
            client.close()
        }
    }

    private fun worker(client: ApolloClient) = TopAnimeSyncWorker(
        context = context,
        params = params,
        apolloClient = client,
        notificationHelper = notificationHelper
    )

    private suspend fun withServer(
        responseBody: String,
        block: suspend (MockServer, ApolloClient) -> Unit
    ) {
        val server = MockServer.Builder().build()
        val client = ApolloClient.Builder().serverUrl(server.url()).build()
        try {
            server.enqueue(MockResponse.Builder().body(responseBody).build())
            block(server, client)
        } finally {
            client.close()
            server.close()
        }
    }

    private fun validTopResponse(includeErrors: Boolean): String =
        """
        {
          "data": {
            "Page": {
              "pageInfo": {"currentPage": 1, "hasNextPage": true},
              "media": [{
                "__typename": "Media",
                "id": 52991,
                "title": {
                  "english": "Frieren: Beyond Journey's End",
                  "romaji": "Sousou no Frieren"
                },
                "coverImage": {
                  "extraLarge": "https://example.com/frieren.jpg",
                  "large": "https://example.com/frieren-large.jpg"
                },
                "averageScore": 92,
                "episodes": 28,
                "format": "TV",
                "seasonYear": 2023,
                "description": "A mage retraces a journey.",
                "genres": ["Adventure", "Fantasy"],
                "studios": {"nodes": [{"name": "Madhouse"}]},
                "status": "FINISHED",
                "duration": 24,
                "startDate": {"year": 2023, "month": 9, "day": 29},
                "endDate": {"year": 2024, "month": 3, "day": 22},
                "trailer": {"id": "trailer-id", "site": "youtube"},
                "rankings": [{"rank": 1, "type": "RATED", "allTime": true}]
              }]
            }
          }
          ${if (includeErrors) ""","errors":[{"message":"one sibling failed"}]""" else ""}
        }
        """.trimIndent()

    private fun emptyTopResponse(): String =
        """
        {
          "data": {
            "Page": {
              "pageInfo": {"currentPage": 1, "hasNextPage": false},
              "media": [null]
            }
          }
        }
        """.trimIndent()

    private class CancellingInterceptor(
        private val cancellation: CancellationException
    ) : ApolloInterceptor {
        override fun <D : Operation.Data> intercept(
            request: ApolloRequest<D>,
            chain: ApolloInterceptorChain
        ): Flow<ApolloResponse<D>> = flow {
            throw cancellation
        }
    }
}
