@file:Suppress("ConstructorParameterNaming")

package com.example.movieflux.data.remote

data class MovieDto(
    val id: Int,
    val title: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val vote_average: Double? = null,
    val genre_ids: List<Int>? = null,
)
