package com.example.animewiki.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.animewiki.data.repository.HomeShelfRepository
import com.example.animewiki.domain.model.HomeShelf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeShelfRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(
        HomeShelf.entries.associateWith { ShelfState.Loading }
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        HomeShelf.entries.forEach { shelf ->
            observe(shelf)
            refresh(shelf)
        }
    }

    fun retry(shelf: HomeShelf) = refresh(shelf)

    private fun observe(shelf: HomeShelf) {
        viewModelScope.launch {
            repository.observe(shelf).collect { items ->
                if (items.isNotEmpty()) set(shelf, ShelfState.Content(items))
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun refresh(shelf: HomeShelf) {
        viewModelScope.launch {
            if (!hasContent(shelf)) set(shelf, ShelfState.Loading)
            try {
                repository.refresh(shelf)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!hasContent(shelf)) set(shelf, ShelfState.Error)
            }
        }
    }

    private fun hasContent(shelf: HomeShelf) = _uiState.value[shelf] is ShelfState.Content

    private fun set(shelf: HomeShelf, state: ShelfState) {
        _uiState.update { current -> current + (shelf to state) }
    }
}
