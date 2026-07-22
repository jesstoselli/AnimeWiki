package com.example.animewiki.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeFiltersTest {
    @Test
    fun `api values match Jikan query contract`() {
        assertEquals("tv", AnimeFormat.TV.apiValue)
        assertEquals("movie", AnimeFormat.MOVIE.apiValue)
        assertEquals("pg13", AnimeAgeRating.PG13.apiValue)
        assertEquals("r17", AnimeAgeRating.R17.apiValue)
        assertEquals("r", AnimeAgeRating.R_PLUS.apiValue)
    }

    @Test
    fun `genre query is sorted and null when no genre is selected`() {
        assertEquals("1,10,24", AnimeFilters(genreIds = setOf(24, 1, 10)).genresQuery)
        assertNull(AnimeFilters().genresQuery)
    }

    @Test
    fun `active count includes each selected criterion`() {
        val filters = AnimeFilters(
            format = AnimeFormat.TV,
            rating = AnimeAgeRating.PG13,
            genreIds = setOf(1, 10)
        )

        assertEquals(4, filters.activeCount)
        assertFalse(filters.isEmpty)
    }

    @Test
    fun `criteria trims query and identifies default feed`() {
        val default = AnimeBrowseCriteria.create("   ", AnimeFilters())
        val filtered = AnimeBrowseCriteria.create("  frieren  ", AnimeFilters())

        assertTrue(default.isDefault)
        assertEquals("", default.query)
        assertFalse(filtered.isDefault)
        assertEquals("frieren", filtered.query)
    }
}
