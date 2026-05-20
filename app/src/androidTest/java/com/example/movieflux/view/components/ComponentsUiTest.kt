package com.example.movieflux.view.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.movieflux.R
import com.example.movieflux.ui.theme.MovieFluxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Isolated Compose UI tests for the stateless shared components used across Home, Favorites,
 * and Profile. Each test drives the hoisted state/callbacks directly with [createComposeRule]
 * (no Activity, no Hilt, no network), so they are deterministic and fast.
 */
class ComponentsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun emptyView_rendersTitleAndSubtitle() {
        composeTestRule.setContent {
            MovieFluxTheme { EmptyView(title = "No favorites", subtitle = "Tap a heart") }
        }

        composeTestRule.onNodeWithText("No favorites").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap a heart").assertIsDisplayed()
    }

    @Test
    fun errorView_showsMessageAndRetryInvokesCallback() {
        var retried = false
        composeTestRule.setContent {
            MovieFluxTheme { ErrorView(message = "Network down", onRetry = { retried = true }) }
        }

        composeTestRule.onNodeWithText("Network down").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_retry)).performClick()

        assertTrue(retried)
    }

    @Test
    fun searchBar_typingEmitsQueryAndClearButtonResetsIt() {
        val changes = mutableListOf<String>()
        composeTestRule.setContent {
            var query by remember { mutableStateOf("") }
            MovieFluxTheme {
                SearchBar(
                    query = query,
                    onQueryChange = {
                        query = it
                        changes += it
                    }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.search_movies_placeholder))
            .performTextInput("matrix")
        assertEquals("matrix", changes.last())

        // Clear button appears only when the query is non-empty; tapping it emits "".
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.cd_clear_search))
            .performClick()
        assertEquals("", changes.last())
    }

    @Test
    fun settingsSwitchRow_reflectsStateAndTogglesValue() {
        var checked by mutableStateOf(false)
        composeTestRule.setContent {
            MovieFluxTheme {
                SettingsSwitchRow(
                    icon = Icons.Default.Fingerprint,
                    title = "Biometric login",
                    subtitle = "Use your fingerprint",
                    checked = checked,
                    onCheckedChange = { checked = it }
                )
            }
        }

        composeTestRule.onNode(isToggleable()).assertIsOff()
        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun viewModeToggle_inGridMode_offersListAndEmitsListOnClick() {
        var toggledTo: ViewMode? = null
        composeTestRule.setContent {
            MovieFluxTheme {
                ViewModeToggle(current = ViewMode.GRID, onToggle = { toggledTo = it })
            }
        }

        composeTestRule.onNodeWithContentDescription("Switch to list view").performClick()
        assertEquals(ViewMode.LIST, toggledTo)
    }

    @Test
    fun logoutConfirmDialog_confirmAndCancelInvokeCorrectCallbacks() {
        var confirmed = false
        var dismissed = false
        composeTestRule.setContent {
            MovieFluxTheme {
                LogoutConfirmDialog(onConfirm = { confirmed = true }, onDismiss = { dismissed = true })
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.logout_dialog_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.profile_logout)).performClick()
        assertTrue(confirmed)

        composeTestRule.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        assertTrue(dismissed)
    }
}
