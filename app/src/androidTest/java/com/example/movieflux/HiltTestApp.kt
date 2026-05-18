package com.example.movieflux

import android.app.Application
import dagger.hilt.android.testing.CustomTestApplication
import timber.log.Timber

// Base application class for testing - no @HiltAndroidApp annotation
open class BaseTestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}

// Hilt will generate HiltTestApp_Application from this
@CustomTestApplication(BaseTestApp::class)
open class HiltTestApp : Application()
