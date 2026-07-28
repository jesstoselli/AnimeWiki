package com.example.animewiki.data.mapper

import com.example.animewiki.data.remote.dto.AnimeGenreDto
import com.example.animewiki.domain.model.AnimeGenre

fun AnimeGenreDto.toDomain(): AnimeGenre? {
    val normalizedName = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return AnimeGenre(name = normalizedName)
}
