package com.example.animewiki.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnimeGenreDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `genre response tolerates missing fields`() {
        val response = json.decodeFromString<AnimeGenreListResponseDto>(
            """{"data":[{"mal_id":1,"name":"Action","count":5310},{}]}"""
        )

        assertEquals(2, response.data?.size)
        assertNull(response.data?.get(1)?.malId)
    }
}
