package com.example.animewiki.data.repository

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.map
import com.example.animewiki.data.local.AppDatabase
import com.example.animewiki.data.local.dao.FavoriteDao
import com.example.animewiki.data.mapper.toDomain
import com.example.animewiki.data.mapper.toFavoriteEntity
import com.example.animewiki.data.paging.AnimeSearchPagingSource
import com.example.animewiki.data.paging.TopAnimeRemoteMediator
import com.example.animewiki.data.remote.JikanApi
import com.example.animewiki.domain.model.Anime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalPagingApi::class)
class AnimeRepository @Inject constructor(
    private val api: JikanApi,
    private val db: AppDatabase,
    private val favoriteDao: FavoriteDao
) {
    fun topAnime(): Flow<PagingData<Anime>> = Pager(
        config = PagingConfig(
            pageSize = 25,
            prefetchDistance = 10,
            enablePlaceholders = false,
            initialLoadSize = 25
        ),
        remoteMediator = TopAnimeRemoteMediator(api, db),
        pagingSourceFactory = { db.animeDao().pagingSource() }
    ).flow.map { pagingData ->
        pagingData.map { it.toDomain() }
    }

    // Cache-first: falls back to local DB if the network call fails for any reason.
    @Suppress("TooGenericExceptionCaught")
    suspend fun getAnimeDetails(id: Int): Anime? {
        val cached = db.animeDao().getById(id)?.toDomain()
        return try {
            api.getAnimeDetails(id).data?.toDomain() ?: cached
        } catch (e: Exception) {
            Log.w("AnimeRepository", "Failed to fetch details for id=$id, using cache", e)
            cached
        }
    }

    fun searchAnime(query: String): Flow<PagingData<Anime>> = Pager(
        config = PagingConfig(
            pageSize = 25,
            prefetchDistance = 10,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { AnimeSearchPagingSource(api, query) }
    ).flow.map { pagingData ->
        val seenIds = mutableSetOf<Int>()
        pagingData.filter { anime -> seenIds.add(anime.id) }
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
