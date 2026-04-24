package com.example.animewiki.data.mapper

import com.example.animewiki.data.local.entity.FavoriteEntity
import com.example.animewiki.domain.model.Anime

fun Anime.toFavoriteEntity(): FavoriteEntity = FavoriteEntity(
    id = id,
    title = title,
    imageUrl = imageUrl,
    score = score,
    year = year,
    type = type
)

fun FavoriteEntity.toDomain(): Anime = Anime(
    id = id,
    title = title,
    imageUrl = imageUrl,
    score = score,
    episodes = null,
    type = type,
    year = year,
    synopsis = null
)
