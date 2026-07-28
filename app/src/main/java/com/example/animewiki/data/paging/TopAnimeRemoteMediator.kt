package com.example.animewiki.data.paging

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.example.animewiki.data.local.AppDatabase
import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.data.local.entity.RemoteKeyEntity
import com.example.animewiki.data.mapper.toEntity
import com.example.animewiki.data.remote.AniListGraphQlException
import com.example.animewiki.data.remote.dataOrAniListError
import com.example.animewiki.graphql.TopAnimeQuery
import kotlinx.coroutines.delay

@OptIn(ExperimentalPagingApi::class)
class TopAnimeRemoteMediator internal constructor(
    private val apolloClient: ApolloClient,
    private val db: AppDatabase,
    private val transaction: suspend (suspend () -> Unit) -> Unit
) : RemoteMediator<Int, AnimeEntity>() {

    constructor(apolloClient: ApolloClient, db: AppDatabase) : this(
        apolloClient = apolloClient,
        db = db,
        transaction = { block -> db.withTransaction { block() } }
    )

    override suspend fun initialize(): InitializeAction {
        // Comporta como "cache-first com refresh em segundo plano"
        return InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    // Error boundary: any network/DB failure is surfaced as MediatorResult.Error
    // so Paging can retry. CancellationException is rethrown above.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, AnimeEntity>
    ): MediatorResult {
        val stateSize = state.pages.sumOf { it.data.size }
        Log.d(
            "Mediator",
            "load() called: loadType=$loadType, stateItems=$stateSize, anchor=${state.anchorPosition}"
        )

        val page = resolvePage(loadType, state)
            ?: return MediatorResult.Success(endOfPaginationReached = true)

        Log.d("Mediator", "Fetching page=$page")

        return try {
            throttleAppend(loadType)
            val response = apolloClient.query(
                TopAnimeQuery(
                    page = page,
                    perPage = state.config.pageSize.coerceAtMost(25),
                    isAdult = Optional.present(false)
                )
            ).execute()
            val data = response.dataOrAniListError()
            val resultPage = data.Page ?: throw AniListGraphQlException(
                response.errors.orEmpty()
                    .joinToString(separator = "; ") { it.message }
                    .ifBlank { "AniList top response contained no page" }
            )
            val hasNext = resultPage.pageInfo?.hasNextPage == true
            Log.d(
                "Mediator",
                "page=$page returned ${resultPage.media?.size} items, hasNext=$hasNext"
            )

            val rawMedia = resultPage.media.orEmpty()
            val baseIndex = resolveBaseIndex(loadType)
            val entities = rawMedia
                .mapIndexedNotNull { i, media ->
                    media?.animeCacheFields?.toEntity(baseIndex + i)
                }
            val responseErrors = response.errors.orEmpty()
            val unusablePage = entities.isEmpty() && (
                responseErrors.isNotEmpty() ||
                    rawMedia.isNotEmpty() ||
                    hasNext
                )
            if (unusablePage) {
                throw AniListGraphQlException(
                    responseErrors.joinToString(separator = "; ") { it.message }
                        .ifBlank { "AniList top response contained no usable media" }
                )
            }
            if (loadType == LoadType.APPEND && entities.isEmpty()) {
                return MediatorResult.Success(endOfPaginationReached = true)
            }

            transaction {
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

    private suspend fun resolveBaseIndex(loadType: LoadType): Int =
        if (loadType == LoadType.REFRESH) 0 else db.animeDao().maxPageIndex() + 1

    private suspend fun throttleAppend(loadType: LoadType) {
        if (loadType == LoadType.APPEND) delay(400)
    }

    private suspend fun resolvePage(
        loadType: LoadType,
        state: PagingState<Int, AnimeEntity>
    ): Int? = when (loadType) {
        LoadType.REFRESH -> 1
        LoadType.PREPEND -> {
            Log.d("Mediator", "PREPEND → end")
            null
        }
        LoadType.APPEND -> {
            val lastItem = state.lastItemOrNull()
            Log.d("Mediator", "APPEND → lastItem.id=${lastItem?.id}")
            if (lastItem == null) {
                Log.d("Mediator", "APPEND → no lastItem, end")
                null
            } else {
                val key = db.remoteKeyDao().getKey(lastItem.id)
                Log.d("Mediator", "APPEND → remoteKey=$key")
                key?.nextKey.also { nextKey ->
                    if (nextKey == null) Log.d("Mediator", "APPEND → no nextKey, end")
                }
            }
        }
    }
}
