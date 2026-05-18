package com.example.movieflux.data.preferences

import android.content.SharedPreferences
import com.example.movieflux.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class UiPreferencesThemeTest {

    private class FakeSharedPreferences : SharedPreferences {
        val data = mutableMapOf<String, String?>()

        override fun getString(key: String, defValue: String?) = data[key] ?: defValue
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putString(key: String, value: String?) = apply { data[key] = value }
            override fun apply() = Unit
            override fun commit() = true
            override fun clear() = apply { data.clear() }
            override fun remove(key: String) = apply { data.remove(key) }
            override fun putBoolean(key: String, value: Boolean) = this
            override fun putInt(key: String, value: Int) = this
            override fun putLong(key: String, value: Long) = this
            override fun putFloat(key: String, value: Float) = this
            override fun putStringSet(key: String, values: MutableSet<String>?) = this
        }

        override fun getAll(): Map<String, *> = data.toMap()
        override fun getBoolean(key: String, defValue: Boolean) = defValue
        override fun getInt(key: String, defValue: Int) = defValue
        override fun getLong(key: String, defValue: Long) = defValue
        override fun getFloat(key: String, defValue: Float) = defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
        override fun contains(key: String) = data.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(
            l: SharedPreferences.OnSharedPreferenceChangeListener,
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            l: SharedPreferences.OnSharedPreferenceChangeListener,
        ) = Unit
    }

    private fun uiPrefs(prefs: SharedPreferences = FakeSharedPreferences()) = UiPreferences(prefs)

    @Test
    fun `default returns SYSTEM`() {
        assertEquals(ThemeMode.SYSTEM, uiPrefs().getThemeMode())
    }

    @Test
    fun `setThemeMode then getThemeMode returns stored value`() {
        val sut = uiPrefs()
        sut.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, sut.getThemeMode())
    }

    @Test
    fun `corrupt stored value falls back to SYSTEM without throwing`() {
        val fakePrefs = FakeSharedPreferences().also { it.data["theme_mode"] = "NOT_VALID" }
        assertEquals(ThemeMode.SYSTEM, uiPrefs(fakePrefs).getThemeMode())
    }

    @Test
    fun `theme key is independent from view mode keys`() {
        val sut = uiPrefs()
        sut.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, sut.getThemeMode())
        // view mode keys must be unaffected
        assertEquals(com.example.movieflux.view.components.ViewMode.GRID, sut.getHomeViewMode())
        assertEquals(com.example.movieflux.view.components.ViewMode.GRID, sut.getFavoritesViewMode())
    }
}
