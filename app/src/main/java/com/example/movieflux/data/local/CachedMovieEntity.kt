package com.example.movieflux.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movie_cache")
data class CachedMovieEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val overview: String,
    val posterUrl: String,
    val rating: Double,
    val genreIds: String, // comma-separated: "28,12,35"
    val page: Int
)
