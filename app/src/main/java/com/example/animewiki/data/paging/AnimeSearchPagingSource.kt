package com.example.animewiki.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.example.animewiki.data.mapper.toDomain
import com.example.animewiki.data.remote.dataOrAniListError
import com.example.animewiki.domain.model.Anime
import com.example.animewiki.domain.model.AnimeBrowseCriteria
import com.example.animewiki.domain.model.AnimeFormat
import com.example.animewiki.graphql.SearchAnimeQuery
import com.example.animewiki.graphql.type.MediaFormat
import kotlinx.coroutines.delay

class AnimeSearchPagingSource(
    private val apolloClient: ApolloClient,
    private val criteria: AnimeBrowseCriteria
) : PagingSource<Int, Anime>() {

    override fun getRefreshKey(state: PagingState<Int, Anime>): Int? {
        return state.anchorPosition?.let { anchor ->
            val page = state.closestPageToPosition(anchor)
            page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
        }
    }

    // Error boundary: surfaces any failure (network, parse, etc.) as LoadResult.Error
    // so the UI can show its error state without crashing the app.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Anime> {
        val page = params.key ?: 1
        return try {
            if (page > 1) delay(400) // rate-limit polite

            val filters = criteria.filters
            val data = apolloClient.query(
                SearchAnimeQuery(
                    page = page,
                    perPage = params.loadSize.coerceAtMost(25),
                    search = criteria.query.takeIf(String::isNotBlank)
                        ?.let { Optional.present(it) } ?: Optional.absent(),
                    format = filters.format?.toAniListMediaFormat()
                        ?.let { Optional.present(it) } ?: Optional.absent(),
                    genres = filters.genres.sorted().takeIf { it.isNotEmpty() }
                        ?.let { Optional.present(it) } ?: Optional.absent(),
                    isAdult = if (filters.includeAdultContent) {
                        Optional.absent()
                    } else {
                        Optional.present(false)
                    },
                    sort = listOf(filters.sort.toAniListMediaSort())
                )
            ).execute().dataOrAniListError()
            val resultPage = requireNotNull(data.Page) {
                "AniList search response contained no page"
            }
            val items = resultPage.media.orEmpty()
                .mapNotNull { it?.animeCardFields?.toDomain() }
            val hasNext = resultPage.pageInfo?.hasNextPage == true

            LoadResult.Page(
                data = items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (hasNext) page + 1 else null
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}

private fun AnimeFormat.toAniListMediaFormat(): MediaFormat = when (this) {
    AnimeFormat.TV -> MediaFormat.TV
    AnimeFormat.MOVIE -> MediaFormat.MOVIE
    AnimeFormat.OVA -> MediaFormat.OVA
    AnimeFormat.ONA -> MediaFormat.ONA
    AnimeFormat.SPECIAL -> MediaFormat.SPECIAL
    AnimeFormat.MUSIC -> MediaFormat.MUSIC
}
