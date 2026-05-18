package com.example.movieflux.data.preferences

import app.cash.turbine.test
import com.example.movieflux.ui.theme.ThemeMode
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeRepositoryTest {

    private val uiPreferences: UiPreferences = mockk()

    private fun buildRepo(initial: ThemeMode = ThemeMode.SYSTEM): ThemeRepository {
        every { uiPreferences.getThemeMode() } returns initial
        every { uiPreferences.setThemeMode(any()) } just runs
        return ThemeRepository(uiPreferences)
    }

    @Test
    fun `seeded with current theme from UiPreferences`() {
        val repo = buildRepo(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.themeMode.value)
    }

    @Test
    fun `setThemeMode persists then publishes new value`() {
        val repo = buildRepo()
        repo.setThemeMode(ThemeMode.LIGHT)
        verifyOrder {
            uiPreferences.setThemeMode(ThemeMode.LIGHT)
        }
        assertEquals(ThemeMode.LIGHT, repo.themeMode.value)
    }

    @Test
    fun `second collector receives new value`() = runTest {
        val repo = buildRepo()
        repo.themeMode.test {
            assertEquals(ThemeMode.SYSTEM, awaitItem())
            repo.setThemeMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
