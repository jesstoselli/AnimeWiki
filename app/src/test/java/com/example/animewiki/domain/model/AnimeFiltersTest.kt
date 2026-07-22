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

    @Test
    fun `source genre mutations do not change filters or criteria identity`() {
        val sourceGenres = mutableSetOf(24, 1)
        val filters = AnimeFilters(genreIds = sourceGenres)
        val criteria = AnimeBrowseCriteria.create("  frieren  ", filters)
        val filtersBeforeMutation = filters.copy()
        val criteriaBeforeMutation = AnimeBrowseCriteria.create("  frieren  ", filtersBeforeMutation)

        sourceGenres += 10

        assertEquals(setOf(1, 24), filters.genreIds)
        assertEquals("1,24", filters.genresQuery)
        assertEquals(criteriaBeforeMutation, criteria)
        assertEquals("1,24", criteria.filters.genresQuery)
    }

    @Test
    fun `criteria and filters use structural equality`() {
        assertEquals(
            AnimeFilters(format = AnimeFormat.TV, genreIds = setOf(10, 1)),
            AnimeFilters(format = AnimeFormat.TV, genreIds = setOf(1, 10))
        )
        assertEquals(
            AnimeBrowseCriteria.create("frieren", AnimeFilters(genreIds = setOf(1, 10))),
            AnimeBrowseCriteria.create("  frieren  ", AnimeFilters(genreIds = setOf(10, 1)))
        )
    }

    @Test
    fun `filter copy preserves value semantics and accepts replacement values`() {
        val filters = AnimeFilters(format = AnimeFormat.TV, genreIds = setOf(1))

        assertEquals(
            AnimeFilters(rating = AnimeAgeRating.PG13, genreIds = setOf(10)),
            filters.copy(format = null, rating = AnimeAgeRating.PG13, genreIds = setOf(10))
        )
    }
}
