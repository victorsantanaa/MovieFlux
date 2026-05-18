package com.example.movieflux.view.login

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.movieflux.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class LoginScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun login_with_valid_credentials_shows_biometric_opt_in_dialog() {
        composeTestRule.onNodeWithTag("username").performTextInput("admin")
        composeTestRule.onNodeWithTag("password").performTextInput("1234")
        composeTestRule.onNodeWithText("Done").performClick()
        composeTestRule.onNodeWithText("Enable biometric login?").assertIsDisplayed()
    }
}
