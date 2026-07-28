package com.example.animewiki.data.mapper

import com.example.animewiki.data.remote.dto.AnimeGenreDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnimeGenreMapperTest {
    @Test
    fun `valid genre maps to domain`() {
        val result = AnimeGenreDto(malId = 1, name = "Action", count = 5310).toDomain()

        assertEquals("Action", result?.name)
    }

    @Test
    fun `genre identity only requires a nonblank name`() {
        assertEquals("Action", AnimeGenreDto(malId = null, name = " Action ").toDomain()?.name)
        assertNull(AnimeGenreDto(malId = 1, name = "  ").toDomain())
    }
}
