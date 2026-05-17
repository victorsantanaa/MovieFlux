package com.example.movieflux.view.biometric

sealed class BiometricGateState {
    object Checking : BiometricGateState()
    object Passed : BiometricGateState()
    data class Failed(val message: String) : BiometricGateState()
}
