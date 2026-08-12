package com.example.animewiki.data.repository

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.interceptor.ApolloInterceptor
import com.apollographql.apollo.interceptor.ApolloInterceptorChain
import com.apollographql.mockserver.MockResponse
import com.apollographql.mockserver.MockServer
import com.example.animewiki.data.local.AppDatabase
import com.example.animewiki.data.local.dao.AnimeDao
import com.example.animewiki.data.local.dao.FavoriteDao
import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.domain.model.Anime
import com.example.animewiki.domain.model.AnimeGenre
import com.example.animewiki.graphql.GenreCollectionQuery
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AnimeRepositoryTest {

    private val animeDao: AnimeDao = mockk(relaxed = true)
    private val favoriteDao: FavoriteDao = mockk(relaxed = true)
    private val db: AppDatabase = mockk {
        every { animeDao() } returns this@AnimeRepositoryTest.animeDao
    }

    @Test
    fun `toggleFavorite inserts when anime is not yet favorite`() = runTest {
        repository(mockk()).toggleFavorite(frieren(), isCurrentlyFavorite = false)

        coVerify(exactly = 1) { favoriteDao.insert(match { it.id == 52991 }) }
        coVerify(exactly = 0) { favoriteDao.deleteById(any()) }
    }

    @Test
    fun `toggleFavorite deletes when anime is currently favorite`() = runTest {
        repository(mockk()).toggleFavorite(frieren(), isCurrentlyFavorite = true)

        coVerify(exactly = 1) { favoriteDao.deleteById(52991) }
        coVerify(exactly = 0) { favoriteDao.insert(any()) }
    }

    @Test
    fun `getAnimeDetails prefers usable generated network detail over cache`() = runTest {
        coEvery { animeDao.getById(52991) } returns cachedFrierenEntity()
        withServer(detailResponse("Frieren from AniList")) { _, client ->
            val result = repository(client).getAnimeDetails(52991)

            assertEquals(52991, result?.id)
            assertEquals("Frieren from AniList", result?.title)
        }
    }

    @Test
    fun `getAnimeDetails accepts usable detail beside GraphQL field errors`() = runTest {
        coEvery { animeDao.getById(52991) } returns cachedFrierenEntity()
        withServer(detailResponse("Partial AniList", includeErrors = true)) { _, client ->
            val result = repository(client).getAnimeDetails(52991)

            assertEquals("Partial AniList", result?.title)
        }
    }

    @Test
    fun `getAnimeDetails maps discovery sections from the same response`() = runTest {
        withServer(detailResponseWithDiscoverySections()) { _, client ->
            val result = repository(client).getAnimeDetails(52991)

            assertEquals(1, result?.relations?.size)
            assertEquals("SEQUEL", result?.relations?.single()?.type?.name)
            assertEquals("Frieren Season 2", result?.relations?.single()?.media?.title)
            assertEquals(1, result?.recommendations?.size)
            assertEquals("The Apothecary Diaries", result?.recommendations?.single()?.media?.title)
            assertEquals(720, result?.recommendations?.single()?.votes)
            assertEquals(1, result?.characters?.size)
            assertEquals("Frieren", result?.characters?.single()?.name)
            assertEquals("Atsumi Tanezaki", result?.characters?.single()?.japaneseVoiceActor)
            assertEquals(1, result?.streamingLinks?.size)
            assertEquals("Crunchyroll", result?.streamingLinks?.single()?.site)
        }
    }

    @Test
    fun `getAnimeDetails falls back to cache on GraphQL server failure`() = runTest {
        coEvery { animeDao.getById(52991) } returns cachedFrierenEntity()
        withServer("""{"data":null,"errors":[{"message":"details unavailable"}]}""") { _, client ->
            val result = repository(client).getAnimeDetails(52991)

            assertEquals("Sousou no Frieren (cached)", result?.title)
        }
    }

    @Test
    fun `getAnimeDetails falls back to cache on transport failure`() = runTest {
        coEvery { animeDao.getById(52991) } returns cachedFrierenEntity()
        val server = MockServer.Builder().build()
        val client = ApolloClient.Builder().serverUrl(server.url()).build()
        server.close()
        try {
            val result = repository(client).getAnimeDetails(52991)

            assertEquals("Sousou no Frieren (cached)", result?.title)
        } finally {
            client.close()
        }
    }

    @Test
    fun `getAnimeDetails falls back to cache when AniList media is not found`() = runTest {
        coEvery { animeDao.getById(52991) } returns cachedFrierenEntity()
        withServer("""{"data":{"Media":null}}""") { _, client ->
            val result = repository(client).getAnimeDetails(52991)

            assertEquals("Sousou no Frieren (cached)", result?.title)
        }
    }

    @Test
    fun `getAnimeDetails rethrows cancellation instead of falling back`() = runTest {
        val cancellation = CancellationException("cancel details")
        coEvery { animeDao.getById(52991) } returns cachedFrierenEntity()
        val client = ApolloClient.Builder()
            .serverUrl("https://example.test/graphql")
            .addInterceptor(CancellingInterceptor(cancellation))
            .build()
        try {
            val thrown = try {
                repository(client).getAnimeDetails(52991)
                fail("Expected cancellation")
                null
            } catch (error: CancellationException) {
                error
            }

            assertEquals("cancel details", thrown?.message)
        } finally {
            client.close()
        }
    }

    @Test
    fun `getAnimeGenres keeps case distinct values sorts ignoring case and caches result`() =
        runTest {
            withServer(
                """{"data":{"genres":["drama","Action","action","Action","  ",""]}}"""
            ) { _, client ->
                val repository = repository(client)

                val first = repository.getAnimeGenres()
                val second = repository.getAnimeGenres()

                assertEquals(listOf("Action", "action", "drama"), first.map { it.name })
                assertEquals(first, second)
            }
        }

    @Test
    fun `concurrent genre cache misses share one Apollo request`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val interceptor = GenreGateInterceptor(
            responses = ArrayDeque(
                listOf(GatedGenres(listOf("Action"), started, release))
            )
        )
        val client = clientWith(interceptor)
        try {
            val repository = repository(client)
            val requests = List(10) {
                async(start = CoroutineStart.UNDISPATCHED) {
                    repository.getAnimeGenres()
                }
            }

            started.await()
            assertEquals(1, interceptor.calls)
            release.complete(Unit)
            assertEquals(
                List(10) { listOf("Action") },
                requests.awaitAll().map { genres -> genres.map { it.name } }
            )
            assertEquals(1, interceptor.calls)
        } finally {
            client.close()
        }
    }

    @Test
    fun `concurrent forced genre refreshes share one new Apollo request`() = runTest {
        val refreshStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val interceptor = GenreGateInterceptor(
            responses = ArrayDeque(
                listOf(
                    GatedGenres(listOf("Cached")),
                    GatedGenres(listOf("Refreshed"), refreshStarted, release)
                )
            )
        )
        val client = clientWith(interceptor)
        try {
            val repository = repository(client)
            repository.getAnimeGenres()

            val first = async(start = CoroutineStart.UNDISPATCHED) {
                repository.getAnimeGenres(forceRefresh = true)
            }
            refreshStarted.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                repository.getAnimeGenres(forceRefresh = true)
            }

            assertEquals(2, interceptor.calls)
            release.complete(Unit)
            assertEquals(
                listOf(listOf("Refreshed"), listOf("Refreshed")),
                awaitAll(first, second).map { genres -> genres.map { it.name } }
            )
            assertEquals(2, interceptor.calls)
        } finally {
            client.close()
        }
    }

    @Test
    fun `completed forced genre refresh allows a later forced request`() = runTest {
        withServer(
            """{"data":{"genres":["Cached"]}}""",
            """{"data":{"genres":["First refresh"]}}""",
            """{"data":{"genres":["Second refresh"]}}"""
        ) { _, client ->
            val repository = repository(client)
            repository.getAnimeGenres()

            val first = repository.getAnimeGenres(forceRefresh = true)
            val second = repository.getAnimeGenres(forceRefresh = true)

            assertEquals(listOf("First refresh"), first.map { it.name })
            assertEquals(listOf("Second refresh"), second.map { it.name })
        }
    }

    @Test
    fun `failed forced refresh preserves good genre cache and releases in flight state`() =
        runTest {
            withServer(
                """{"data":{"genres":["Cached"]}}""",
                """{"data":null,"errors":[{"message":"genre failure"}]}""",
                """{"data":{"genres":["Recovered"]}}"""
            ) { _, client ->
                val repository = repository(client)
                repository.getAnimeGenres()

                try {
                    repository.getAnimeGenres(forceRefresh = true)
                    fail("Expected failed forced refresh")
                } catch (_: RuntimeException) {
                    // The prior snapshot remains available and a later force refresh can own a new call.
                }

                assertEquals(listOf("Cached"), repository.getAnimeGenres().map { it.name })
                assertEquals(
                    listOf("Recovered"),
                    repository.getAnimeGenres(forceRefresh = true).map { it.name }
                )
            }
        }

    @Test
    fun `empty forced refresh preserves a valid genre cache`() = runTest {
        withServer(
            """{"data":{"genres":["Cached"]}}""",
            """{"data":{"genres":[]}}"""
        ) { _, client ->
            val repository = repository(client)
            repository.getAnimeGenres()

            try {
                repository.getAnimeGenres(forceRefresh = true)
                fail("Expected empty catalog failure")
            } catch (_: IllegalStateException) {
                // The previous valid snapshot must remain cached.
            }

            assertEquals(listOf("Cached"), repository.getAnimeGenres().map { it.name })
        }
    }

    @Test
    fun `mutating returned genres does not corrupt the cached snapshot`() = runTest {
        withServer("""{"data":{"genres":["Adventure","Action"]}}""") { _, client ->
            val repository = repository(client)
            val exposed = repository.getAnimeGenres() as MutableList<AnimeGenre>
            exposed[0] = AnimeGenre("Corrupted")

            assertEquals(
                listOf("Action", "Adventure"),
                repository.getAnimeGenres().map { it.name }
            )
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `empty genre response is not accepted as a valid catalog`() = runTest {
        withServer("""{"data":{"genres":[]}}""") { _, client ->
            repository(client).getAnimeGenres()
        }
    }

    private fun repository(client: ApolloClient) =
        AnimeRepository(client, db, favoriteDao)

    private suspend fun withServer(
        vararg responseBodies: String,
        block: suspend (MockServer, ApolloClient) -> Unit
    ) {
        val server = MockServer.Builder().build()
        val client = ApolloClient.Builder().serverUrl(server.url()).build()
        try {
            responseBodies.forEach { body ->
                server.enqueue(MockResponse.Builder().body(body).build())
            }
            block(server, client)
        } finally {
            client.close()
            server.close()
        }
    }

    private fun clientWith(interceptor: ApolloInterceptor): ApolloClient =
        ApolloClient.Builder()
            .serverUrl("https://example.test/graphql")
            .addInterceptor(interceptor)
            .build()

    private fun detailResponse(title: String, includeErrors: Boolean = false): String =
        """
        {
          "data": {
            "Media": {
              "__typename": "Media",
              "id": 52991,
              "title": {"english": "$title", "romaji": "Sousou no Frieren"},
              "coverImage": {
                "extraLarge": "https://example.com/frieren.jpg",
                "large": "https://example.com/frieren-large.jpg"
              },
              "averageScore": 92,
              "episodes": 28,
              "format": "TV",
              "seasonYear": 2023,
              "description": "Fresh synopsis",
              "genres": ["Adventure", "Fantasy"],
              "studios": {"nodes": [{"name": "Madhouse"}]},
              "status": "FINISHED",
              "duration": 24,
              "startDate": {"year": 2023, "month": 9, "day": 29},
              "endDate": {"year": 2024, "month": 3, "day": 22},
              "trailer": {"id": "trailer-id", "site": "youtube"},
              "rankings": [{"rank": 1, "type": "RATED", "allTime": true}]
            }
          }
          ${if (includeErrors) ""","errors":[{"message":"optional field failed"}]""" else ""}
        }
        """.trimIndent()

    private fun detailResponseWithDiscoverySections(): String =
        detailResponse("Frieren from AniList").replace(
            """"rankings": [{"rank": 1, "type": "RATED", "allTime": true}]""",
            """
            "rankings": [{"rank": 1, "type": "RATED", "allTime": true}],
            "relations": {
              "edges": [{
                "relationType": "SEQUEL",
                "node": {
                  "__typename": "Media",
                  "type": "ANIME",
                  "id": 154587,
                  "title": {"english": "Frieren Season 2", "romaji": null},
                  "coverImage": {"extraLarge": "https://example.com/frieren-2.jpg", "large": null},
                  "averageScore": 90,
                  "episodes": null,
                  "format": "TV",
                  "seasonYear": 2026,
                  "isAdult": false
                }
              }]
            },
            "recommendations": {
              "nodes": [{
                "rating": 720,
                "mediaRecommendation": {
                  "__typename": "Media",
                  "type": "ANIME",
                  "id": 161645,
                  "title": {"english": "The Apothecary Diaries", "romaji": null},
                  "coverImage": {"extraLarge": "https://example.com/apothecary.jpg", "large": null},
                  "averageScore": 89,
                  "episodes": 24,
                  "format": "TV",
                  "seasonYear": 2023,
                  "isAdult": false
                }
              }]
            },
            "characters": {
              "edges": [{
                "role": "MAIN",
                "node": {
                  "id": 176754,
                  "name": {"full": "Frieren"},
                  "image": {
                    "large": "https://example.com/character-frieren.jpg",
                    "medium": null
                  }
                },
                "voiceActors": [{
                  "id": 95027,
                  "name": {"full": "Atsumi Tanezaki"}
                }]
              }]
            },
            "externalLinks": [{
              "id": 1,
              "url": "https://www.crunchyroll.com/frieren",
              "site": "Crunchyroll",
              "type": "STREAMING",
              "language": "English",
              "icon": "https://example.com/crunchyroll.png",
              "notes": null,
              "isDisabled": false
            }, {
              "id": 2,
              "url": "https://example.com/disabled",
              "site": "Disabled service",
              "type": "STREAMING",
              "language": null,
              "icon": null,
              "notes": null,
              "isDisabled": true
            }]
            """.trimIndent()
        )

    private fun frieren() = Anime(
        id = 52991,
        title = "Sousou no Frieren",
        imageUrl = "https://example.com/frieren.jpg",
        score = 9.2,
        episodes = 28,
        type = "TV",
        year = 2023,
        synopsis = null
    )

    private fun cachedFrierenEntity() = AnimeEntity(
        id = 52991,
        title = "Sousou no Frieren (cached)",
        imageUrl = "https://example.com/cached.jpg",
        score = 9.2,
        episodes = 28,
        type = "TV",
        year = 2023,
        synopsis = "Cached synopsis",
        genres = listOf("Fantasy"),
        studios = listOf("Cached Studio"),
        aired = "2023 - 2024",
        status = "Finished",
        rating = null,
        duration = "24 min per ep",
        rank = 1,
        trailerYoutubeId = null,
        pageIndex = 0
    )

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

    private data class GatedGenres(
        val genres: List<String>,
        val started: CompletableDeferred<Unit>? = null,
        val release: CompletableDeferred<Unit>? = null
    )

    private class GenreGateInterceptor(
        private val responses: ArrayDeque<GatedGenres>
    ) : ApolloInterceptor {
        var calls: Int = 0
            private set

        override fun <D : Operation.Data> intercept(
            request: ApolloRequest<D>,
            chain: ApolloInterceptorChain
        ): Flow<ApolloResponse<D>> = flow {
            calls += 1
            val response = responses.removeFirst()
            response.started?.complete(Unit)
            response.release?.await()
            @Suppress("UNCHECKED_CAST")
            val data = GenreCollectionQuery.Data(response.genres) as D
            emit(
                ApolloResponse.Builder(request.operation, request.requestUuid)
                    .data(data)
                    .build()
            )
        }
    }
}
