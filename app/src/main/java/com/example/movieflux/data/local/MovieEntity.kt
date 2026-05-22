package com.example.movieflux.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class MovieEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val posterUrl: String,
    val overview: String = "",
    val rating: Double = 0.0,
    val genreIds: String = "",
    val genreNames: String = ""
)
