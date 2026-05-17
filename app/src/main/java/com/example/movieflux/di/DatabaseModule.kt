package com.example.movieflux.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.movieflux.data.local.MovieDao
import com.example.movieflux.data.local.MovieDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS movie_cache (
                id INTEGER NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                overview TEXT NOT NULL,
                posterUrl TEXT NOT NULL,
                rating REAL NOT NULL,
                genreIds TEXT NOT NULL,
                page INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MovieDatabase =
        Room.databaseBuilder(context, MovieDatabase::class.java, "movieflux.db")
            .addMigrations(MIGRATION_2_3)
            .build()

    @Provides
    @Singleton
    fun provideMovieDao(db: MovieDatabase): MovieDao = db.movieDao()
}
