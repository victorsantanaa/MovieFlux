package com.example.movieflux.data.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.movieflux.R
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed class BiometricAvailability {
    object Available : BiometricAvailability()
    object NoHardware : BiometricAvailability()
    object NoneEnrolled : BiometricAvailability()
    object Unavailable : BiometricAvailability()
}

@Singleton
class BiometricHelper @Inject constructor(@ApplicationContext private val appContext: Context) {

    fun canAuthenticate(): BiometricAvailability {
        val manager = BiometricManager.from(appContext)
        return when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.NoHardware
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NoneEnrolled
            else -> BiometricAvailability.Unavailable
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
            override fun onAuthenticationFailed() {
                Timber.d("Biometric attempt failed (transient)")
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_prompt_title))
            .setSubtitle(activity.getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(activity.getString(R.string.action_cancel))
            .build()
        prompt.authenticate(info)
    }
}
