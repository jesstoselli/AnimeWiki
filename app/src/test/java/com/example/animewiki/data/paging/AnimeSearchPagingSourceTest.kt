package com.example.animewiki.data.paging

import androidx.paging.PagingSource
import com.example.animewiki.data.remote.JikanApi
import com.example.animewiki.data.remote.dto.AnimeListResponseDto
import com.example.animewiki.data.remote.dto.PaginationDto
import com.example.animewiki.domain.model.AnimeAgeRating
import com.example.animewiki.domain.model.AnimeBrowseCriteria
import com.example.animewiki.domain.model.AnimeFilters
import com.example.animewiki.domain.model.AnimeFormat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeSearchPagingSourceTest {
    private val api: JikanApi = mockk()

    @Test
    fun `load sends normalized query and all active filters`() = runTest {
        stubEmptyPage()
        val criteria = AnimeBrowseCriteria.create(
            query = " frieren ",
            filters = AnimeFilters(
                format = AnimeFormat.TV,
                rating = AnimeAgeRating.PG13,
                genreIds = setOf(10, 1)
            )
        )

        val result = AnimeSearchPagingSource(api, criteria).load(refresh())

        assertTrue(result is PagingSource.LoadResult.Page)
        coVerify(exactly = 1) {
            api.searchAnime(
                query = "frieren",
                page = 1,
                limit = 25,
                type = "tv",
                rating = "pg13",
                genres = "1,10",
                orderBy = "popularity",
                sort = "asc"
            )
        }
    }

    @Test
    fun `filter-only load omits blank q parameter`() = runTest {
        stubEmptyPage()
        val criteria = AnimeBrowseCriteria.create(
            filters = AnimeFilters(format = AnimeFormat.MOVIE)
        )

        AnimeSearchPagingSource(api, criteria).load(refresh())

        coVerify {
            api.searchAnime(
                query = null,
                page = 1,
                limit = 25,
                type = "movie",
                rating = null,
                genres = null,
                orderBy = "popularity",
                sort = "asc"
            )
        }
    }

    private fun stubEmptyPage() {
        coEvery { api.searchAnime(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            AnimeListResponseDto(
                pagination = PaginationDto(hasNextPage = false),
                data = emptyList()
            )
    }

    private fun refresh() = PagingSource.LoadParams.Refresh<Int>(
        key = null,
        loadSize = 25,
        placeholdersEnabled = false
    )
}
