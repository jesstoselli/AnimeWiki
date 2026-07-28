package com.example.animewiki.data.remote

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.mockserver.MockResponse
import com.apollographql.mockserver.MockServer
import com.example.animewiki.graphql.GenreCollectionQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AniListResponseTest {

    @Test
    fun `returns data when response also contains field errors`() = runTest {
        val server = MockServer.Builder().build()
        try {
            server.enqueue(
                MockResponse.Builder()
                    .body(
                        """{"data":{"genres":["Action",null]},"errors":[{"message":"one item failed"}]}"""
                    )
                    .build()
            )
            val client = ApolloClient.Builder().serverUrl(server.url()).build()

            val data = client.query(GenreCollectionQuery()).execute().dataOrAniListError()

            assertEquals("Action", data.genres?.first())
        } finally {
            server.close()
        }
    }

    @Test
    fun `throws typed server error when no data is usable`() = runTest {
        val server = MockServer.Builder().build()
        try {
            server.enqueue(
                MockResponse.Builder()
                    .body("""{"data":null,"errors":[{"message":"upstream failed"}]}""")
                    .build()
            )
            val client = ApolloClient.Builder().serverUrl(server.url()).build()

            val exception = try {
                client.query(GenreCollectionQuery()).execute().dataOrAniListError()
                null
            } catch (throwable: Throwable) {
                throwable
            }

            assertTrue(exception is AniListGraphQlException)
            assertEquals("upstream failed", exception?.message)
        } finally {
            server.close()
        }
    }

    @Test
    fun `rethrows the response Apollo exception unchanged`() = runTest {
        val server = MockServer.Builder().build()
        val client = ApolloClient.Builder().serverUrl(server.url()).build()
        server.close()
        val response = client.query(GenreCollectionQuery()).execute()
        val apolloException = requireNotNull(response.exception)

        val thrown = try {
            response.dataOrAniListError()
            null
        } catch (throwable: Throwable) {
            throwable
        }

        assertTrue(apolloException is ApolloNetworkException)
        assertSame(apolloException, thrown)
    }
}
