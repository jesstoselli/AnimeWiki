package com.example.animewiki.data.mapper

import com.example.animewiki.data.remote.dto.AnimeGenreDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnimeGenreMapperTest {
    @Test
    fun `valid genre maps to domain`() {
        val result = AnimeGenreDto(malId = 1, name = "Action", count = 5310).toDomain()

        assertEquals(1, result?.id)
        assertEquals("Action", result?.name)
        assertEquals(5310, result?.count)
    }

    @Test
    fun `missing id or blank name is skipped`() {
        assertNull(AnimeGenreDto(malId = null, name = "Action").toDomain())
        assertNull(AnimeGenreDto(malId = 1, name = "  ").toDomain())
    }
}
