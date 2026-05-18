package com.example.movieflux.data.preferences

import android.content.SharedPreferences
import com.example.movieflux.view.components.ViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class UiPreferencesTest {

    private class FakeSharedPreferences : SharedPreferences {
        val data = mutableMapOf<String, String?>()

        override fun getString(key: String, defValue: String?) = data[key] ?: defValue
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putString(key: String, value: String?) = apply { data[key] = value }
            override fun apply() {}
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
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {}
    }

    private fun uiPrefs(prefs: SharedPreferences = FakeSharedPreferences()) = UiPreferences(prefs)

    @Test
    fun `default returns GRID for both screens when nothing stored`() {
        val sut = uiPrefs()
        assertEquals(ViewMode.GRID, sut.getHomeViewMode())
        assertEquals(ViewMode.GRID, sut.getFavoritesViewMode())
    }

    @Test
    fun `setHomeViewMode then getHomeViewMode returns the stored value`() {
        val sut = uiPrefs()
        sut.setHomeViewMode(ViewMode.LIST)
        assertEquals(ViewMode.LIST, sut.getHomeViewMode())
    }

    @Test
    fun `setFavoritesViewMode does NOT affect getHomeViewMode and vice versa`() {
        val sut = uiPrefs()
        sut.setFavoritesViewMode(ViewMode.LIST)
        assertEquals(ViewMode.GRID, sut.getHomeViewMode())

        sut.setHomeViewMode(ViewMode.LIST)
        sut.setFavoritesViewMode(ViewMode.GRID)
        assertEquals(ViewMode.LIST, sut.getHomeViewMode())
        assertEquals(ViewMode.GRID, sut.getFavoritesViewMode())
    }

    @Test
    fun `corrupt stored value falls back to GRID without throwing`() {
        val fakePrefs = FakeSharedPreferences().also { it.data["view_mode_home"] = "NOT_A_VALID_ENUM" }
        val sut = uiPrefs(fakePrefs)
        assertEquals(ViewMode.GRID, sut.getHomeViewMode())
    }
}
