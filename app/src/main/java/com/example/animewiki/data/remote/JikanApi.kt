package com.example.animewiki.data.remote

import com.example.animewiki.data.remote.dto.AnimeDetailsResponseDto
import com.example.animewiki.data.remote.dto.AnimeListResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface JikanApi {

    @GET("top/anime")
    suspend fun getTopAnime(
        @Query(PAGE) page: Int = 1,
        @Query(LIMIT) limit: Int = 25
    ): AnimeListResponseDto

    @GET("anime/{id}/full")
    suspend fun getAnimeDetails(@Path(ID) id: Int): AnimeDetailsResponseDto

    @GET("anime")
    suspend fun searchAnime(
        @Query(QUERY) query: String,
        @Query(PAGE) page: Int = 1,
        @Query(LIMIT) limit: Int = 25,
        @Query(ORDER_BY) orderBy: String = POPULARITY,
        @Query(SORT) sort: String = ASCENDING
    ): AnimeListResponseDto

    companion object {
        const val POPULARITY = "popularity"
        const val ASCENDING = "asc"

        const val LIMIT = "limit"
        const val PAGE = "page"
        const val ID = "id"
        const val QUERY = "q"
        const val ORDER_BY = "order_by"
        const val SORT = "sort"
    }
}