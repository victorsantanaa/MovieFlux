package com.example.movieflux.data.biometric

// TODO Phase 2: replace with real BiometricManager / BiometricPrompt implementation
sealed class BiometricAvailability {
    object Available : BiometricAvailability()
    object NoHardware : BiometricAvailability()
    object NoneEnrolled : BiometricAvailability()
    object Unavailable : BiometricAvailability()
}

class BiometricHelper {
    fun canAuthenticate(): BiometricAvailability = BiometricAvailability.Unavailable
}
