package com.example.animewiki.data.repository

import com.example.animewiki.data.local.AppDatabase
import com.example.animewiki.data.local.dao.AnimeDao
import com.example.animewiki.data.local.dao.FavoriteDao
import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.data.remote.JikanApi
import com.example.animewiki.data.remote.dto.AnimeDetailsResponseDto
import com.example.animewiki.data.remote.dto.AnimeDto
import com.example.animewiki.data.remote.dto.AnimeGenreDto
import com.example.animewiki.data.remote.dto.AnimeGenreListResponseDto
import com.example.animewiki.data.remote.dto.AnimeImageUrlsDto
import com.example.animewiki.data.remote.dto.AnimeImagesDto
import com.example.animewiki.domain.model.Anime
import com.example.animewiki.domain.model.AnimeGenre
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class AnimeRepositoryTest {

    private val api: JikanApi = mockk()
    private val animeDao: AnimeDao = mockk(relaxed = true)
    private val favoriteDao: FavoriteDao = mockk(relaxed = true)
    private val db: AppDatabase = mockk {
        every { animeDao() } returns this@AnimeRepositoryTest.animeDao
    }

    private lateinit var repository: AnimeRepository

    @Before
    fun setUp() {
        repository = AnimeRepository(api, db, favoriteDao)
    }

    @Test
    fun `toggleFavorite inserts when anime is not yet favorite`() = runTest {
        val anime = frieren()

        repository.toggleFavorite(anime, isCurrentlyFavorite = false)

        coVerify(exactly = 1) { favoriteDao.insert(match { it.id == 52991 }) }
        coVerify(exactly = 0) { favoriteDao.deleteById(any()) }
    }

    @Test
    fun `toggleFavorite deletes when anime is currently favorite`() = runTest {
        val anime = frieren()

        repository.toggleFavorite(anime, isCurrentlyFavorite = true)

        coVerify(exactly = 1) { favoriteDao.deleteById(52991) }
        coVerify(exactly = 0) { favoriteDao.insert(any()) }
    }

    @Test
    fun `getAnimeDetails falls back to cached entity when network fails`() = runTest {
        coEvery { animeDao.getById(52991) } returns cachedFrierenEntity()
        coEvery { api.getAnimeDetails(52991) } throws IOException("offline")

        val result = repository.getAnimeDetails(52991)

        assertEquals(52991, result?.id)
        assertEquals("Sousou no Frieren (cached)", result?.title)
    }

    @Test
    fun `getAnimeDetails prefers fresh network data over cache`() = runTest {
        coEvery { animeDao.getById(52991) } returns cachedFrierenEntity()
        coEvery { api.getAnimeDetails(52991) } returns AnimeDetailsResponseDto(
            data = frierenDto()
        )

        val result = repository.getAnimeDetails(52991)

        assertEquals("Sousou no Frieren", result?.title) // network title, not cached
    }

    @Test
    fun `getAnimeGenres maps sorts and caches valid genres`() = runTest {
        coEvery { api.getAnimeGenres() } returns AnimeGenreListResponseDto(
            data = listOf(
                AnimeGenreDto(malId = 2, name = "Adventure", count = 20),
                AnimeGenreDto(malId = null, name = "Invalid"),
                AnimeGenreDto(malId = 1, name = "Action", count = 30)
            )
        )

        val first = repository.getAnimeGenres()
        val second = repository.getAnimeGenres()

        assertEquals(listOf("Action", "Adventure"), first.map { it.name })
        assertEquals(first, second)
        coVerify(exactly = 1) { api.getAnimeGenres() }
    }

    @Test
    fun `concurrent genre cache misses share one remote request`() = runTest {
        withTimeout(1_000) {
            val releaseRemoteCall = CompletableDeferred<Unit>()
            var remoteCalls = 0
            coEvery { api.getAnimeGenres() } coAnswers {
                remoteCalls += 1
                releaseRemoteCall.await()
                AnimeGenreListResponseDto(
                    data = listOf(AnimeGenreDto(malId = 1, name = "Action", count = 30))
                )
            }

            val requests = List(10) {
                async(start = CoroutineStart.UNDISPATCHED) { repository.getAnimeGenres() }
            }

            assertEquals(1, remoteCalls)
            releaseRemoteCall.complete(Unit)

            assertEquals(
                List(10) { listOf("Action") },
                requests.awaitAll().map { it.map { genre -> genre.name } }
            )
            coVerify(exactly = 1) { api.getAnimeGenres() }
        }
    }

    @Test
    fun `mutating returned genres does not corrupt the cached catalog`() = runTest {
        coEvery { api.getAnimeGenres() } returns AnimeGenreListResponseDto(
            data = listOf(
                AnimeGenreDto(malId = 2, name = "Adventure", count = 20),
                AnimeGenreDto(malId = 1, name = "Action", count = 30)
            )
        )

        val exposedGenres = repository.getAnimeGenres() as MutableList<AnimeGenre>
        exposedGenres[0] = AnimeGenre(id = 99, name = "Corrupted", count = null)

        assertEquals(listOf("Action", "Adventure"), repository.getAnimeGenres().map { it.name })
        coVerify(exactly = 1) { api.getAnimeGenres() }
    }

    @Test(expected = IllegalStateException::class)
    fun `empty genre response is not accepted as a valid catalog`() = runTest {
        coEvery { api.getAnimeGenres() } returns AnimeGenreListResponseDto(data = emptyList())

        repository.getAnimeGenres()
    }

    // --- helpers ---

    private fun frieren() = Anime(
        id = 52991,
        title = "Sousou no Frieren",
        imageUrl = "https://example.com/frieren.jpg",
        score = 9.27,
        episodes = 28,
        type = "TV",
        year = 2023,
        synopsis = null
    )

    private fun cachedFrierenEntity() = AnimeEntity(
        id = 52991,
        title = "Sousou no Frieren (cached)",
        imageUrl = "https://example.com/cached.jpg",
        score = 9.27,
        episodes = 28,
        type = "TV",
        year = 2023,
        synopsis = null,
        genres = emptyList(),
        studios = emptyList(),
        aired = null,
        status = null,
        rating = null,
        duration = null,
        rank = 1,
        trailerYoutubeId = null,
        pageIndex = 0
    )

    private fun frierenDto() = AnimeDto(
        malId = 52991,
        title = "Sousou no Frieren",
        type = "TV",
        episodes = 28,
        score = 9.27,
        year = 2023,
        images = AnimeImagesDto(
            jpg = AnimeImageUrlsDto(imageUrl = "https://example.com/frieren.jpg")
        )
    )
}
