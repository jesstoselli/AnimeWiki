package com.example.animewiki.domain.model

data class Anime(
    val id: Int,
    val title: String,
    val imageUrl: String,
    val score: Double?,
    val episodes: Int?,
    val type: String?,
    val year: Int?,
    val synopsis: String?,
    val genres: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    val aired: String? = null,
    val status: String? = null,
    val rating: String? = null,
    val duration: String? = null,
    val rank: Int? = null,
    val trailerYoutubeId: String? = null
)
