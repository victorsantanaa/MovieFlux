package com.example.movieflux.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RemoteDataSource {

    @GET("movie/popular")
    suspend fun getPopular(
        @Query("page") page: Int
    ): MovieResponse

    @GET("search/movie")
    suspend fun search(
        @Query("query") query: String
    ): MovieResponse

    @GET("genre/movie/list")
    suspend fun genres(): GenreResponse

    @GET("movie/{movie_id}")
    suspend fun getMovieDetail(
        @Path("movie_id") id: Int
    ): MovieDetailDto
}