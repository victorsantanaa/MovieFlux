package com.example.movieflux.data.preferences

// TODO Phase 2: replace with EncryptedSharedPreferences (AES256_SIV / AES256_GCM)
class AuthPreferences {
    var isLoggedIn: Boolean = false
    var biometricEnabled: Boolean = false

    fun clear() {
        isLoggedIn = false
        biometricEnabled = false
    }
}
