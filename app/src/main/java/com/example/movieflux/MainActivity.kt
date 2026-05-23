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
        // Desenha por trás das system bars para que a top bar verde-azulada / linha de busca pinte
        // a região da status bar. Sem isso a janela se ajusta às system windows e statusBarsPadding()
        // resolve para 0.
        enableEdgeToEdge()

        // Mantém a splash em tela até que o estado de autenticação (apoiado pelo Keystore) tenha sido
        // lido fora da main thread. Ler EncryptedSharedPreferences de forma síncrona aqui correria
        // risco de ANR no cold start.
        var contentReady = false
        splashScreen.setKeepOnScreenCondition { !contentReady }

        lifecycleScope.launch {
            // Constrói as prefs criptografadas e lê as flags de auth em uma thread de background.
            authPreferences.awaitReady()
            val loggedInWithBiometric = withContext(Dispatchers.IO) {
                authPreferences.isLoggedIn && authPreferences.biometricEnabled
            }

            // A biometria é a ÚNICA forma de pular a tela de login em um relaunch. Sem uma biometria
            // cadastrada, disponível e com opt-in, o usuário deve autenticar via tela de login toda
            // vez — uma flag isLoggedIn persistida, sozinha, nunca contorna o login.
            val needsBiometric = loggedInWithBiometric &&
                biometricHelper.canAuthenticate() == BiometricAvailability.Available

            // Sempre inicia no grafo de login. Quando needsBiometric é true, o overlay BiometricGate
            // adia a composição do NavHost e troca effectiveStart para MainGraph após um scan bem-sucedido.
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
