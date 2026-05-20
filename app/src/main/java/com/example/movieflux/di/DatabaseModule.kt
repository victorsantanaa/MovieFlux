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

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE favorites ADD COLUMN overview TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE favorites ADD COLUMN rating REAL NOT NULL DEFAULT 0")
    }
}

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

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE movie_cache ADD COLUMN rank INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE movie_cache ADD COLUMN genreNames TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE favorites ADD COLUMN genreIds TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE favorites ADD COLUMN genreNames TEXT NOT NULL DEFAULT ''")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MovieDatabase =
        Room.databaseBuilder(context, MovieDatabase::class.java, "movieflux.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Provides
    @Singleton
    fun provideMovieDao(db: MovieDatabase): MovieDao = db.movieDao()
}
