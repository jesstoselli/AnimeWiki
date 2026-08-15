package com.example.animewiki.domain.model

data class AnimeMediaPreview(
    val id: Int,
    val title: String,
    val imageUrl: String,
    val score: Double?,
    val year: Int?,
    val mediaType: String,
    val isAnime: Boolean
)
