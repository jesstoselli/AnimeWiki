package com.example.animewiki.data.repository

import com.apollographql.apollo.ApolloClient
import com.apollographql.mockserver.MockResponse
import com.apollographql.mockserver.MockServer
import com.example.animewiki.data.local.AppDatabase
import com.example.animewiki.data.local.dao.HomeShelfDao
import com.example.animewiki.data.local.entity.HomeShelfItemEntity
import com.example.animewiki.domain.model.HomeShelf
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class HomeShelfRepositoryTest {

    private val homeShelfDao: HomeShelfDao = mockk(relaxed = true)
    private val db: AppDatabase = mockk {
        every { homeShelfDao() } returns this@HomeShelfRepositoryTest.homeShelfDao
    }
    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC)

    private fun repository(client: ApolloClient) =
        HomeShelfRepository(client, db, fixedClock)

    private fun <T> withServer(response: String, block: suspend (ApolloClient) -> T): T =
        runBlocking {
            val server = MockServer()
            server.enqueue(MockResponse.Builder().body(response).build())
            val client = ApolloClient.Builder().serverUrl(server.url()).build()
            try {
                block(client)
            } finally {
                client.close()
                server.close()
            }
        }

    @Test
    fun `refresh trending stores mapped media and skips malformed entries`() = runTest {
        val body = """
        {"data":{"Page":{"pageInfo":{"currentPage":1,"hasNextPage":false},"media":[
          {"__typename":"Media","id":1,"title":{"english":"Alpha","romaji":"Alpha"},
           "coverImage":{"extraLarge":"https://img/1.jpg","large":null},
           "averageScore":88,"episodes":12,"format":"TV","seasonYear":2026,
           "isAdult":false,"season":"SUMMER","nextAiringEpisode":null},
          {"__typename":"Media","id":2,"title":{"english":null,"romaji":null},
           "coverImage":{"extraLarge":null,"large":null},
           "averageScore":null,"episodes":null,"format":null,"seasonYear":null,
           "isAdult":false,"season":null,"nextAiringEpisode":null}
        ]}}}
        """.trimIndent()

        withServer(body) { client ->
            val stored = slot<List<HomeShelfItemEntity>>()

            repository(client).refresh(HomeShelf.TRENDING)

            coVerify(exactly = 1) {
                homeShelfDao.replaceShelf("TRENDING", capture(stored))
            }
            assertEquals(1, stored.captured.size)
            assertEquals("Alpha", stored.captured[0].title)
            assertEquals(0, stored.captured[0].position)
        }
    }

    @Test
    fun `observe trending maps cached rows to shelf anime`() = runTest {
        every { homeShelfDao.observeShelf("TRENDING") } returns flowOf(
            listOf(
                HomeShelfItemEntity(
                    shelf = "TRENDING", position = 0, id = 5, title = "Cached",
                    imageUrl = "https://img/5.jpg", score = 8.0, season = "FALL",
                    year = 2026, rank = null, nextEpisode = null,
                    nextAiringAtSeconds = null
                )
            )
        )

        val items = repository(mockk()).observe(HomeShelf.TRENDING).first()

        assertEquals(1, items.size)
        assertEquals("Cached", items[0].title)
    }
}
