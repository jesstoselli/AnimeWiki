package com.example.animewiki.domain

import com.example.animewiki.domain.model.AnimeSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SeasonResolverTest {

    @Test
    fun `winter spans january to march`() {
        listOf(1, 2, 3).forEach { month ->
            assertEquals(
                SeasonYear(AnimeSeason.WINTER, 2026),
                SeasonResolver.current(2026, month)
            )
        }
    }

    @Test
    fun `spring summer fall boundaries`() {
        assertEquals(AnimeSeason.SPRING, SeasonResolver.current(2026, 4).season)
        assertEquals(AnimeSeason.SPRING, SeasonResolver.current(2026, 6).season)
        assertEquals(AnimeSeason.SUMMER, SeasonResolver.current(2026, 7).season)
        assertEquals(AnimeSeason.SUMMER, SeasonResolver.current(2026, 9).season)
        assertEquals(AnimeSeason.FALL, SeasonResolver.current(2026, 10).season)
        assertEquals(AnimeSeason.FALL, SeasonResolver.current(2026, 12).season)
    }

    @Test
    fun `year is carried through unchanged`() {
        assertEquals(2031, SeasonResolver.current(2031, 8).year)
    }

    @Test
    fun `month out of range is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SeasonResolver.current(2026, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SeasonResolver.current(2026, 13)
        }
    }
}
