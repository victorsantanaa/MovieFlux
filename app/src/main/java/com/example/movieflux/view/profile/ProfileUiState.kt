package com.example.movieflux.view.profile

import com.example.movieflux.ui.theme.ThemeMode

data class ProfileUiState(
    val username: String = "admin",
    val email: String = "admin@movieflux.app",
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val appVersion: String = "",
    val showLogoutDialog: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)
