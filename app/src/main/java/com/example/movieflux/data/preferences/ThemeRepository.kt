package com.example.movieflux.data.preferences

import com.example.movieflux.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeRepository @Inject constructor(private val uiPreferences: UiPreferences) {

    private val _themeMode = MutableStateFlow(uiPreferences.getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        uiPreferences.setThemeMode(mode)
        _themeMode.value = mode
    }
}
