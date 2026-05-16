package com.example.movieflux.view.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieflux.BuildConfig
import com.example.movieflux.data.biometric.BiometricAvailability
import com.example.movieflux.data.biometric.BiometricHelper
import com.example.movieflux.data.preferences.AuthPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProfileUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        _uiState.update {
            it.copy(
                biometricEnabled = authPreferences.biometricEnabled,
                biometricAvailable = biometricHelper.canAuthenticate(context) == BiometricAvailability.Available,
                appVersion = BuildConfig.VERSION_NAME
            )
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        if (enabled) {
            when (biometricHelper.canAuthenticate(context)) {
                BiometricAvailability.Available -> {
                    authPreferences.biometricEnabled = true
                    _uiState.update { it.copy(biometricEnabled = true) }
                }
                BiometricAvailability.NoHardware ->
                    emitEvent(ProfileUiEvent.BiometricUnavailable("No biometric hardware found"))
                BiometricAvailability.NoneEnrolled ->
                    emitEvent(ProfileUiEvent.BiometricUnavailable("No fingerprints enrolled. Go to Settings > Security to add one"))
                BiometricAvailability.Unavailable ->
                    emitEvent(ProfileUiEvent.BiometricUnavailable("Biometric authentication is unavailable"))
            }
        } else {
            authPreferences.biometricEnabled = false
            _uiState.update { it.copy(biometricEnabled = false) }
        }
    }

    fun requestLogout() {
        _uiState.update { it.copy(showLogoutDialog = true) }
    }

    fun dismissLogoutDialog() {
        _uiState.update { it.copy(showLogoutDialog = false) }
    }

    fun confirmLogout() {
        authPreferences.clear()
        emitEvent(ProfileUiEvent.LogoutComplete)
    }

    private fun emitEvent(event: ProfileUiEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
