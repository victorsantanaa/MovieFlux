package com.example.movieflux.domain.model

data class MovieModel(
    val id: Int,
    val title: String,
    val overview: String,
    val posterUrl: String,
    val rating: Double,
    val genreIds: List<Int>,
    val isFavorite: Boolean
)
