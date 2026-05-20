@file:Suppress("ConstructorParameterNaming")

package com.example.movieflux.data.remote

data class MovieResponse(
    val page: Int = 1,
    val results: List<MovieDto>? = null,
    val total_pages: Int = 1,
    val total_results: Int = 0,
)
