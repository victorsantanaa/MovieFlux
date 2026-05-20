package com.example.movieflux.view.login

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.movieflux.R
import com.example.movieflux.ui.theme.MovieFluxTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Isolated test for the stateless [BiometricOptInDialog].
 *
 * Uses [createComposeRule] (no Activity, no Hilt) so it is deterministic and does NOT
 * depend on emulator biometric enrollment — unlike the end-to-end flow in [LoginScreenTest].
 */
class BiometricOptInDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun dialog_rendersTitleMessageAndBothActions() {
        composeTestRule.setContent {
            MovieFluxTheme {
                BiometricOptInDialog(onEnable = {}, onSkip = {})
            }
        }

        composeTestRule.onNodeWithTag("biometric_opt_in_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.biometric_opt_in_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.biometric_opt_in_message)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.biometric_opt_in_enable)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.biometric_opt_in_skip)).assertIsDisplayed()
    }

    @Test
    fun tappingEnable_invokesOnEnableOnly() {
        var enabled = false
        var skipped = false
        composeTestRule.setContent {
            MovieFluxTheme {
                BiometricOptInDialog(onEnable = { enabled = true }, onSkip = { skipped = true })
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.biometric_opt_in_enable)).performClick()

        assertTrue(enabled)
        assertFalse(skipped)
    }

    @Test
    fun tappingSkip_invokesOnSkipOnly() {
        var enabled = false
        var skipped = false
        composeTestRule.setContent {
            MovieFluxTheme {
                BiometricOptInDialog(onEnable = { enabled = true }, onSkip = { skipped = true })
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.biometric_opt_in_skip)).performClick()

        assertTrue(skipped)
        assertFalse(enabled)
    }
}
