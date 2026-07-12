package com.example.animewiki.data.remote.dto

import com.example.animewiki.data.mapper.toEntity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A single malformed record in the Jikan payload must not blow up the whole page.
 * kotlinx.serialization fails the entire List<AnimeDto> if one element violates a
 * non-nullable field, which surfaces as a MediatorResult.Error → misleading "offline" banner.
 */
class AnimeDtoDeserializationTest {

    // Mirrors NetworkModule.provideJson()
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `list with a record missing images still deserializes`() {
        val payload = """
            {
              "pagination": { "last_visible_page": 10, "has_next_page": true, "current_page": 1 },
              "data": [
                {
                  "mal_id": 1,
                  "title": "Good One",
                  "images": { "jpg": { "image_url": "https://example.com/good.jpg" } }
                },
                {
                  "mal_id": 2,
                  "title": "Missing Images"
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<AnimeListResponseDto>(payload)

        assertEquals(2, response.data?.size)
        // Good record maps to a real entity; the broken one is skipped (null), not crashing.
        assertNotNull(response.data!![0].toEntity(pageIndex = 0))
        assertNull(response.data!![1].toEntity(pageIndex = 1))
    }

    @Test
    fun `response missing pagination still deserializes`() {
        val payload = """
            {
              "data": [
                {
                  "mal_id": 1,
                  "title": "Good One",
                  "images": { "jpg": { "image_url": "https://example.com/good.jpg" } }
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<AnimeListResponseDto>(payload)

        assertNull(response.pagination)
        assertEquals(1, response.data?.size)
    }
}
