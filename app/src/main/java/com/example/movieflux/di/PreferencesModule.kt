package com.example.movieflux.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Vazio por design: [com.example.movieflux.data.preferences.AuthPreferences] e
 * [com.example.movieflux.data.biometric.BiometricHelper] usam construtores `@Inject` + `@Singleton`,
 * então o Hilt faz o binding automaticamente sem necessidade de `@Provides` explícito aqui.
 */
@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule
