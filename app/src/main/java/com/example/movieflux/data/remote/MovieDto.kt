@file:Suppress("ConstructorParameterNaming")

package com.example.movieflux.data.remote

data class MovieDto(
    val id: Int,
    val title: String,
    val overview: String,
    val poster_path: String?,
    val vote_average: Double,
    val genre_ids: List<Int>,
)
