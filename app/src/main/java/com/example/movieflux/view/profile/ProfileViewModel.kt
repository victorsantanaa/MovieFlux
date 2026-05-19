package com.example.movieflux.view.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieflux.BuildConfig
import com.example.movieflux.analytics.AnalyticsTracker
import com.example.movieflux.data.biometric.BiometricAvailability
import com.example.movieflux.data.biometric.BiometricHelper
import com.example.movieflux.data.preferences.AuthPreferences
import com.example.movieflux.data.preferences.ThemeRepository
import com.example.movieflux.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ProfileUiEvent {
    object LogoutComplete : ProfileUiEvent()
    data class BiometricUnavailable(val reason: String) : ProfileUiEvent()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val biometricHelper: BiometricHelper,
    private val tracker: AnalyticsTracker,
    private val themeRepository: ThemeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProfileUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        tracker.trackScreen("profile")
        _uiState.update {
            it.copy(
                biometricEnabled = authPreferences.biometricEnabled,
                biometricAvailable = biometricHelper.canAuthenticate() == BiometricAvailability.Available,
                appVersion = BuildConfig.VERSION_NAME
            )
        }
        viewModelScope.launch {
            themeRepository.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        if (enabled) {
            when (biometricHelper.canAuthenticate()) {
                BiometricAvailability.Available -> {
                    authPreferences.biometricEnabled = true
                    _uiState.update { it.copy(biometricEnabled = true) }
                }
                BiometricAvailability.NoHardware ->
                    emitEvent(ProfileUiEvent.BiometricUnavailable("Nenhum hardware de biometria encontrado"))
                BiometricAvailability.NoneEnrolled ->
                    emitEvent(
                        ProfileUiEvent.BiometricUnavailable(
                            "Nenhuma digital cadastrada. Vá em Configurações > Segurança para adicionar uma"
                        )
                    )
                BiometricAvailability.Unavailable ->
                    emitEvent(ProfileUiEvent.BiometricUnavailable("A autenticação por biometria está indisponível"))
            }
        } else {
            authPreferences.biometricEnabled = false
            _uiState.update { it.copy(biometricEnabled = false) }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        tracker.trackEvent("theme_changed", mapOf("mode" to mode.name))
        themeRepository.setThemeMode(mode)
    }

    fun requestLogout() {
        _uiState.update { it.copy(showLogoutDialog = true) }
    }

    fun dismissLogoutDialog() {
        _uiState.update { it.copy(showLogoutDialog = false) }
    }

    fun confirmLogout() {
        tracker.trackEvent("logout")
        authPreferences.clear()
        emitEvent(ProfileUiEvent.LogoutComplete)
    }

    private fun emitEvent(event: ProfileUiEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
