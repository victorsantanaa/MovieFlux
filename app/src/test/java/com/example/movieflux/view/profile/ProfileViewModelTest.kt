package com.example.movieflux.view.profile

import android.content.Context
import app.cash.turbine.test
import com.example.movieflux.analytics.AnalyticsTracker
import com.example.movieflux.data.biometric.BiometricAvailability
import com.example.movieflux.data.biometric.BiometricHelper
import com.example.movieflux.data.preferences.AuthPreferences
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val authPreferences: AuthPreferences = mockk(relaxed = true)
    private val biometricHelper: BiometricHelper = mockk()
    private val tracker: AnalyticsTracker = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private fun buildViewModel(): ProfileViewModel {
        every { biometricHelper.canAuthenticate(any()) } returns BiometricAvailability.Available
        every { authPreferences.biometricEnabled } returns false
        return ProfileViewModel(authPreferences, biometricHelper, tracker, context)
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    // ── setBiometricEnabled(true) when Available → persists ──────────────────

    @Test
    fun `setBiometricEnabled true when Available writes to AuthPreferences`() {
        val vm = buildViewModel()
        every { authPreferences.biometricEnabled = true } just runs

        vm.setBiometricEnabled(true)

        verify { authPreferences.biometricEnabled = true }
        assertTrue(vm.uiState.value.biometricEnabled)
    }

    // ── setBiometricEnabled(true) when NoneEnrolled → event, no write ────────

    @Test
    fun `setBiometricEnabled true when NoneEnrolled emits BiometricUnavailable and does not write`() = runTest {
        val vm = buildViewModel()
        every { biometricHelper.canAuthenticate(any()) } returns BiometricAvailability.NoneEnrolled

        vm.events.test {
            vm.setBiometricEnabled(true)

            val event = awaitItem()
            assertTrue(event is ProfileUiEvent.BiometricUnavailable)
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 0) { authPreferences.biometricEnabled = true }
    }

    // ── setBiometricEnabled(false) → always writes false ─────────────────────

    @Test
    fun `setBiometricEnabled false disables biometric and writes to prefs`() {
        val vm = buildViewModel()
        every { authPreferences.biometricEnabled } returns true
        every { authPreferences.biometricEnabled = false } just runs

        vm.setBiometricEnabled(false)

        verify { authPreferences.biometricEnabled = false }
        assertFalse(vm.uiState.value.biometricEnabled)
    }

    // ── confirmLogout → clears prefs and emits LogoutComplete ────────────────

    @Test
    fun `confirmLogout calls authPreferences clear and emits LogoutComplete`() = runTest {
        val vm = buildViewModel()

        vm.events.test {
            vm.confirmLogout()

            val event = awaitItem()
            assertTrue(event is ProfileUiEvent.LogoutComplete)
            cancelAndIgnoreRemainingEvents()
        }

        verify { authPreferences.clear() }
    }

    // ── requestLogout / dismissLogoutDialog ───────────────────────────────────

    @Test
    fun `requestLogout sets showLogoutDialog to true`() {
        val vm = buildViewModel()
        vm.requestLogout()
        assertTrue(vm.uiState.value.showLogoutDialog)
    }

    @Test
    fun `dismissLogoutDialog sets showLogoutDialog to false`() {
        val vm = buildViewModel()
        vm.requestLogout()
        vm.dismissLogoutDialog()
        assertFalse(vm.uiState.value.showLogoutDialog)
    }

    // ── init tracks profile screen ────────────────────────────────────────────

    @Test
    fun `init tracks profile screen`() {
        buildViewModel()
        verify { tracker.trackScreen("profile") }
    }
}
