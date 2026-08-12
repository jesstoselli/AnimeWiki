package com.example.animewiki.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.apollographql.apollo.ApolloClient
import com.example.animewiki.data.mapper.toDomain
import com.example.animewiki.data.remote.dataOrAniListError
import com.example.animewiki.domain.model.Anime
import com.example.animewiki.domain.model.AnimeOrganization
import com.example.animewiki.domain.model.AnimeSort
import com.example.animewiki.graphql.OrganizationAnimeQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class OrganizationAnimePagingSource(
    private val apolloClient: ApolloClient,
    private val organization: AnimeOrganization,
    private val sort: AnimeSort
) : PagingSource<Int, Anime>() {

    override fun getRefreshKey(state: PagingState<Int, Anime>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.let { page ->
                page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
            }
        }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Anime> {
        val page = params.key ?: 1
        return try {
            if (page > 1) delay(400)
            val data = apolloClient.query(
                OrganizationAnimeQuery(
                    id = organization.id,
                    page = page,
                    perPage = params.loadSize.coerceAtMost(25),
                    sort = com.apollographql.apollo.api.Optional.present(
                        listOf(sort.toAniListMediaSort())
                    )
                )
            ).execute().dataOrAniListError()
            val media = requireNotNull(data.Studio?.media) {
                "AniList returned no media for ${organization.name}"
            }
            val items = media.nodes.orEmpty()
                .filter { it?.animeCardFields?.isAdult != true }
                .mapNotNull { it?.animeCardFields?.toDomain() }

            LoadResult.Page(
                data = items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (media.pageInfo?.hasNextPage == true) page + 1 else null
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            LoadResult.Error(error)
        }
    }
}
