package com.example.animewiki.data.paging

import androidx.paging.LoadType
import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingConfig
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

    private fun state(pageSize: Int = 25) = PagingState<Int, AnimeEntity>(
        pages = emptyList(),
        anchorPosition = null,
        config = PagingConfig(pageSize = pageSize),
        leadingPlaceholderCount = 0
    )

    private class DatabaseFixture {
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

        val entities: List<AnimeEntity>
            get() = if (entitySlot.isCaptured) entitySlot.captured else emptyList()
        val keys: List<RemoteKeyEntity>
            get() = if (keySlot.isCaptured) keySlot.captured else emptyList()

        init {
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
            }
            coEvery { remoteKeyDao.upsertAll(capture(keySlot)) } coAnswers {
                assertTrue(insideTransaction)
            }
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
}
