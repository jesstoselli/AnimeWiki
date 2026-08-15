package com.example.animewiki.domain

import com.example.animewiki.domain.model.AnimeSeason

data class SeasonYear(val season: AnimeSeason, val year: Int)

object SeasonResolver {
    fun current(year: Int, month: Int): SeasonYear {
        val season = when (month) {
            1, 2, 3 -> AnimeSeason.WINTER
            4, 5, 6 -> AnimeSeason.SPRING
            7, 8, 9 -> AnimeSeason.SUMMER
            10, 11, 12 -> AnimeSeason.FALL
            else -> throw IllegalArgumentException("month must be 1..12, was $month")
        }
        return SeasonYear(season, year)
    }
}
