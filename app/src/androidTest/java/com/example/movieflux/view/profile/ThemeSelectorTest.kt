package com.example.movieflux.view.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.movieflux.R
import com.example.movieflux.ui.theme.MovieFluxTheme
import com.example.movieflux.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Isolated tests for the Profile screen's tri-state [ThemeSelector]. Hoisted state is driven
 * directly so the three options and selection callbacks are verified without Hilt or DataStore.
 */
class ThemeSelectorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun rendersAllThreeOptions_withCurrentModeSelected() {
        composeTestRule.setContent {
            MovieFluxTheme { ThemeSelector(themeMode = ThemeMode.SYSTEM, onModeChange = {}) }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.theme_system)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.theme_light)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.theme_dark)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.theme_system)).assertIsSelected()
    }

    @Test
    fun selectingDark_emitsDarkAndUpdatesSelection() {
        var mode by mutableStateOf(ThemeMode.SYSTEM)
        val emitted = mutableListOf<ThemeMode>()
        composeTestRule.setContent {
            MovieFluxTheme {
                ThemeSelector(
                    themeMode = mode,
                    onModeChange = {
                        mode = it
                        emitted += it
                    }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.theme_dark)).performClick()

        assertEquals(ThemeMode.DARK, emitted.last())
        composeTestRule.onNodeWithText(context.getString(R.string.theme_dark)).assertIsSelected()
    }
}
