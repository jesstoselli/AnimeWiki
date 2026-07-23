package com.example.animewiki.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.animewiki.data.mapper.toDomain
import com.example.animewiki.data.remote.JikanApi
import com.example.animewiki.domain.model.Anime
import com.example.animewiki.domain.model.AnimeBrowseCriteria
import kotlinx.coroutines.delay

class AnimeSearchPagingSource(
    private val api: JikanApi,
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
            val response = api.searchAnime(
                query = criteria.query.ifBlank { null },
                page = page,
                limit = params.loadSize.coerceAtMost(25),
                type = filters.format?.apiValue,
                rating = filters.rating?.apiValue,
                genres = filters.genresQuery
            )
            val items = response.data.orEmpty().mapNotNull { it.toDomain() }
            val hasNext = response.pagination?.hasNextPage == true

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
