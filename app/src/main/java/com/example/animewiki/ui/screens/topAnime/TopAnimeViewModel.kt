package com.example.animewiki.ui.screens.topAnime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.animewiki.data.repository.AnimeRepository
import com.example.animewiki.domain.model.Anime
import com.example.animewiki.domain.model.AnimeBrowseCriteria
import com.example.animewiki.domain.model.AnimeFilters
import com.example.animewiki.domain.model.AnimeOrganization
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class TopAnimeViewModel @Inject constructor(
    private val repository: AnimeRepository
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filters = MutableStateFlow(AnimeFilters())
    val filters: StateFlow<AnimeFilters> = _filters.asStateFlow()
    private val filterCriteria = MutableStateFlow<AnimeBrowseCriteria?>(null)

    private val _genresState = MutableStateFlow<AnimeGenresState>(AnimeGenresState.Idle)
    val genresState: StateFlow<AnimeGenresState> = _genresState.asStateFlow()

    private val _organization = MutableStateFlow<AnimeOrganization?>(null)
    val organization: StateFlow<AnimeOrganization?> = _organization.asStateFlow()
    private val _organizationQuery = MutableStateFlow("")
    val organizationQuery: StateFlow<String> = _organizationQuery.asStateFlow()
    private val _organizationsState =
        MutableStateFlow<AnimeOrganizationsState>(AnimeOrganizationsState.Idle)
    val organizationsState: StateFlow<AnimeOrganizationsState> =
        _organizationsState.asStateFlow()
    private var organizationSearchJob: Job? = null

    private val queryCriteria = _query
        .debounce { query -> if (query.isBlank()) 0L else 400L }
        .map { query -> AnimeBrowseCriteria.create(query, _filters.value) }

    private val criteria = merge(queryCriteria, filterCriteria.filterNotNull())
        .distinctUntilChanged()

    val animeList: Flow<PagingData<Anime>> = combine(criteria, _organization) { q, organization ->
        q to organization
    }.distinctUntilChanged().flatMapLatest { (q, organization) ->
        if (organization != null) {
            repository.organizationAnime(organization, q.filters.sort)
        } else if (q.isDefault) {
            repository.topAnime()
        } else {
            repository.searchAnime(q)
        }
    }
        .cachedIn(viewModelScope)

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun clearQuery() {
        _query.value = ""
    }

    fun applyFilters(filters: AnimeFilters) {
        val applied = if (_organization.value == null) {
            filters
        } else {
            AnimeFilters(sort = filters.sort)
        }
        _filters.value = applied
        filterCriteria.value = AnimeBrowseCriteria.create(_query.value, applied)
    }

    fun clearFilters() {
        applyFilters(AnimeFilters())
    }

    fun removeGenre(name: String) {
        applyFilters(_filters.value.copy(genres = _filters.value.genres - name))
    }

    @Suppress("TooGenericExceptionCaught")
    fun loadGenres(forceRefresh: Boolean = false) {
        if (!forceRefresh && _genresState.value is AnimeGenresState.Content) return
        if (_genresState.value is AnimeGenresState.Loading) return

        viewModelScope.launch {
            _genresState.value = AnimeGenresState.Loading
            _genresState.value = try {
                AnimeGenresState.Content(repository.getAnimeGenres(forceRefresh))
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                AnimeGenresState.Error(error)
            }
        }
    }

    fun retryGenres() = loadGenres(forceRefresh = true)

    fun onOrganizationQueryChange(query: String) {
        _organizationQuery.value = query
        loadOrganizations(query)
    }

    @Suppress("TooGenericExceptionCaught")
    fun loadOrganizations(query: String = _organizationQuery.value) {
        organizationSearchJob?.cancel()
        organizationSearchJob = viewModelScope.launch {
            if (query.isNotBlank()) delay(300)
            _organizationsState.value = AnimeOrganizationsState.Loading
            _organizationsState.value = try {
                AnimeOrganizationsState.Content(repository.searchOrganizations(query))
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                AnimeOrganizationsState.Error(error)
            }
        }
    }

    fun selectOrganization(organization: AnimeOrganization) {
        _organization.value = organization
        _query.value = ""
        applyFilters(AnimeFilters(sort = _filters.value.sort))
    }

    fun clearOrganization() {
        _organization.value = null
    }
}
