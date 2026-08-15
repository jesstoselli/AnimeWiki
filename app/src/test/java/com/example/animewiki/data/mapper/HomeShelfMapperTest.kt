package com.example.animewiki.data.mapper

import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.data.local.entity.HomeShelfItemEntity
import com.example.animewiki.domain.model.AnimeSeason
import com.example.animewiki.domain.model.HomeShelf
import com.example.animewiki.domain.model.HomeShelfAnime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeShelfMapperTest {

    private fun shelfAnime() = HomeShelfAnime(
        id = 1,
        title = "Frieren",
        imageUrl = "https://img/1.jpg",
        score = 9.1,
        season = AnimeSeason.FALL,
        year = 2026,
        rank = null,
        nextEpisode = 6,
        nextAiringAtSeconds = 1_700_000_000L
    )

    @Test
    fun `domain to entity carries shelf key and position`() {
        val entity = shelfAnime().toEntity(HomeShelf.THIS_SEASON, position = 3)

        assertEquals("THIS_SEASON", entity.shelf)
        assertEquals(3, entity.position)
        assertEquals(1, entity.id)
        assertEquals("FALL", entity.season)
        assertEquals(6, entity.nextEpisode)
        assertEquals(1_700_000_000L, entity.nextAiringAtSeconds)
    }

    @Test
    fun `entity round-trips back to domain`() {
        val restored = shelfAnime().toEntity(HomeShelf.UPCOMING, 0).toShelfAnime()

        assertEquals(shelfAnime(), restored)
    }

    @Test
    fun `unknown stored season maps to null instead of crashing`() {
        val entity = HomeShelfItemEntity(
            shelf = "TRENDING", position = 0, id = 2, title = "X",
            imageUrl = "https://img/2.jpg", score = null, season = "GARBAGE",
            year = null, rank = null, nextEpisode = null, nextAiringAtSeconds = null
        )

        assertNull(entity.toShelfAnime().season)
    }

    @Test
    fun `anime ranking entity maps to a shelf anime with its rank`() {
        val entity = AnimeEntity(
            id = 7, title = "Top One", imageUrl = "https://img/7.jpg", score = 9.5,
            episodes = 12, type = "TV", year = 2025, synopsis = null,
            genres = emptyList(), studios = emptyList(), aired = null, status = null,
            rating = null, duration = null, rank = 1, trailerYoutubeId = null,
            pageIndex = 0
        )

        val mapped = entity.toShelfAnime()

        assertEquals(7, mapped.id)
        assertEquals(1, mapped.rank)
        assertEquals(9.5, mapped.score)
    }
}
