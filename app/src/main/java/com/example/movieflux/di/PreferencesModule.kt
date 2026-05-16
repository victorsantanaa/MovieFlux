package com.example.movieflux.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// AuthPreferences and BiometricHelper use @Inject constructors + @Singleton — Hilt binds them automatically.
@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule
