package com.example.animewiki.data.paging

import androidx.paging.LoadType
import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.apollographql.apollo.ApolloClient
import com.apollographql.mockserver.MockResponse
import com.apollographql.mockserver.MockServer
import com.example.animewiki.data.local.AppDatabase
import com.example.animewiki.data.local.dao.AnimeDao
import com.example.animewiki.data.local.dao.RemoteKeyDao
import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.data.local.entity.RemoteKeyEntity
import com.example.animewiki.data.remote.AniListGraphQlException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalPagingApi::class)
class TopAnimeRemoteMediatorTest {

    @Test
    fun `refresh sends non adult capped query and writes full mapped cache inside transaction`() =
        runTest {
            withServer(fullTopPage()) { server, client ->
                val fixture = DatabaseFixture()
                val result = fixture.mediator(client).load(LoadType.REFRESH, state(pageSize = 50))
                val variables = Json.parseToJsonElement(
                    server.takeRequest().body.utf8()
                ).jsonObject.getValue("variables").jsonObject

                assertTrue(result is RemoteMediator.MediatorResult.Success)
                assertEquals(1, variables.getValue("page").jsonPrimitive.int)
                assertEquals(25, variables.getValue("perPage").jsonPrimitive.int)
                assertFalse(variables.getValue("isAdult").jsonPrimitive.boolean)
                assertEquals(1, fixture.transactions)
                assertEquals(1, fixture.clearAnimeCalls)
                assertEquals(1, fixture.clearKeyCalls)
                assertEquals(1, fixture.keys.single().animeId)
                assertNull(fixture.keys.single().prevKey)
                assertEquals(2, fixture.keys.single().nextKey)

                val anime = fixture.entities.single()
                assertEquals(1, anime.id)
                assertEquals("Fullmetal Alchemist: Brotherhood", anime.title)
                assertEquals("A complete synopsis.", anime.synopsis)
                assertEquals(listOf("Action", "Adventure"), anime.genres)
                assertEquals(listOf("Bones"), anime.studios)
                assertEquals("Apr 5, 2009 to Jul 4, 2010", anime.aired)
                assertEquals("Finished", anime.status)
                assertEquals("24 min per ep", anime.duration)
                assertEquals(1, anime.rank)
                assertEquals("dQw4w9WgXcQ", anime.trailerYoutubeId)
            }
        }

    @Test
    fun `GraphQL field errors keep valid sibling media`() = runTest {
        withServer(partialTopPage()) { _, client ->
            val fixture = DatabaseFixture()

            val result = fixture.mediator(client).load(LoadType.REFRESH, state())

            assertTrue(result is RemoteMediator.MediatorResult.Success)
            assertEquals(listOf(1), fixture.entities.map { it.id })
        }
    }

    @Test
    fun `missing required page is typed error and leaves anime rows and keys untouched`() =
        runTest {
            withServer(
                """{"data":{"Page":null},"errors":[{"message":"ranking unavailable"}]}"""
            ) { _, client ->
                val fixture = DatabaseFixture()

                val result = fixture.mediator(client).load(LoadType.REFRESH, state())

                assertTrue(result is RemoteMediator.MediatorResult.Error)
                val throwable = (result as RemoteMediator.MediatorResult.Error).throwable
                assertTrue(throwable is AniListGraphQlException)
                assertEquals("ranking unavailable", throwable.message)
                assertEquals(0, fixture.transactions)
                assertEquals(0, fixture.clearAnimeCalls)
                assertEquals(0, fixture.clearKeyCalls)
                assertTrue(fixture.entities.isEmpty())
                assertTrue(fixture.keys.isEmpty())
            }
        }

    @Test
    fun `response without usable data is error and never starts cache transaction`() = runTest {
        withServer("""{"data":null,"errors":[{"message":"upstream failed"}]}""") { _, client ->
            val fixture = DatabaseFixture()

            val result = fixture.mediator(client).load(LoadType.REFRESH, state())

            assertTrue(result is RemoteMediator.MediatorResult.Error)
            assertEquals(0, fixture.transactions)
            assertEquals(0, fixture.clearAnimeCalls)
            assertEquals(0, fixture.clearKeyCalls)
        }
    }

    @Test
    fun `error bearing page with zero usable siblings preserves refresh cache`() = runTest {
        withServer(allNullPartialTopPage()) { _, client ->
            val fixture = DatabaseFixture()

            val result = fixture.mediator(client).load(LoadType.REFRESH, state())

            assertTrue(result is RemoteMediator.MediatorResult.Error)
            val throwable = (result as RemoteMediator.MediatorResult.Error).throwable
            assertTrue(throwable is AniListGraphQlException)
            assertEquals("all media failed", throwable.message)
            fixture.assertNoMutation()
        }
    }

