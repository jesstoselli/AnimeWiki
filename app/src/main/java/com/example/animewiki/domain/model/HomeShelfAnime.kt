package com.example.animewiki.domain.model

data class HomeShelfAnime(
    val id: Int,
    val title: String,
    val imageUrl: String,
    val score: Double?,
    val season: AnimeSeason? = null,
    val year: Int? = null,
    val rank: Int? = null,
    val nextEpisode: Int? = null,
    val nextAiringAtSeconds: Long? = null
)
