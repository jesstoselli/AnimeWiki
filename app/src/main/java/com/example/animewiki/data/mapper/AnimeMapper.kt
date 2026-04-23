package com.example.animewiki.data.mapper

import com.example.animewiki.data.remote.dto.AnimeDto
import com.example.animewiki.domain.model.Anime

fun AnimeDto.toDomain(): Anime? {
    val id = malId ?: return null
    val title = title ?: return null
    val imageUrl = images?.jpg?.largeImageUrl
        ?: images?.jpg?.imageUrl
        ?: return null

    return Anime(
        id = id,
        title = title,
        imageUrl = imageUrl,
        score = score,
        episodes = episodes,
        type = type,
        year = year,
        synopsis = synopsis
    )
}