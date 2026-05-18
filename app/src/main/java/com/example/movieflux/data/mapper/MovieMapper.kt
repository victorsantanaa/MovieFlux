package com.example.movieflux.data.mapper

import com.example.movieflux.data.remote.MovieDto
import com.example.movieflux.domain.model.MovieModel

private const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"

fun MovieDto.toDomain(isFavorite: Boolean) = MovieModel(
    id = id,
    title = title,
    overview = overview,
    posterUrl = poster_path?.let { IMAGE_BASE_URL + it } ?: "",
    rating = vote_average,
    genreIds = genre_ids,
    isFavorite = isFavorite
)
