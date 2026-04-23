package com.example.animewiki.data.remote

import com.example.animewiki.data.remote.dto.AnimeListResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface JikanApi {

    @GET("top/anime")
    suspend fun getTopAnime(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 25
    ): AnimeListResponseDto
}