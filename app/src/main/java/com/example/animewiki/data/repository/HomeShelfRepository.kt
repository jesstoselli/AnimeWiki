package com.example.animewiki.data.repository

import androidx.room.withTransaction
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.example.animewiki.data.local.AppDatabase
import com.example.animewiki.data.local.entity.RemoteKeyEntity
import com.example.animewiki.data.mapper.toEntity
import com.example.animewiki.data.mapper.toMediaSeason
import com.example.animewiki.data.mapper.toShelfAnime
import com.example.animewiki.data.remote.AniListGraphQlException
import com.example.animewiki.data.remote.dataOrAniListError
import com.example.animewiki.domain.HomeShelfSize
import com.example.animewiki.domain.SeasonResolver
import com.example.animewiki.domain.model.HomeShelf
import com.example.animewiki.domain.model.HomeShelfAnime
import com.example.animewiki.graphql.SeasonAnimeQuery
import com.example.animewiki.graphql.TopAnimeQuery
import com.example.animewiki.graphql.type.MediaSort
import com.example.animewiki.graphql.type.MediaStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeShelfRepository internal constructor(
    private val apolloClient: ApolloClient,
    private val db: AppDatabase,
    private val clock: Clock,
    private val transaction: suspend (suspend () -> Unit) -> Unit
) {
    @Inject
    constructor(apolloClient: ApolloClient, db: AppDatabase, clock: Clock) : this(
        apolloClient = apolloClient,
        db = db,
        clock = clock,
        transaction = { block -> db.withTransaction { block() } }
    )

    fun observe(shelf: HomeShelf): Flow<List<HomeShelfAnime>> = when (shelf) {
        HomeShelf.TOP ->
            db.animeDao().observeTop(HomeShelfSize.VALUE).map { list ->
                list.map { it.toShelfAnime() }
            }
        else ->
            db.homeShelfDao().observeShelf(shelf.name).map { list ->
                list.map { it.toShelfAnime() }
            }
    }

    suspend fun refresh(shelf: HomeShelf) {
        if (shelf == HomeShelf.TOP) refreshTop() else refreshNetworkShelf(shelf)
    }

    private suspend fun refreshNetworkShelf(shelf: HomeShelf) {
        val media = apolloClient.query(seasonQueryFor(shelf))
            .execute()
            .dataOrAniListError()
            .Page
            ?.media
            .orEmpty()
        val items = media.mapNotNull { it?.toShelfAnime() }
            .distinctBy(HomeShelfAnime::id)
            .mapIndexed { index, anime -> anime.toEntity(shelf, index) }
        if (items.isEmpty()) {
            throw AniListGraphQlException("AniList ${shelf.name} response had no usable media")
        }
        db.homeShelfDao().replaceShelf(shelf.name, items)
    }

    private suspend fun refreshTop() {
        val page = apolloClient.query(
            TopAnimeQuery(
                page = 1,
                perPage = HomeShelfSize.VALUE,
                isAdult = Optional.present(false)
            )
        ).execute().dataOrAniListError().Page
        val entities = page?.media.orEmpty()
            .mapIndexedNotNull { index, media -> media?.animeCacheFields?.toEntity(index) }
        if (entities.isEmpty()) {
            throw AniListGraphQlException("AniList top response had no usable media")
        }
        val hasNext = page?.pageInfo?.hasNextPage == true
        transaction {
            db.animeDao().upsertAll(entities)
            db.remoteKeyDao().upsertAll(
                entities.map {
                    RemoteKeyEntity(
                        animeId = it.id,
                        prevKey = null,
                        nextKey = if (hasNext) 2 else null
                    )
                }
            )
        }
    }

    private fun seasonQueryFor(shelf: HomeShelf): SeasonAnimeQuery = when (shelf) {
        HomeShelf.THIS_SEASON -> {
            val today = LocalDate.now(clock)
            val current = SeasonResolver.current(today.year, today.monthValue)
            SeasonAnimeQuery(
                page = 1,
                perPage = HomeShelfSize.VALUE,
                season = Optional.present(current.season.toMediaSeason()),
                seasonYear = Optional.present(current.year),
                status = Optional.absent(),
                sort = listOf(MediaSort.POPULARITY_DESC),
                isAdult = Optional.present(false)
            )
        }
        HomeShelf.UPCOMING -> SeasonAnimeQuery(
            page = 1,
            perPage = HomeShelfSize.VALUE,
            season = Optional.absent(),
            seasonYear = Optional.absent(),
            status = Optional.present(MediaStatus.NOT_YET_RELEASED),
            sort = listOf(MediaSort.POPULARITY_DESC),
            isAdult = Optional.present(false)
        )
        HomeShelf.TRENDING -> SeasonAnimeQuery(
            page = 1,
            perPage = HomeShelfSize.VALUE,
            season = Optional.absent(),
            seasonYear = Optional.absent(),
            status = Optional.absent(),
            sort = listOf(MediaSort.TRENDING_DESC),
            isAdult = Optional.present(false)
        )
        HomeShelf.TOP -> error("TOP uses the ranking table, not the season query")
    }
}
