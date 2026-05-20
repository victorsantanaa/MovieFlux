@file:Suppress("ConstructorParameterNaming")

package com.example.movieflux.data.remote

data class MovieDetailDto(
    val id: Int,
    val title: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val vote_average: Double? = null,
    val genres: List<GenreDto>? = null,
)
