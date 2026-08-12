package com.example.animewiki.ui.screens.topAnime

import com.example.animewiki.domain.model.AnimeOrganization

sealed interface AnimeOrganizationsState {
    data object Idle : AnimeOrganizationsState
    data object Loading : AnimeOrganizationsState
    data class Content(val organizations: List<AnimeOrganization>) : AnimeOrganizationsState
    data class Error(val cause: Throwable) : AnimeOrganizationsState
}
