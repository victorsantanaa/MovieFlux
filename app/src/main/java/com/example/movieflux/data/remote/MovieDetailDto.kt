@file:Suppress("ConstructorParameterNaming")

package com.example.movieflux.data.remote

data class MovieDetailDto(
    val id: Int,
    val title: String,
    val overview: String,
    val poster_path: String?,
    val vote_average: Double,
    val genres: List<GenreDto>,
)
