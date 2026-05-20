package com.example.movieflux.data.mapper

import com.example.movieflux.data.remote.MovieDetailDto
import com.example.movieflux.domain.model.MovieModel

private const val DETAIL_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w780"

fun MovieDetailDto.toDomain(isFavorite: Boolean): MovieModel = MovieModel(
    id = id,
    title = title.orEmpty(),
    overview = overview.orEmpty(),
    posterUrl = poster_path?.let { DETAIL_IMAGE_BASE_URL + it } ?: "",
    rating = vote_average ?: 0.0,
    genreIds = genres?.map { it.id } ?: emptyList(),
    isFavorite = isFavorite,
    genreNames = genres?.mapNotNull { it.name } ?: emptyList()
)
