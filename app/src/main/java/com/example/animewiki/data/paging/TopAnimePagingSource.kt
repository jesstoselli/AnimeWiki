package com.example.animewiki.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.animewiki.data.mapper.toDomain
import com.example.animewiki.data.remote.JikanApi
import com.example.animewiki.domain.model.Anime
import kotlinx.coroutines.delay

class TopAnimePagingSource(
    private val api: JikanApi
) : PagingSource<Int, Anime>() {

    override fun getRefreshKey(state: PagingState<Int, Anime>): Int? {
        return state.anchorPosition?.let { anchor ->
            val page = state.closestPageToPosition(anchor)
            page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Anime> {
        val page = params.key ?: 1
        return try {
            // Jikan rate-limit (~3 req/s, 60/min). Throttle suave pra não tomar 429.
            if (page > 1) delay(400)

            val response = api.getTopAnime(
                page = page,
                limit = params.loadSize.coerceAtMost(25)
            )

            val items = response.data.orEmpty().mapNotNull { it.toDomain() }
            val hasNext = response.pagination?.hasNextPage == true

            LoadResult.Page(
                data = items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (hasNext) page + 1 else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}