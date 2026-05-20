package com.example.movieflux.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MovieEntity::class, CachedMovieEntity::class],
    version = 6,
    exportSchema = false
)
abstract class MovieDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
}