    @Test
    fun `error bearing empty raw page is unusable and preserves refresh cache`() = runTest {
        withServer(errorBearingEmptyTopPage()) { _, client ->
            val fixture = DatabaseFixture()

            val result = fixture.mediator(client).load(LoadType.REFRESH, state())

            assertTrue(result is RemoteMediator.MediatorResult.Error)
            val throwable = (result as RemoteMediator.MediatorResult.Error).throwable
            assertTrue(throwable is AniListGraphQlException)
            assertEquals("empty page failed", throwable.message)
            fixture.assertNoMutation()
        }
    }

    @Test
    fun `nonempty raw page with only unmappable media is error without mutation`() = runTest {
        withServer(unmappableTopPage(hasNextPage = false)) { _, client ->
            val fixture = DatabaseFixture()

            val result = fixture.mediator(client).load(LoadType.REFRESH, state())

            assertTrue(result is RemoteMediator.MediatorResult.Error)
            val throwable = (result as RemoteMediator.MediatorResult.Error).throwable
            assertTrue(throwable is AniListGraphQlException)
            assertEquals("AniList top response contained no usable media", throwable.message)
            fixture.assertNoMutation()
        }
    }

    @Test
    fun `empty page claiming a next page is error without mutation`() = runTest {
        withServer(emptyTopPage(hasNextPage = true)) { _, client ->
            val fixture = DatabaseFixture()

            val result = fixture.mediator(client).load(LoadType.REFRESH, state())

            assertTrue(result is RemoteMediator.MediatorResult.Error)
            val throwable = (result as RemoteMediator.MediatorResult.Error).throwable
            assertTrue(throwable is AniListGraphQlException)
            fixture.assertNoMutation()
        }
    }

    @Test
    fun `authoritative empty refresh clears cache and reports terminal success`() = runTest {
        withServer(emptyTopPage(hasNextPage = false)) { _, client ->
            val fixture = DatabaseFixture()

            val result = fixture.mediator(client).load(LoadType.REFRESH, state())

            assertTrue(result is RemoteMediator.MediatorResult.Success)
            assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
            assertEquals(1, fixture.transactions)
            assertEquals(1, fixture.clearAnimeCalls)
            assertEquals(1, fixture.clearKeyCalls)
        }
    }

