package com.example.animewiki.data.remote

import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation

fun <D : Operation.Data> ApolloResponse<D>.dataOrAniListError(): D {
    exception?.let { throw it }
    data?.let { return it }
    val message = errors.orEmpty()
        .joinToString(separator = "; ") { it.message }
        .ifBlank { "AniList response contained no usable data" }
    throw AniListGraphQlException(message)
}
