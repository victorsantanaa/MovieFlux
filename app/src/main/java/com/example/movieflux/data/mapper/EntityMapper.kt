package com.example.movieflux.data.mapper

import com.example.movieflux.data.local.MovieEntity
import com.example.movieflux.domain.model.MovieModel

fun MovieEntity.toDomain(): MovieModel {
    return MovieModel(
        id = id,
        title = title,
        overview = overview,
        posterUrl = posterUrl,
        rating = rating,
        genreIds = if (genreIds.isBlank()) emptyList() else genreIds.split(",").mapNotNull { it.toIntOrNull() },
        isFavorite = true,
        genreNames = if (genreNames.isBlank()) emptyList() else genreNames.split(",")
    )
}

fun MovieModel.toEntity(): MovieEntity {
    return MovieEntity(
        id = id,
        title = title,
        posterUrl = posterUrl,
        overview = overview,
        rating = rating,
        genreIds = genreIds.joinToString(","),
        genreNames = genreNames.joinToString(",")
    )
}
