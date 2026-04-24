package com.example.animewiki.data.mapper

import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.data.remote.dto.AnimeDto
import com.example.animewiki.data.remote.dto.AnimeImageUrlsDto
import com.example.animewiki.data.remote.dto.AnimeImagesDto
import com.example.animewiki.data.remote.dto.NamedEntityDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AnimeMapperTest {

    @Test
    fun `AnimeDto toDomain maps all primary fields`() {
        val dto = frierenDto()

        val domain = dto.toDomain()

        assertNotNull(domain)
        assertEquals(52991, domain!!.id)
        assertEquals("Sousou no Frieren", domain.title)
        assertEquals(9.27, domain.score!!, 0.001)
        assertEquals(28, domain.episodes)
        assertEquals("TV", domain.type)
        assertEquals(2023, domain.year)
    }

    @Test
    fun `AnimeDto toDomain extracts genre names from nested objects`() {
        val dto = frierenDto().copy(
            genres = listOf(
                NamedEntityDto(malId = 1, name = "Adventure"),
                NamedEntityDto(malId = 10, name = "Fantasy"),
                NamedEntityDto(malId = 999, name = null) // should be filtered out
            )
        )

        val domain = dto.toDomain()!!

        assertEquals(listOf("Adventure", "Fantasy"), domain.genres)
    }

    @Test
    fun `AnimeDto toDomain prefers largeImageUrl over default imageUrl`() {
        val dto = frierenDto().copy(
            images = AnimeImagesDto(
                jpg = AnimeImageUrlsDto(
                    imageUrl = "https://example.com/small.jpg",
                    largeImageUrl = "https://example.com/large.jpg"
                )
            )
        )

        val domain = dto.toDomain()!!

        assertEquals("https://example.com/large.jpg", domain.imageUrl)
    }

    @Test
    fun `AnimeEntity toDomain preserves all stored fields`() {
        val entity = AnimeEntity(
            id = 42,
            title = "Fullmetal Alchemist: Brotherhood",
            imageUrl = "https://example.com/fmab.jpg",
            score = 9.11,
            episodes = 64,
            type = "TV",
            year = 2009,
            synopsis = "Two brothers seek the Philosopher's Stone.",
            genres = listOf("Action", "Adventure", "Drama"),
            studios = listOf("Bones"),
            aired = "Apr 5, 2009 to Jul 4, 2010",
            status = "Finished Airing",
            rating = "PG-13",
            duration = "24 min per ep",
            rank = 3,
            trailerYoutubeId = null,
            pageIndex = 2
        )

        val domain = entity.toDomain()

        assertEquals(42, domain.id)
        assertEquals("Fullmetal Alchemist: Brotherhood", domain.title)
        assertEquals(listOf("Bones"), domain.studios)
        assertEquals(3, domain.rank)
        assertNull(domain.trailerYoutubeId)
    }

    @Test
    fun `AnimeDto toEntity uses provided pageIndex for ordering`() {
        val dto = frierenDto()

        val entity = dto.toEntity(pageIndex = 7)

        assertNotNull(entity)
        assertEquals(7, entity!!.pageIndex)
        assertEquals("Sousou no Frieren", entity.title)
    }

    // --- helpers ---

    private fun frierenDto(): AnimeDto = AnimeDto(
        malId = 52991,
        title = "Sousou no Frieren",
        type = "TV",
        episodes = 28,
        score = 9.27,
        year = 2023,
        images = AnimeImagesDto(
            jpg = AnimeImageUrlsDto(
                imageUrl = "https://example.com/frieren.jpg",
                largeImageUrl = "https://example.com/frieren-large.jpg"
            )
        ),
        synopsis = "Frieren lives for centuries...",
        genres = listOf(NamedEntityDto(malId = 1, name = "Adventure"))
    )
}
