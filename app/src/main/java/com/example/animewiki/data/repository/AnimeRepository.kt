package com.example.animewiki.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.example.animewiki.data.local.AppDatabase
import com.example.animewiki.data.mapper.toDomain
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
    private val db: AppDatabase
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

    suspend fun getAnimeDetails(id: Int): Anime? {
        // Cache-first
        val cached = db.animeDao().getById(id)?.toDomain()
        return try {
            api.getAnimeDetails(id).data?.toDomain() ?: cached
        } catch (e: Exception) {
            cached  // offline: retorna o que tiver no cache
        }
    }
}