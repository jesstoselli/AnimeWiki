package com.example.animewiki.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeFiltersTest {
    @Test
    fun `format values match AniList query contract`() {
        assertEquals("TV", AnimeFormat.TV.apiValue)
        assertEquals("MOVIE", AnimeFormat.MOVIE.apiValue)
        assertEquals("OVA", AnimeFormat.OVA.apiValue)
        assertEquals("ONA", AnimeFormat.ONA.apiValue)
        assertEquals("SPECIAL", AnimeFormat.SPECIAL.apiValue)
        assertEquals("MUSIC", AnimeFormat.MUSIC.apiValue)
    }

    @Test
    fun `active count includes format adult toggle and each selected genre`() {
        val filters = AnimeFilters(
            format = AnimeFormat.TV,
            includeAdultContent = true,
            genres = setOf("Fantasy", "Action")
        )

        assertEquals(4, filters.activeCount)
        assertEquals(setOf("Action", "Fantasy"), filters.genres)
        assertFalse(filters.isEmpty)
        assertTrue(AnimeFilters().isEmpty)
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
        val sourceGenres = mutableSetOf("Fantasy", "Action")
        val filters = AnimeFilters(genres = sourceGenres)
        val criteria = AnimeBrowseCriteria.create("  frieren  ", filters)
        val filtersBeforeMutation = filters.copy()
        val criteriaBeforeMutation = AnimeBrowseCriteria.create("  frieren  ", filtersBeforeMutation)

        sourceGenres += "Drama"

        assertEquals(setOf("Action", "Fantasy"), filters.genres)
        assertEquals(criteriaBeforeMutation, criteria)
        assertEquals(setOf("Action", "Fantasy"), criteria.filters.genres)
    }

    @Test
    fun `criteria and filters use structural equality`() {
        assertEquals(
            AnimeFilters(format = AnimeFormat.TV, genres = setOf("Fantasy", "Action")),
            AnimeFilters(format = AnimeFormat.TV, genres = setOf("Action", "Fantasy"))
        )
        assertEquals(
            AnimeBrowseCriteria.create(
                "frieren",
                AnimeFilters(genres = setOf("Action", "Fantasy"))
            ),
            AnimeBrowseCriteria.create(
                "  frieren  ",
                AnimeFilters(genres = setOf("Fantasy", "Action"))
            )
        )
    }

    @Test
    fun `filter copy preserves value semantics and accepts replacement values`() {
        val filters = AnimeFilters(format = AnimeFormat.TV, genres = setOf("Action"))

        assertEquals(
            AnimeFilters(includeAdultContent = true, genres = setOf("Fantasy")),
            filters.copy(
                format = null,
                includeAdultContent = true,
                genres = setOf("Fantasy")
            )
        )
    }
}
