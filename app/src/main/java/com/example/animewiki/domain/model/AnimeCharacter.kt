package com.example.animewiki.domain.model

data class AnimeCharacter(
    val id: Int,
    val name: String,
    val imageUrl: String,
    val role: AnimeCharacterRole,
    val japaneseVoiceActor: String?
)
