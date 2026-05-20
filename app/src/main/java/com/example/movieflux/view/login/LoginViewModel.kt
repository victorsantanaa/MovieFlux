package com.example.movieflux.view.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieflux.R
import com.example.movieflux.analytics.AnalyticsTracker
import com.example.movieflux.analytics.FunnelTracker
import com.example.movieflux.data.biometric.BiometricAvailability
import com.example.movieflux.data.biometric.BiometricHelper
import com.example.movieflux.data.preferences.AuthPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val biometricHelper: BiometricHelper,
    private val tracker: AnalyticsTracker,
    private val funnel: FunnelTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        tracker.trackScreen("login")
    }

    fun login(username: String, password: String) {
        _uiState.value = LoginUiState.Loading
        funnel.start("auth")
        funnel.step("auth", "login_clicked")
        viewModelScope.launch {
            if (username == "admin" && password == "1234") {
                authPreferences.isLoggedIn = true
                funnel.step("auth", "login_success")
                val shouldPrompt = !authPreferences.biometricPrompted &&
                    biometricHelper.canAuthenticate() == BiometricAvailability.Available
                _uiState.value = LoginUiState.Success(shouldPromptBiometric = shouldPrompt)
            } else {
                funnel.abandon("auth", "invalid_credentials")
                tracker.trackError("[AUTH]", Exception("Invalid credentials"))
                _uiState.value = LoginUiState.Error(R.string.login_error_invalid_credentials)
            }
        }
    }

    fun confirmBiometricOptIn(enable: Boolean) {
        authPreferences.biometricEnabled = enable
        authPreferences.biometricPrompted = true
        _uiState.value = LoginUiState.Success(shouldPromptBiometric = false)
    }
}
