package com.example.animewiki.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnimeGenreListResponseDto(
    val data: List<AnimeGenreDto>? = null
)

@Serializable
data class AnimeGenreDto(
    @SerialName("mal_id") val malId: Int? = null,
    val name: String? = null,
    val count: Int? = null
)
