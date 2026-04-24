package com.example.animewiki.data.repository

import com.example.animewiki.data.local.AppDatabase
import com.example.animewiki.data.local.dao.AnimeDao
import com.example.animewiki.data.local.dao.FavoriteDao
import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.data.local.entity.FavoriteEntity
import com.example.animewiki.data.remote.JikanApi
import com.example.animewiki.data.remote.dto.AnimeDetailsResponseDto
import com.example.animewiki.data.remote.dto.AnimeDto
import com.example.animewiki.data.remote.dto.AnimeImageUrlsDto
import com.example.animewiki.data.remote.dto.AnimeImagesDto
import com.example.animewiki.domain.model.Anime
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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
