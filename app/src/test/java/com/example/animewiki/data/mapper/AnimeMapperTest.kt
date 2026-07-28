package com.example.animewiki.data.mapper

import com.example.animewiki.data.local.entity.AnimeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnimeMapperTest {

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

}
