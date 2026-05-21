package com.example.movieflux

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.metrics.performance.JankStats
import androidx.navigation.compose.rememberNavController
import com.example.movieflux.data.biometric.BiometricAvailability
import com.example.movieflux.data.biometric.BiometricHelper
import com.example.movieflux.data.preferences.AuthPreferences
import com.example.movieflux.data.preferences.ThemeRepository
import com.example.movieflux.navigation.AppNavHost
import com.example.movieflux.navigation.Screen
import com.example.movieflux.performance.JankReporter
import com.example.movieflux.ui.theme.MovieFluxTheme
import com.example.movieflux.view.biometric.BiometricGate
import com.example.movieflux.view.biometric.BiometricGateState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var authPreferences: AuthPreferences

    @Inject lateinit var biometricHelper: BiometricHelper

    @Inject lateinit var jankReporter: JankReporter

    @Inject lateinit var themeRepository: ThemeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Draw behind the system bars so the teal top bar / search row can paint the status-bar
        // region. Without this the window fits system windows and statusBarsPadding() resolves to 0.
        enableEdgeToEdge()

        // Keep the splash on screen until the (Keystore-backed) auth state has been read off the main
        // thread. Reading EncryptedSharedPreferences synchronously here would risk a cold-start ANR.
        var contentReady = false
        splashScreen.setKeepOnScreenCondition { !contentReady }

        lifecycleScope.launch {
            // Build the encrypted prefs and read the auth flags on a background thread.
            authPreferences.awaitReady()
            val loggedInWithBiometric = withContext(Dispatchers.IO) {
                authPreferences.isLoggedIn && authPreferences.biometricEnabled
            }

            // Biometric is the ONLY way to skip the login screen on a relaunch. Without an enrolled,
            // available, opted-in biometric, the user must authenticate via the login screen every
            // time — a persisted isLoggedIn flag alone never bypasses login.
            val needsBiometric = loggedInWithBiometric &&
                biometricHelper.canAuthenticate() == BiometricAvailability.Available

            // Always start at the login graph. When needsBiometric is true the BiometricGate overlay
            // defers composing the NavHost and flips effectiveStart to MainGraph on a successful scan.
            val startDestination = Screen.AuthGraph.route

            setContent {
                val themeMode by themeRepository.themeMode.collectAsStateWithLifecycle()

                MovieFluxTheme(themeMode = themeMode) {
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

            contentReady = true
        }

        val jankStats = JankStats.createAndTrack(window, jankReporter)
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                jankStats.isTrackingEnabled = (event == Lifecycle.Event.ON_RESUME)
            }
        )
    }
}
