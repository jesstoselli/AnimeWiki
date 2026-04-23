package com.example.animewiki.data.paging

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.example.animewiki.data.local.AppDatabase
import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.data.local.entity.RemoteKeyEntity
import com.example.animewiki.data.mapper.toEntity
import com.example.animewiki.data.remote.JikanApi
import kotlinx.coroutines.delay

@OptIn(ExperimentalPagingApi::class)
class TopAnimeRemoteMediator(
    private val api: JikanApi,
    private val db: AppDatabase
) : RemoteMediator<Int, AnimeEntity>() {

    override suspend fun initialize(): InitializeAction {
        // Comporta como "cache-first com refresh em segundo plano"
        return InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, AnimeEntity>
    ): MediatorResult {
        Log.d("Mediator", "load() called: loadType=$loadType, stateItems=${state.pages.sumOf { it.data.size }}, anchor=${state.anchorPosition}")

        val page = when (loadType) {
            LoadType.REFRESH -> 1
            LoadType.PREPEND -> {
                Log.d("Mediator", "PREPEND → end")
                return MediatorResult.Success(endOfPaginationReached = true)
            }
            LoadType.APPEND -> {
                val lastItem = state.lastItemOrNull()
                Log.d("Mediator", "APPEND → lastItem.id=${lastItem?.id}")
                if (lastItem == null) {
                    Log.d("Mediator", "APPEND → no lastItem, end")
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                val key = db.remoteKeyDao().getKey(lastItem.id)
                Log.d("Mediator", "APPEND → remoteKey=$key")
                key?.nextKey ?: run {
                    Log.d("Mediator", "APPEND → no nextKey, end")
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
            }
        }

        Log.d("Mediator", "Fetching page=$page")

        return try {
            if (loadType == LoadType.APPEND) delay(400)
            val response = api.getTopAnime(page = page, limit = state.config.pageSize)
            val hasNext = response.pagination?.hasNextPage == true
            Log.d("Mediator", "page=$page returned ${response.data?.size} items, hasNext=$hasNext")

            val baseIndex = if (loadType == LoadType.REFRESH) 0
            else db.animeDao().maxPageIndex() + 1
            val entities = response.data.orEmpty()
                .mapIndexedNotNull { i, dto -> dto.toEntity(baseIndex + i) }

            db.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    db.remoteKeyDao().clearAll()
                    db.animeDao().clearAll()
                }
                val keys = entities.map {
                    RemoteKeyEntity(
                        animeId = it.id,
                        prevKey = if (page == 1) null else page - 1,
                        nextKey = if (hasNext) page + 1 else null
                    )
                }
                db.remoteKeyDao().upsertAll(keys)
                db.animeDao().upsertAll(entities)
            }

            Log.d("Mediator", "Saved ${entities.size} entities. endOfPaginationReached=${!hasNext}")
            MediatorResult.Success(endOfPaginationReached = !hasNext)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Mediator", "Load failed", e)
            MediatorResult.Error(e)
        }
    }
}