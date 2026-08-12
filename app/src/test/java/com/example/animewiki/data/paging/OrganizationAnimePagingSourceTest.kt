package com.example.animewiki.data.paging

import androidx.paging.PagingSource
import com.apollographql.apollo.ApolloClient
import com.apollographql.mockserver.MockResponse
import com.apollographql.mockserver.MockServer
import com.example.animewiki.domain.model.AnimeOrganization
import com.example.animewiki.domain.model.AnimeSort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OrganizationAnimePagingSourceTest {
    @Test
    fun `duplicate anime ids are removed before reaching lazy grid`() = runTest {
        val server = MockServer.Builder().build()
        val client = ApolloClient.Builder().serverUrl(server.url()).build()
        server.enqueue(MockResponse.Builder().body(duplicateMediaResponse()).build())
        try {
            val loadResult = OrganizationAnimePagingSource(
                client,
                AnimeOrganization(11, "MADHOUSE", true),
                AnimeSort.SCORE
            ).load(
                PagingSource.LoadParams.Refresh(
                    key = null,
                    loadSize = 25,
                    placeholdersEnabled = false
                )
            )
            val result = when (loadResult) {
                is PagingSource.LoadResult.Page -> loadResult
                is PagingSource.LoadResult.Error -> throw loadResult.throwable
                is PagingSource.LoadResult.Invalid -> error("Unexpected invalid result")
            }

            assertEquals(listOf(109731), result.data.map { it.id })
        } finally {
            client.close()
            server.close()
        }
    }

    private fun duplicateMediaResponse(): String = """
        {
          "data": {
            "Studio": {
              "media": {
                "pageInfo": {"currentPage": 1, "hasNextPage": false},
                "nodes": [$MEDIA, $MEDIA]
              }
            }
          }
        }
    """.trimIndent()

    private companion object {
        const val MEDIA = """
            {
              "__typename": "Media",
              "id": 109731,
              "title": {"english": "Duplicate", "romaji": "Duplicate"},
              "coverImage": {
                "extraLarge": "https://example.test/cover.jpg",
                "large": null
              },
              "averageScore": 80,
              "episodes": 12,
              "format": "TV",
              "seasonYear": 2020,
              "isAdult": false
            }
        """
    }
}
