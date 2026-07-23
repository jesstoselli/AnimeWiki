package com.example.animewiki.ui.screens.topAnime

import com.example.animewiki.domain.model.AnimeGenre

sealed interface AnimeGenresState {
    data object Idle : AnimeGenresState
    data object Loading : AnimeGenresState
    data class Content(val genres: List<AnimeGenre>) : AnimeGenresState
    data class Error(val cause: Throwable) : AnimeGenresState
}
