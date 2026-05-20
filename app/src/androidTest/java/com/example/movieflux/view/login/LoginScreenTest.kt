package com.example.movieflux.view.login

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.movieflux.MainActivity
import com.example.movieflux.R
import com.example.movieflux.data.preferences.AuthPreferences
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * End-to-end UI test for the login flow against the real [MainActivity] graph (Hilt-wired).
 *
 * Field-level and credential-validation assertions here are deterministic. The biometric
 * opt-in dialog itself is covered in isolation by [BiometricOptInDialogTest]; the dialog's
 * appearance after login depends on emulator biometric enrollment, so the post-login test
 * only asserts the deterministic outcome: the login form is left behind (dialog OR navigation).
 */
@HiltAndroidTest
class LoginScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var authPreferences: AuthPreferences

    private fun submitButtonLabel() =
        composeTestRule.activity.getString(R.string.login_submit_button)

    @Before
    fun setUp() {
        hiltRule.inject()
        // The activity launches (and reads AuthPreferences) before this runs, so clear the
        // persisted auth state and recreate the activity. This makes the test hermetic: with no
        // persisted login or enabled biometric, MainActivity always starts at the login form,
        // regardless of prior runs or the device's biometric enrollment.
        authPreferences.clear()
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("username").assertIsDisplayed()
    }

    @Test
    fun submitButton_isDisabled_whenFieldsAreEmpty() {
        composeTestRule.onNodeWithText(submitButtonLabel()).assertIsNotEnabled()
    }

    @Test
    fun submitButton_isDisabled_whenOnlyUsernameIsFilled() {
        composeTestRule.onNodeWithTag("username").performTextInput("admin")
        composeTestRule.onNodeWithText(submitButtonLabel()).assertIsNotEnabled()
    }

    @Test
    fun submitButton_becomesEnabled_whenBothFieldsAreFilled() {
        composeTestRule.onNodeWithTag("username").performTextInput("admin")
        composeTestRule.onNodeWithTag("password").performTextInput("1234")
        composeTestRule.onNodeWithText(submitButtonLabel()).assertIsEnabled()
    }

    @Test
    fun invalidCredentials_showErrorMessage() {
        composeTestRule.onNodeWithTag("username").performTextInput("admin")
        composeTestRule.onNodeWithTag("password").performTextInput("wrong-password")
        composeTestRule.onNodeWithText(submitButtonLabel()).performClick()

        val errorText = composeTestRule.activity.getString(R.string.login_error_invalid_credentials)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(errorText).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(errorText).assertIsDisplayed()

        // The user remains on the login form after a failed attempt.
        composeTestRule.onNodeWithTag("username").assertIsDisplayed()
    }

    @Test
    fun validCredentials_advancePastLoginForm() {
        composeTestRule.onNodeWithTag("username").performTextInput("admin")
        composeTestRule.onNodeWithTag("password").performTextInput("1234")
        composeTestRule.onNodeWithText(submitButtonLabel()).performClick()

        // Deterministic regardless of biometric enrollment: either the opt-in dialog is shown
        // (biometric available) or we navigate away and the login form disappears.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            val dialogShown = composeTestRule
                .onAllNodesWithTag("biometric_opt_in_dialog").fetchSemanticsNodes().isNotEmpty()
            val leftLoginForm = composeTestRule
                .onAllNodesWithTag("username").fetchSemanticsNodes().isEmpty()
            dialogShown || leftLoginForm
        }
    }
}
