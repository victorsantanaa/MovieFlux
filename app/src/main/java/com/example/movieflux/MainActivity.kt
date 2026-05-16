package com.example.movieflux

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.metrics.performance.JankStats
import androidx.navigation.compose.rememberNavController
import com.example.movieflux.data.biometric.BiometricAvailability
import com.example.movieflux.data.biometric.BiometricHelper
import com.example.movieflux.data.preferences.AuthPreferences
import com.example.movieflux.navigation.AppNavHost
import com.example.movieflux.navigation.Screen
import com.example.movieflux.performance.JankReporter
import com.example.movieflux.ui.theme.MovieFluxTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var authPreferences: AuthPreferences
    @Inject lateinit var biometricHelper: BiometricHelper
    @Inject lateinit var jankReporter: JankReporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isLoggedIn = authPreferences.isLoggedIn
        val needsBiometric = isLoggedIn &&
            authPreferences.biometricEnabled &&
            biometricHelper.canAuthenticate(this) == BiometricAvailability.Available

        // Start at MainGraph if already logged in and no biometric required.
        // If biometric is needed, start at AuthGraph and navigate to Main on success.
        val startDestination = if (isLoggedIn && !needsBiometric) {
            Screen.MainGraph.route
        } else {
            Screen.AuthGraph.route
        }

        setContent {
            MovieFluxTheme {
                val rootNavController = rememberNavController()

                if (needsBiometric) {
                    LaunchedEffect(Unit) {
                        biometricHelper.authenticate(
                            activity = this@MainActivity,
                            onSuccess = {
                                rootNavController.navigate(Screen.MainGraph.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onError = {
                                // Stay on AuthGraph — user must re-login manually
                            }
                        )
                    }
                }

                AppNavHost(rootNavController, startDestination)
            }
        }

        val jankStats = JankStats.createAndTrack(window, jankReporter)
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            jankStats.isTrackingEnabled = (event == Lifecycle.Event.ON_RESUME)
        })
    }
}
