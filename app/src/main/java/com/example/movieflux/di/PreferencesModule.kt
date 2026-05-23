package com.example.movieflux.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Empty by design: [com.example.movieflux.data.preferences.AuthPreferences] and
 * [com.example.movieflux.data.biometric.BiometricHelper] use `@Inject` constructors + `@Singleton`,
 * so Hilt binds them automatically without explicit `@Provides` here.
 */
@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule
