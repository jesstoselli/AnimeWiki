package com.example.animewiki.ui.common

import com.apollographql.apollo.exception.ApolloNetworkException
import java.io.IOException

/**
 * Classifies a Paging load failure so the UI can tell the user what actually went wrong
 * instead of always blaming connectivity.
 */
enum class LoadErrorType {
    /** No usable network — device is offline, DNS failed, or the request timed out. */
    NO_CONNECTION,

    /** The request reached the network but the server / payload was the problem (5xx, bad JSON, etc.). */
    SERVER
}

/**
 * [ApolloNetworkException] and [IOException] (including UnknownHostException,
 * SocketTimeoutException, and ConnectException) mean "we couldn't reach the API".
 * Everything else represents a server-side, GraphQL, or data problem.
 */
fun Throwable.toLoadErrorType(): LoadErrorType =
    if (this is ApolloNetworkException || this is IOException) {
        LoadErrorType.NO_CONNECTION
    } else {
        LoadErrorType.SERVER
    }
