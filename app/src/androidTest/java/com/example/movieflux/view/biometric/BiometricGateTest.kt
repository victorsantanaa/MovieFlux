package com.example.movieflux.view.biometric

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * Isolated tests for [BiometricGate] — drives each [BiometricGateState] directly so the
 * gate's branching (defer content while Checking, fallback UI on Failed) is verified without
 * touching real biometric hardware.
 */
class BiometricGateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun passedState_rendersGatedContent() {
        composeTestRule.setContent {
            MovieFluxTheme {
                BiometricGate(
                    state = BiometricGateState.Passed,
                    onRetry = {},
                    onUsePassword = {},
                    onResolved = {},
                    authenticate = { _, _ -> }
                ) { Text("PROTECTED_CONTENT") }
            }
        }

        composeTestRule.onNodeWithText("PROTECTED_CONTENT").assertIsDisplayed()
    }

    @Test
    fun checkingState_doesNotRenderContentButTriggersAuthenticate() {
        var authenticateCalled = false
        composeTestRule.setContent {
            MovieFluxTheme {
                BiometricGate(
                    state = BiometricGateState.Checking,
                    onRetry = {},
                    onUsePassword = {},
                    onResolved = {},
                    authenticate = { _, _ -> authenticateCalled = true }
                ) { Text("PROTECTED_CONTENT") }
            }
        }

        composeTestRule.waitForIdle()
        assertTrue(authenticateCalled)
        composeTestRule.onAllNodesWithText("PROTECTED_CONTENT").fetchSemanticsNodes().let {
            assertTrue("Content must not render while Checking", it.isEmpty())
        }
    }

    @Test
    fun failedState_showsFallbackUiAndInvokesCallbacks() {
        var usedPassword = false
        var retried = false
        composeTestRule.setContent {
            MovieFluxTheme {
                BiometricGate(
                    state = BiometricGateState.Failed("Too many attempts"),
                    onRetry = { retried = true },
                    onUsePassword = { usedPassword = true },
                    onResolved = {},
                    authenticate = { _, _ -> }
                ) { Text("PROTECTED_CONTENT") }
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.biometric_failed_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Too many attempts").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("PROTECTED_CONTENT").fetchSemanticsNodes().let {
            assertTrue("Content must not render while Failed", it.isEmpty())
        }

        composeTestRule.onNodeWithText(context.getString(R.string.biometric_use_password)).performClick()
        assertTrue(usedPassword)
        assertFalse(retried)

        composeTestRule.onNodeWithText(context.getString(R.string.biometric_retry)).performClick()
        assertTrue(retried)
    }
}
