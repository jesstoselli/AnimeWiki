package com.example.animewiki.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.animewiki.data.paging.TopAnimePagingSource
import com.example.animewiki.data.remote.JikanApi
import com.example.animewiki.domain.model.Anime
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimeRepository @Inject constructor(
    private val api: JikanApi
) {
    fun topAnime(): Flow<PagingData<Anime>> = Pager(
        config = PagingConfig(
            pageSize = 25,
            prefetchDistance = 10,
            enablePlaceholders = false,
            initialLoadSize = 25
        ),
        pagingSourceFactory = { TopAnimePagingSource(api) }
    ).flow
}