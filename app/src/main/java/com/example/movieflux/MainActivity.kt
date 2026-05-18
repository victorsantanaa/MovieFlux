package com.example.movieflux

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.movieflux.view.biometric.BiometricGate
import com.example.movieflux.view.biometric.BiometricGateState
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
            biometricHelper.canAuthenticate() == BiometricAvailability.Available

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
                var effectiveStart by remember { mutableStateOf(startDestination) }
                var gateState by remember {
                    mutableStateOf(
                        if (needsBiometric) BiometricGateState.Checking else BiometricGateState.Passed
                    )
                }

                BiometricGate(
                    state = gateState,
                    onRetry = { gateState = BiometricGateState.Checking },
                    onUsePassword = {
                        effectiveStart = Screen.AuthGraph.route
                        gateState = BiometricGateState.Passed
                    },
                    onResolved = { newState ->
                        if (newState is BiometricGateState.Passed && needsBiometric) {
                            effectiveStart = Screen.MainGraph.route
                        }
                        gateState = newState
                    },
                    authenticate = { onSuccess, onError ->
                        biometricHelper.authenticate(
                            activity = this@MainActivity,
                            onSuccess = { onSuccess() },
                            onError = onError
                        )
                    }
                ) {
                    AppNavHost(rootNavController, effectiveStart)
                }
            }
        }

        val jankStats = JankStats.createAndTrack(window, jankReporter)
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            jankStats.isTrackingEnabled = (event == Lifecycle.Event.ON_RESUME)
        })
    }
}
