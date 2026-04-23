package com.example.animewiki.domain.model

data class Anime(
    val id: Int,
    val title: String,
    val imageUrl: String,
    val score: Double?,
    val episodes: Int?,
    val type: String?,
    val year: Int?,
    val synopsis: String?
)