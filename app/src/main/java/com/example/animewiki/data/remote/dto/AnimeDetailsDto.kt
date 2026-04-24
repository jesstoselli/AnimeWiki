package com.example.animewiki.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnimeDetailsResponseDto(
    @SerialName("data") val data: AnimeDto? = null
)

@Serializable
data class NamedEntityDto(
    @SerialName("mal_id") val malId: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("url") val url: String? = null
)

@Serializable
data class AiredDto(
    @SerialName("string") val string: String? = null
)

@Serializable
data class TrailerDto(
    @SerialName("youtube_id") val youtubeId: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("embed_url") val embedUrl: String? = null
)
