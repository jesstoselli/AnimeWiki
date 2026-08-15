package com.example.animewiki.data.repository

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.map
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.example.animewiki.data.local.AppDatabase
import com.example.animewiki.data.local.dao.FavoriteDao
import com.example.animewiki.data.mapper.toDomain
import com.example.animewiki.data.mapper.toDomainDetails
import com.example.animewiki.data.mapper.toFavoriteEntity
import com.example.animewiki.data.paging.AnimeSearchPagingSource
import com.example.animewiki.data.paging.OrganizationAnimePagingSource
import com.example.animewiki.data.paging.TopAnimeRemoteMediator
import com.example.animewiki.data.remote.dataOrAniListError
import com.example.animewiki.domain.model.Anime
import com.example.animewiki.domain.model.AnimeBrowseCriteria
import com.example.animewiki.domain.model.AnimeGenre
import com.example.animewiki.domain.model.AnimeOrganization
import com.example.animewiki.domain.model.AnimeSort
import com.example.animewiki.graphql.AnimeDetailsQuery
import com.example.animewiki.graphql.GenreCollectionQuery
import com.example.animewiki.graphql.SearchOrganizationsQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalPagingApi::class)
class AnimeRepository @Inject constructor(
    private val apolloClient: ApolloClient,
    private val db: AppDatabase,
    private val favoriteDao: FavoriteDao
) {
    private var cachedGenres: List<AnimeGenre>? = null
    private var genreRefreshInFlight: CompletableDeferred<List<AnimeGenre>>? = null
    private val genreCacheMutex = Mutex()

    fun topAnime(): Flow<PagingData<Anime>> = Pager(
        config = PagingConfig(
            pageSize = 25,
            prefetchDistance = 10,
            enablePlaceholders = false,
            initialLoadSize = 25
        ),
        remoteMediator = TopAnimeRemoteMediator(apolloClient, db),
        pagingSourceFactory = { db.animeDao().pagingSource() }
    ).flow.map { pagingData ->
        pagingData.map { it.toDomain() }
    }

    // Cache-first: falls back to local DB if the network call fails for any reason.
    @Suppress("TooGenericExceptionCaught")
    suspend fun getAnimeDetails(id: Int): Anime? {
        val cached = db.animeDao().getById(id)?.toDomain()
        return try {
            apolloClient.query(AnimeDetailsQuery(id))
                .execute()
                .dataOrAniListError()
                .Media
                ?.toDomainDetails()
                ?: cached
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AnimeRepository", "Failed to fetch details for id=$id, using cache", e)
            cached
        }
    }

    fun searchAnime(criteria: AnimeBrowseCriteria): Flow<PagingData<Anime>> = Pager(
        config = PagingConfig(
            pageSize = 25,
            prefetchDistance = 10,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { AnimeSearchPagingSource(apolloClient, criteria) }
    ).flow.map { pagingData ->
        val seenIds = mutableSetOf<Int>()
        pagingData.filter { anime -> seenIds.add(anime.id) }
    }

    fun organizationAnime(
        organization: AnimeOrganization,
        sort: AnimeSort
    ): Flow<PagingData<Anime>> = Pager(
        config = PagingConfig(pageSize = 25, prefetchDistance = 10, enablePlaceholders = false),
        pagingSourceFactory = {
            OrganizationAnimePagingSource(apolloClient, organization, sort)
        }
    ).flow

    suspend fun searchOrganizations(query: String): List<AnimeOrganization> =
        apolloClient.query(
            SearchOrganizationsQuery(
                search = query.trim().takeIf(String::isNotBlank)
                    ?.let(Optional.Companion::present) ?: Optional.absent()
            )
        ).execute().dataOrAniListError().Page?.studios.orEmpty()
            .mapNotNull { studio ->
                studio?.name?.takeIf(String::isNotBlank)?.let { name ->
                    AnimeOrganization(studio.id, name, studio.isAnimationStudio)
                }
            }

    @Suppress("TooGenericExceptionCaught")
    suspend fun getAnimeGenres(forceRefresh: Boolean = false): List<AnimeGenre> {
        val (refresh, ownsRefresh) = genreCacheMutex.withLock {
            if (!forceRefresh) cachedGenres?.let { return it.toList() }
            genreRefreshInFlight?.let { return@withLock it to false }

            CompletableDeferred<List<AnimeGenre>>()
                .also { genreRefreshInFlight = it } to true
        }

        if (ownsRefresh) {
            try {
                val genres = apolloClient.query(GenreCollectionQuery())
                    .execute()
                    .dataOrAniListError()
                    .genres
                    .orEmpty()
                    .mapNotNull { it?.takeIf(String::isNotBlank) }
                    .distinct()
                    .sortedBy(String::lowercase)
                    .map(::AnimeGenre)
                check(genres.isNotEmpty()) {
                    "AniList returned an empty anime genre catalog"
                }
                val snapshot = genres.toList()

                withContext(NonCancellable) {
                    genreCacheMutex.withLock {
                        cachedGenres = snapshot
                        refresh.complete(snapshot)
                        genreRefreshInFlight = null
                    }
                }
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    genreCacheMutex.withLock {
                        refresh.completeExceptionally(error)
                        genreRefreshInFlight = null
                    }
                }
            }
        }

        return refresh.await().toList()
    }

    fun observeFavorites(): Flow<List<Anime>> =
        favoriteDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeIsFavorite(id: Int): Flow<Boolean> =
        favoriteDao.observeIsFavorite(id)

    suspend fun toggleFavorite(anime: Anime, isCurrentlyFavorite: Boolean) {
        if (isCurrentlyFavorite) {
            favoriteDao.deleteById(anime.id)
        } else {
            favoriteDao.insert(anime.toFavoriteEntity())
        }
    }
}