    @Test
    fun `append requests remote next page and persists ordered indexes and keys`() = runTest {
        withServer(appendTopPage(hasNextPage = true)) { server, client ->
            val fixture = DatabaseFixture(
                remoteKey = RemoteKeyEntity(animeId = 1, prevKey = null, nextKey = 2),
                maxPageIndex = 4
            )

            val result = fixture.mediator(client).load(
                LoadType.APPEND,
                state(items = listOf(cachedAnime(id = 1, pageIndex = 4)))
            )
            val variables = Json.parseToJsonElement(
                server.takeRequest().body.utf8()
            ).jsonObject.getValue("variables").jsonObject

            assertTrue(result is RemoteMediator.MediatorResult.Success)
            assertFalse((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
            assertEquals(2, variables.getValue("page").jsonPrimitive.int)
            assertEquals(25, variables.getValue("perPage").jsonPrimitive.int)
            assertFalse(variables.getValue("isAdult").jsonPrimitive.boolean)
            assertEquals(1, fixture.transactions)
            assertEquals(0, fixture.clearAnimeCalls)
            assertEquals(0, fixture.clearKeyCalls)
            assertEquals(1, fixture.maxPageIndexCalls)
            assertEquals(5, fixture.entities.single().pageIndex)
            assertEquals(2, fixture.keys.single().animeId)
            assertEquals(1, fixture.keys.single().prevKey)
            assertEquals(3, fixture.keys.single().nextKey)
        }
    }

    @Test
    fun `append terminal valid page stores null next key and reports end reached`() = runTest {
        withServer(appendTopPage(hasNextPage = false)) { _, client ->
            val fixture = DatabaseFixture(
                remoteKey = RemoteKeyEntity(animeId = 1, prevKey = null, nextKey = 2),
                maxPageIndex = 4
            )

            val result = fixture.mediator(client).load(
                LoadType.APPEND,
                state(items = listOf(cachedAnime(id = 1, pageIndex = 4)))
            )

            assertTrue(result is RemoteMediator.MediatorResult.Success)
            assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
            assertEquals(1, fixture.keys.single().prevKey)
            assertNull(fixture.keys.single().nextKey)
        }
    }

    @Test
    fun `append genuinely empty terminal page performs no writes and reports end reached`() =
        runTest {
            withServer(emptyTopPage(hasNextPage = false, currentPage = 2)) { _, client ->
                val fixture = DatabaseFixture(
                    remoteKey = RemoteKeyEntity(animeId = 1, prevKey = null, nextKey = 2),
                    maxPageIndex = 4
                )

                val result = fixture.mediator(client).load(
                    LoadType.APPEND,
                    state(items = listOf(cachedAnime(id = 1, pageIndex = 4)))
                )

                assertTrue(result is RemoteMediator.MediatorResult.Success)
                assertTrue((result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
                assertEquals(0, fixture.transactions)
                assertEquals(0, fixture.entityWriteCalls)
                assertEquals(0, fixture.keyWriteCalls)
                assertEquals(0, fixture.clearAnimeCalls)
                assertEquals(0, fixture.clearKeyCalls)
            }
        }

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

    private fun state(
        pageSize: Int = 25,
        items: List<AnimeEntity> = emptyList()
    ) = PagingState<Int, AnimeEntity>(
        pages = items.takeIf { it.isNotEmpty() }
            ?.let {
                listOf(
                    PagingSource.LoadResult.Page<Int, AnimeEntity>(
                        data = it,
                        prevKey = null,
                        nextKey = null
                    )
                )
            }
            .orEmpty(),
        anchorPosition = null,
        config = PagingConfig(pageSize = pageSize),
        leadingPlaceholderCount = 0
    )

    private class DatabaseFixture(
        remoteKey: RemoteKeyEntity? = null,
        maxPageIndex: Int = -1
    ) {
        private val animeDao: AnimeDao = mockk(relaxed = true)
        private val remoteKeyDao: RemoteKeyDao = mockk(relaxed = true)
        private val entitySlot = slot<List<AnimeEntity>>()
        private val keySlot = slot<List<RemoteKeyEntity>>()
        private var insideTransaction = false

        val db: AppDatabase = mockk {
            every { animeDao() } returns this@DatabaseFixture.animeDao
            every { remoteKeyDao() } returns this@DatabaseFixture.remoteKeyDao
        }
        var transactions = 0
            private set
        var clearAnimeCalls = 0
            private set
        var clearKeyCalls = 0
            private set
        var entityWriteCalls = 0
            private set
        var keyWriteCalls = 0
            private set
        var maxPageIndexCalls = 0
            private set

        val entities: List<AnimeEntity>
            get() = if (entitySlot.isCaptured) entitySlot.captured else emptyList()
        val keys: List<RemoteKeyEntity>
            get() = if (keySlot.isCaptured) keySlot.captured else emptyList()

        init {
            coEvery { remoteKeyDao.getKey(any()) } returns remoteKey
            coEvery { animeDao.maxPageIndex() } coAnswers {
                maxPageIndexCalls += 1
                maxPageIndex
            }
            coEvery { animeDao.clearAll() } coAnswers {
                assertTrue(insideTransaction)
                clearAnimeCalls += 1
            }
            coEvery { remoteKeyDao.clearAll() } coAnswers {
                assertTrue(insideTransaction)
                clearKeyCalls += 1
            }
            coEvery { animeDao.upsertAll(capture(entitySlot)) } coAnswers {
                assertTrue(insideTransaction)
                entityWriteCalls += 1
            }
            coEvery { remoteKeyDao.upsertAll(capture(keySlot)) } coAnswers {
                assertTrue(insideTransaction)
                keyWriteCalls += 1
            }
        }

        fun assertNoMutation() {
            assertEquals(0, transactions)
            assertEquals(0, clearAnimeCalls)
            assertEquals(0, clearKeyCalls)
            assertEquals(0, entityWriteCalls)
            assertEquals(0, keyWriteCalls)
            assertTrue(entities.isEmpty())
            assertTrue(keys.isEmpty())
        }

        fun mediator(client: ApolloClient) = TopAnimeRemoteMediator(
            apolloClient = client,
            db = db,
            transaction = { block ->
                transactions += 1
                insideTransaction = true
                try {
                    block()
                } finally {
                    insideTransaction = false
                }
            }
        )
    }

    private fun fullTopPage(): String =
        """
        {
          "data": {
            "Page": {
              "pageInfo": {"currentPage": 1, "hasNextPage": true},
              "media": [{
                "__typename": "Media",
                "id": 1,
                "title": {
                  "english": "Fullmetal Alchemist: Brotherhood",
                  "romaji": "Hagane no Renkinjutsushi"
                },
                "coverImage": {
                  "extraLarge": "https://example.com/fmab.jpg",
                  "large": "https://example.com/fmab-large.jpg"
                },
                "averageScore": 91,
                "episodes": 64,
                "format": "TV",
                "seasonYear": 2009,
                "description": "A complete synopsis.",
                "genres": ["Action", "Adventure"],
                "studios": {"nodes": [{"name": "Bones"}]},
                "status": "FINISHED",
                "duration": 24,
                "startDate": {"year": 2009, "month": 4, "day": 5},
                "endDate": {"year": 2010, "month": 7, "day": 4},
                "trailer": {"id": "dQw4w9WgXcQ", "site": "youtube"},
                "rankings": [{"rank": 1, "type": "RATED", "allTime": true}]
              }]
            }
          }
        }
        """.trimIndent()

    private fun partialTopPage(): String =
        """
        {
          "data": {
            "Page": {
              "pageInfo": {"currentPage": 1, "hasNextPage": false},
              "media": [
                {
                  "__typename": "Media",
                  "id": 1,
                  "title": {"english": "Valid", "romaji": "Valid"},
                  "coverImage": {
                    "extraLarge": "https://example.com/valid.jpg",
                    "large": null
                  },
                  "averageScore": 80,
                  "episodes": 12,
                  "format": "TV",
                  "seasonYear": 2024,
                  "description": null,
                  "genres": [],
                  "studios": {"nodes": []},
                  "status": "FINISHED",
                  "duration": 24,
                  "startDate": {"year": 2024, "month": 1, "day": 1},
                  "endDate": {"year": 2024, "month": 3, "day": 20},
                  "trailer": null,
                  "rankings": []
                },
                null
              ]
            }
          },
          "errors": [{"message": "one media failed"}]
        }
        """.trimIndent()

    private fun allNullPartialTopPage(): String =
        """
        {
          "data": {
            "Page": {
              "pageInfo": {"currentPage": 1, "hasNextPage": false},
              "media": [null]
            }
          },
          "errors": [{"message": "all media failed"}]
        }
        """.trimIndent()

    private fun errorBearingEmptyTopPage(): String =
        """
        {
          "data": {
            "Page": {
              "pageInfo": {"currentPage": 1, "hasNextPage": false},
              "media": []
            }
          },
          "errors": [{"message": "empty page failed"}]
        }
        """.trimIndent()

    private fun emptyTopPage(
        hasNextPage: Boolean,
        currentPage: Int = 1
    ): String =
        """
        {
          "data": {
            "Page": {
              "pageInfo": {"currentPage": $currentPage, "hasNextPage": $hasNextPage},
              "media": []
            }
          }
        }
        """.trimIndent()

    private fun unmappableTopPage(hasNextPage: Boolean): String =
        """
        {
          "data": {
            "Page": {
              "pageInfo": {"currentPage": 1, "hasNextPage": $hasNextPage},
              "media": [{
                "__typename": "Media",
                "id": 404,
                "title": {"english": " ", "romaji": ""},
                "coverImage": {
                  "extraLarge": "https://example.com/invalid.jpg",
                  "large": "https://example.com/invalid-large.jpg"
                },
                "averageScore": 50,
                "episodes": 1,
                "format": "TV",
                "seasonYear": 2024,
                "description": null,
                "genres": [],
                "studios": {"nodes": []},
                "status": "FINISHED",
                "duration": 1,
                "startDate": {"year": 2024, "month": 1, "day": 1},
                "endDate": {"year": 2024, "month": 1, "day": 1},
                "trailer": null,
                "rankings": []
              }]
            }
          }
        }
        """.trimIndent()

    private fun appendTopPage(hasNextPage: Boolean): String =
        """
        {
          "data": {
            "Page": {
              "pageInfo": {"currentPage": 2, "hasNextPage": $hasNextPage},
              "media": [{
                "__typename": "Media",
                "id": 2,
                "title": {"english": "Second page", "romaji": "Second page"},
                "coverImage": {
                  "extraLarge": "https://example.com/page-2.jpg",
                  "large": "https://example.com/page-2-large.jpg"
                },
                "averageScore": 85,
                "episodes": 24,
                "format": "TV",
                "seasonYear": 2025,
                "description": "Second page synopsis.",
                "genres": ["Adventure"],
                "studios": {"nodes": [{"name": "Page Studio"}]},
                "status": "RELEASING",
                "duration": 24,
                "startDate": {"year": 2025, "month": 1, "day": 1},
                "endDate": {"year": 2025, "month": 6, "day": 1},
                "trailer": null,
                "rankings": []
              }]
            }
          }
        }
        """.trimIndent()

    private fun cachedAnime(id: Int, pageIndex: Int) = AnimeEntity(
        id = id,
        title = "Cached",
        imageUrl = "https://example.com/cached.jpg",
        score = 8.0,
        episodes = 12,
        type = "TV",
        year = 2024,
        synopsis = null,
        genres = emptyList(),
        studios = emptyList(),
        aired = null,
        status = null,
        rating = null,
        duration = null,
        rank = null,
        trailerYoutubeId = null,
        pageIndex = pageIndex
    )
}
