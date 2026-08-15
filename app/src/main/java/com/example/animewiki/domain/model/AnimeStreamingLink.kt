package com.example.animewiki.domain.model

data class AnimeStreamingLink(
    val id: Int,
    val site: String,
    val url: String,
    val iconUrl: String?,
    val language: String?,
    val notes: String?
)
