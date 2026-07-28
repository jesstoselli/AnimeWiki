package com.example.animewiki.data.remote

import com.apollographql.apollo.ApolloClient
import com.apollographql.mockserver.MockResponse
import com.apollographql.mockserver.MockServer
import com.example.animewiki.graphql.GenreCollectionQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ApolloContractTest {
    @Test
    fun `apollo 5 executes generated query through mock server`() = runTest {
        val server = MockServer.Builder().build()
        try {
            server.enqueue(
                MockResponse.Builder()
                    .body("""{"data":{"genres":["Action","Drama"]}}""")
                    .build()
            )
            val client = ApolloClient.Builder().serverUrl(server.url()).build()

            val response = client.query(GenreCollectionQuery()).execute()

            assertEquals(listOf("Action", "Drama"), response.data?.genres)
            assertEquals(null, response.exception)
        } finally {
            server.close()
        }
    }
}
