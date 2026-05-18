package com.example.movieflux.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.movieflux.ui.theme.ThemeMode
import com.example.movieflux.view.components.ViewMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UiPreferences internal constructor(private val prefs: SharedPreferences) {

    @Inject
    constructor(@ApplicationContext context: Context) :
        this(context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE))

    companion object {
        private const val KEY_VIEW_MODE_HOME = "view_mode_home"
        private const val KEY_VIEW_MODE_FAVORITES = "view_mode_favorites"
        private const val KEY_THEME_MODE = "theme_mode"
    }

    private fun readViewMode(key: String): ViewMode =
        runCatching { ViewMode.valueOf(prefs.getString(key, null) ?: return@runCatching ViewMode.GRID) }
            .getOrDefault(ViewMode.GRID)

    private fun writeViewMode(key: String, mode: ViewMode) {
        prefs.edit().putString(key, mode.name).apply()
    }

    fun getHomeViewMode(): ViewMode = readViewMode(KEY_VIEW_MODE_HOME)
    fun setHomeViewMode(mode: ViewMode) = writeViewMode(KEY_VIEW_MODE_HOME, mode)
    fun getFavoritesViewMode(): ViewMode = readViewMode(KEY_VIEW_MODE_FAVORITES)
    fun setFavoritesViewMode(mode: ViewMode) = writeViewMode(KEY_VIEW_MODE_FAVORITES, mode)

    fun getThemeMode(): ThemeMode =
        runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, null) ?: return@runCatching ThemeMode.SYSTEM)
        }.getOrDefault(ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }
}
