package com.example.movieflux.data.mapper

import com.example.movieflux.data.local.CachedMovieEntity
import com.example.movieflux.data.remote.MovieDetailDto
import com.example.movieflux.data.remote.MovieDto
import com.example.movieflux.domain.model.MovieModel

private const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"

fun MovieDto.toCacheEntity(page: Int) = CachedMovieEntity(
    id = id,
    title = title,
    overview = overview,
    posterUrl = poster_path?.let { IMAGE_BASE_URL + it } ?: "",
    rating = vote_average,
    genreIds = genre_ids.joinToString(","),
    page = page
)

fun MovieDetailDto.toCacheEntity() = CachedMovieEntity(
    id = id,
    title = title,
    overview = overview,
    posterUrl = poster_path?.let { IMAGE_BASE_URL + it } ?: "",
    rating = vote_average,
    genreIds = genres.joinToString(",") { it.id.toString() },
    page = 0
)

fun CachedMovieEntity.toDomain(isFavorite: Boolean) = MovieModel(
    id = id,
    title = title,
    overview = overview,
    posterUrl = posterUrl,
    rating = rating,
    genreIds = genreIds.split(",").mapNotNull { it.trim().toIntOrNull() },
    isFavorite = isFavorite
)
