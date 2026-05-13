package com.example.movieflux.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class MovieEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val posterUrl: String
)