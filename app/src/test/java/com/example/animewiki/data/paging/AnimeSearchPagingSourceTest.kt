package com.example.animewiki.data.paging

import androidx.paging.PagingSource
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.interceptor.ApolloInterceptor
import com.apollographql.apollo.interceptor.ApolloInterceptorChain
import com.apollographql.mockserver.MockResponse
import com.apollographql.mockserver.MockServer
import com.example.animewiki.data.remote.AniListGraphQlException
import com.example.animewiki.domain.model.AnimeBrowseCriteria
import com.example.animewiki.domain.model.AnimeFilters
import com.example.animewiki.domain.model.AnimeFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeSearchPagingSourceTest {
    @Test
    fun `default filters send explicit false adult variable and omit blank search`() = runTest {
        withServer(emptyPageResponse()) { server, client ->
            val result = AnimeSearchPagingSource(
                client,
                AnimeBrowseCriteria.create(query = "   ")
            ).load(refresh())
            val variables = server.takeRequest().variables()

            assertTrue(result is PagingSource.LoadResult.Page)
            assertEquals(1, variables.getValue("page").jsonPrimitive.int)
            assertEquals(25, variables.getValue("perPage").jsonPrimitive.int)
            assertFalse(variables.containsKey("search"))
            assertFalse(variables.containsKey("format"))
            assertFalse(variables.containsKey("genres"))
            assertFalse(variables.getValue("isAdult").jsonPrimitive.boolean)
        }
    }

    @Test
    fun `including adult content omits isAdult instead of sending true`() = runTest {
        withServer(emptyPageResponse()) { server, client ->
            AnimeSearchPagingSource(
                client,
                AnimeBrowseCriteria.create(
                    filters = AnimeFilters(includeAdultContent = true)
                )
            ).load(refresh())
            val requestBody = server.takeRequest().body.utf8()
            val variables = requestBody.variables()

            assertFalse(variables.containsKey("isAdult"))
            assertFalse(requestBody.contains(""""isAdult":true"""))
        }
    }

    @Test
    fun `query format and genres use normalized deterministic AniList variables`() = runTest {
        withServer(emptyPageResponse()) { server, client ->
            AnimeSearchPagingSource(
                client,
                AnimeBrowseCriteria.create(
                    query = "  frieren  ",
                    filters = AnimeFilters(
                        format = AnimeFormat.TV,
                        genres = setOf("Fantasy", "Action")
                    )
                )
            ).load(refresh(loadSize = 40))
            val variables = server.takeRequest().variables()

            assertEquals("frieren", variables.getValue("search").jsonPrimitive.content)
            assertEquals("TV", variables.getValue("format").jsonPrimitive.content)
            assertEquals(
                listOf("Action", "Fantasy"),
                variables.getValue("genres").jsonArray.map { it.jsonPrimitive.content }
            )
            assertEquals(25, variables.getValue("perPage").jsonPrimitive.int)
            assertFalse(variables.getValue("isAdult").jsonPrimitive.boolean)
        }
    }

    @Test
    fun `has next page advances key`() = runTest {
        withServer(emptyPageResponse(hasNextPage = true)) { _, client ->
            val result = AnimeSearchPagingSource(
                client,
                AnimeBrowseCriteria.create()
            ).load(refresh()) as PagingSource.LoadResult.Page

            assertEquals(2, result.nextKey)
            assertEquals(null, result.prevKey)
        }
    }

    @Test
    fun `real generated response mapping skips malformed media and keeps valid media`() = runTest {
        withServer(pageWithMalformedAndValidMedia()) { _, client ->
            val result = AnimeSearchPagingSource(
                client,
                AnimeBrowseCriteria.create(query = "frieren")
            ).load(refresh()) as PagingSource.LoadResult.Page

            assertEquals(listOf(52991), result.data.map { it.id })
            assertEquals("Frieren: Beyond Journey's End", result.data.single().title)
            assertEquals(9.2, result.data.single().score ?: 0.0, 0.0)
        }
    }

    @Test
    fun `append load delays 400 milliseconds in virtual time`() = runTest {
        withServer(emptyPageResponse()) { _, client ->
            val source = AnimeSearchPagingSource(client, AnimeBrowseCriteria.create())
            val startedAt = testScheduler.currentTime

            val result = source.load(append())

            assertTrue(result is PagingSource.LoadResult.Page)
            assertEquals(startedAt + 400, testScheduler.currentTime)
        }
    }

    @Test
    fun `cancellation from Apollo is propagated`() = runTest {
        val cancellation = CancellationException("cancel search")
        val client = ApolloClient.Builder()
            .serverUrl("https://example.test/graphql")
            .addInterceptor(CancellingInterceptor(cancellation))
            .build()
        try {
            val thrown = try {
                AnimeSearchPagingSource(client, AnimeBrowseCriteria.create()).load(refresh())
                throw AssertionError("Expected cancellation to be propagated")
            } catch (error: CancellationException) {
                error
            }

            assertEquals("cancel search", thrown.message)
        } finally {
            client.close()
        }
    }

    @Test
    fun `non cancellation Apollo failure becomes load result error`() = runTest {
        withServer("""{"errors":[{"message":"search unavailable"}]}""") { _, client ->
            val result = AnimeSearchPagingSource(
                client,
                AnimeBrowseCriteria.create()
            ).load(refresh())

            assertTrue(result is PagingSource.LoadResult.Error)
            val throwable = (result as PagingSource.LoadResult.Error).throwable
            assertTrue(throwable is AniListGraphQlException)
            assertEquals("search unavailable", throwable.message)
        }
    }

    private suspend fun withServer(
        responseBody: String,
        block: suspend (MockServer, ApolloClient) -> Unit
    ) {
        val server = MockServer.Builder().build()
        val client = ApolloClient.Builder().serverUrl(server.url()).build()
        try {
            server.enqueue(
                MockResponse.Builder()
                    .body(responseBody)
                    .build()
            )
            block(server, client)
        } finally {
            client.close()
            server.close()
        }
    }

    private fun refresh(loadSize: Int = 25) = PagingSource.LoadParams.Refresh<Int>(
        key = null,
        loadSize = loadSize,
        placeholdersEnabled = false
    )

    private fun append(loadSize: Int = 25) = PagingSource.LoadParams.Append(
        key = 2,
        loadSize = loadSize,
        placeholdersEnabled = false
    )

    private fun com.apollographql.mockserver.MockRequest.variables(): JsonObject =
        body.utf8().variables()

    private fun String.variables(): JsonObject =
        Json.parseToJsonElement(this).jsonObject.getValue("variables").jsonObject

    private fun emptyPageResponse(hasNextPage: Boolean = false): String =
        """
        {
          "data": {
            "Page": {
              "pageInfo": {
                "currentPage": 1,
                "hasNextPage": $hasNextPage
              },
              "media": []
            }
          }
        }
        """.trimIndent()

    private fun pageWithMalformedAndValidMedia(): String =
        """
        {
          "data": {
            "Page": {
              "pageInfo": {
                "currentPage": 1,
                "hasNextPage": false
              },
              "media": [
                {
                  "__typename": "Media",
                  "id": 1,
                  "title": {
                    "english": null,
                    "romaji": null
                  },
                  "coverImage": {
                    "extraLarge": "https://example.com/malformed.jpg",
                    "large": null
                  },
                  "averageScore": 10,
                  "episodes": 1,
                  "format": "TV",
                  "seasonYear": 2024
                },
                {
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
                  "seasonYear": 2023
                }
              ]
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
