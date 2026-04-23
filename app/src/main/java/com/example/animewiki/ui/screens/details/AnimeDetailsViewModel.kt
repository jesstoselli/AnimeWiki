package com.example.animewiki.ui.screens.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.animewiki.data.repository.AnimeRepository
import com.example.animewiki.domain.model.Anime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Success(val anime: Anime) : DetailsUiState
    data class Error(val message: String) : DetailsUiState
}

@HiltViewModel
class AnimeDetailsViewModel @Inject constructor(
    private val repository: AnimeRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val animeId: Int = checkNotNull(savedStateHandle["id"])

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    val isFavorite: StateFlow<Boolean> = repository.observeIsFavorite(animeId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = DetailsUiState.Loading
            _uiState.value = try {
                val anime = repository.getAnimeDetails(animeId)
                if (anime != null) DetailsUiState.Success(anime)
                else DetailsUiState.Error("Anime não encontrado")
            } catch (e: Exception) {
                DetailsUiState.Error(e.message ?: "Erro desconhecido")
            }
        }
    }

    fun onToggleFavorite() {
        val currentAnime = (_uiState.value as? DetailsUiState.Success)?.anime ?: return
        viewModelScope.launch {
            repository.toggleFavorite(currentAnime, isFavorite.value)
        }
    }
}