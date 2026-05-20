package com.example.movieflux.data.mapper

import com.example.movieflux.data.local.CachedMovieEntity
import com.example.movieflux.data.remote.MovieDetailDto
import com.example.movieflux.data.remote.MovieDto
import com.example.movieflux.domain.model.MovieModel

private const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"

fun MovieDto.toCacheEntity(page: Int, rank: Int, genreNames: List<String>) = CachedMovieEntity(
    id = id,
    title = title.orEmpty(),
    overview = overview.orEmpty(),
    posterUrl = poster_path?.let { IMAGE_BASE_URL + it } ?: "",
    rating = vote_average ?: 0.0,
    genreIds = (genre_ids ?: emptyList()).joinToString(","),
    page = page,
    rank = rank,
    genreNames = genreNames.joinToString(",")
)

fun MovieDetailDto.toCacheEntity() = CachedMovieEntity(
    id = id,
    title = title.orEmpty(),
    overview = overview.orEmpty(),
    posterUrl = poster_path?.let { IMAGE_BASE_URL + it } ?: "",
    rating = vote_average ?: 0.0,
    genreIds = (genres ?: emptyList()).joinToString(",") { it.id.toString() },
    page = 0,
    rank = 0,
    genreNames = (genres ?: emptyList()).mapNotNull { it.name }.joinToString(",")
)

fun CachedMovieEntity.toDomain(isFavorite: Boolean) = MovieModel(
    id = id,
    title = title,
    overview = overview,
    posterUrl = posterUrl,
    rating = rating,
    genreIds = genreIds.split(",").mapNotNull { it.trim().toIntOrNull() },
    isFavorite = isFavorite,
    genreNames = if (genreNames.isBlank()) emptyList() else genreNames.split(",")
)
