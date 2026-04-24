package com.example.animewiki.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.animewiki.data.preferences.repository.UserPreferencesRepository
import com.example.animewiki.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    preferences: UserPreferencesRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = preferences.preferences
        .map { it.themeMode }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly, // checks for user preferences on app boot
            initialValue = ThemeMode.SYSTEM
        )
}
