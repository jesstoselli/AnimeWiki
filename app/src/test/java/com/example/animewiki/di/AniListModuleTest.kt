package com.example.animewiki.di

import com.apollographql.mockserver.MockResponse
import com.apollographql.mockserver.MockServer
import com.example.animewiki.graphql.GenreCollectionQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AniListModuleTest {

    @Test
    fun `provides Apollo client configured for AniList`() {
        val client = AniListModule.provideApolloClient()
        try {
            assertEquals(
                "https://graphql.anilist.co",
                client.newBuilder().httpServerUrl
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun `provided Apollo client uses the retrying OkHttp transport`() = runTest {
        val server = MockServer.Builder().build()
        val moduleClient = AniListModule.provideApolloClient()
        val client = moduleClient.newBuilder()
            .serverUrl(server.url())
            .build()
        try {
            server.enqueue(
                MockResponse.Builder()
                    .statusCode(429)
                    .addHeader("Retry-After", "0")
                    .body("rate limited")
                    .build()
            )
            server.enqueue(
                MockResponse.Builder()
                    .body("""{"data":{"genres":["Action"]}}""")
                    .build()
            )

            val response = client.query(GenreCollectionQuery()).execute()

            assertEquals(listOf("Action"), response.data?.genres)
            server.takeRequest()
            server.takeRequest()
        } finally {
            client.close()
            moduleClient.close()
            server.close()
        }
    }
}
