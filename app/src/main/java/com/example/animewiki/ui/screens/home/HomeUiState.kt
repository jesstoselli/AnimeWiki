package com.example.animewiki.ui.screens.home

import com.example.animewiki.domain.model.HomeShelf
import com.example.animewiki.domain.model.HomeShelfAnime

sealed interface ShelfState {
    data object Loading : ShelfState
    data class Content(val items: List<HomeShelfAnime>) : ShelfState
    data object Error : ShelfState
}

typealias HomeUiState = Map<HomeShelf, ShelfState>
