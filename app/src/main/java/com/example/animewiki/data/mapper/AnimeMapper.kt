package com.example.animewiki.data.mapper

import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.domain.model.Anime

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
