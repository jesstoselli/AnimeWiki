package com.example.animewiki.data.mapper

import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.data.remote.dto.AnimeDto
import com.example.animewiki.domain.model.Anime

fun AnimeDto.toEntity(pageIndex: Int): AnimeEntity? {
    val id = malId
    val title = title
    val imageUrl = images?.jpg?.largeImageUrl
        ?: images?.jpg?.imageUrl
        ?: return null
    return AnimeEntity(
        id = id,
        title = title,
        imageUrl = imageUrl,
        score = score,
        episodes = episodes,
        type = type,
        year = year,
        synopsis = synopsis,
        genres = genres.orEmpty().mapNotNull { it.name },
        studios = studios.orEmpty().mapNotNull { it.name },
        aired = aired?.string,
        status = status,
        rating = rating,
        duration = duration,
        rank = rank,
        trailerYoutubeId = trailer?.youtubeId,
        pageIndex = pageIndex
    )
}

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
        synopsis = synopsis,
        genres = genres.orEmpty().mapNotNull { it.name },
        studios = studios.orEmpty().mapNotNull { it.name },
        aired = aired?.string,
        status = status,
        rating = rating,
        duration = duration,
        rank = rank,
        trailerYoutubeId = trailer?.youtubeId
    )
}

fun AnimeEntity.toDomain(): Anime = Anime(
    id = id,
    title = title,
    imageUrl = imageUrl,
    score = score,
    episodes = episodes,
    type = type,
    year = year,
    synopsis = synopsis,
    genres = genres,
    studios = studios,
    aired = aired,
    status = status,
    rating = rating,
    duration = duration,
    rank = rank,
    trailerYoutubeId = trailerYoutubeId
)
