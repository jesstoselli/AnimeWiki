package com.example.animewiki.ui.screens.topAnime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.animewiki.data.repository.AnimeRepository
import com.example.animewiki.domain.model.Anime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class TopAnimeViewModel @Inject constructor(
    repository: AnimeRepository
) : ViewModel() {
    val topAnime: Flow<PagingData<Anime>> =
        repository.topAnime().cachedIn(viewModelScope)
}