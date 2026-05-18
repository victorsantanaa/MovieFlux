package com.example.movieflux

import app.cash.turbine.test
import com.example.movieflux.analytics.AnalyticsTracker
import com.example.movieflux.analytics.FunnelTracker
import com.example.movieflux.data.biometric.BiometricAvailability
import com.example.movieflux.data.biometric.BiometricHelper
import com.example.movieflux.data.preferences.AuthPreferences
import com.example.movieflux.view.login.LoginUiState
import com.example.movieflux.view.login.LoginViewModel
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val authPreferences: AuthPreferences = mockk(relaxed = true)
    private val biometricHelper: BiometricHelper = mockk()
    private val tracker: AnalyticsTracker = mockk(relaxed = true)
    private val funnel: FunnelTracker = mockk(relaxed = true)

    private lateinit var viewModel: LoginViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { biometricHelper.canAuthenticate() } returns BiometricAvailability.Unavailable
        every { authPreferences.biometricPrompted } returns false
        viewModel = LoginViewModel(authPreferences, biometricHelper, tracker, funnel)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `login with valid credentials emits Success and sets isLoggedIn`() = runTest {
        viewModel.uiState.test {
            assertEquals(LoginUiState.Idle, awaitItem())
            viewModel.login("admin", "1234")
            assertEquals(LoginUiState.Loading, awaitItem())
            val success = awaitItem()
            assertTrue(success is LoginUiState.Success)
        }
        verify { authPreferences.isLoggedIn = true }
    }

    @Test
    fun `login with invalid credentials emits Error`() = runTest {
        viewModel.uiState.test {
            assertEquals(LoginUiState.Idle, awaitItem())
            viewModel.login("admin", "wrong")
            assertEquals(LoginUiState.Loading, awaitItem())
            val error = awaitItem()
            assertTrue(error is LoginUiState.Error)
            assertEquals("Invalid credentials", (error as LoginUiState.Error).message)
        }
    }

    @Test
    fun `init tracks login screen`() {
        verify { tracker.trackScreen("login") }
    }

    @Test
    fun `login success completes auth funnel`() = runTest {
        viewModel.login("admin", "1234")
        verify { funnel.start("auth") }
        verify { funnel.step("auth", "login_clicked") }
        verify { funnel.step("auth", "login_success") }
    }

    @Test
    fun `login failure abandons auth funnel`() = runTest {
        viewModel.login("admin", "wrong")
        verify { funnel.abandon("auth", "invalid_credentials") }
    }

    @Test
    fun `login success shows biometric prompt when available and not yet prompted`() = runTest {
        every { biometricHelper.canAuthenticate() } returns BiometricAvailability.Available
        every { authPreferences.biometricPrompted } returns false

        viewModel.uiState.test {
            awaitItem() // Idle
            viewModel.login("admin", "1234")
            awaitItem() // Loading
            val success = awaitItem() as LoginUiState.Success
            assertTrue(success.shouldPromptBiometric)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `login success when biometricPrompted already true emits Success with shouldPromptBiometric false`() = runTest {
        every { biometricHelper.canAuthenticate() } returns BiometricAvailability.Available
        every { authPreferences.biometricPrompted } returns true

        viewModel.uiState.test {
            awaitItem() // Idle
            viewModel.login("admin", "1234")
            awaitItem() // Loading
            val success = awaitItem() as LoginUiState.Success
            assertFalse(success.shouldPromptBiometric)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `login success when biometric unavailable emits Success with shouldPromptBiometric false`() = runTest {
        every { biometricHelper.canAuthenticate() } returns BiometricAvailability.Unavailable
        every { authPreferences.biometricPrompted } returns false

        viewModel.uiState.test {
            awaitItem() // Idle
            viewModel.login("admin", "1234")
            awaitItem() // Loading
            val success = awaitItem() as LoginUiState.Success
            assertFalse(success.shouldPromptBiometric)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmBiometricOptIn true sets both prefs to true`() {
        every { authPreferences.biometricEnabled = true } just runs
        every { authPreferences.biometricPrompted = true } just runs

        viewModel.confirmBiometricOptIn(true)

        verify { authPreferences.biometricEnabled = true }
        verify { authPreferences.biometricPrompted = true }
    }

    @Test
    fun `confirmBiometricOptIn false sets only prompted true`() {
        every { authPreferences.biometricEnabled = false } just runs
        every { authPreferences.biometricPrompted = true } just runs

        viewModel.confirmBiometricOptIn(false)

        verify { authPreferences.biometricEnabled = false }
        verify { authPreferences.biometricPrompted = true }
    }
}
