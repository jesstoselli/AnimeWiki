package com.example.animewiki.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.animewiki.data.notification.NotificationScheduler
import com.example.animewiki.data.preferences.repository.UserPreferencesRepository
import com.example.animewiki.domain.model.ThemeMode
import com.example.animewiki.domain.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: UserPreferencesRepository,
    private val scheduler: NotificationScheduler
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = repository.preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferences()
        )

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun onNotificationsToggle(enabled: Boolean) {
        viewModelScope.launch {
            repository.setNotificationsEnabled(enabled)
            if (enabled) scheduler.scheduleWeekly() else scheduler.cancel()
        }
    }

    fun onTestNotification() {
        scheduler.scheduleNowForTesting()
    }
}
