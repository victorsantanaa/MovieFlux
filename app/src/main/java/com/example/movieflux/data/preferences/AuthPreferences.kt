package com.example.movieflux.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthPreferences @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = createEncryptedPrefs(context)

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return try {
            buildPrefs(context, masterKey)
        } catch (e: GeneralSecurityException) {
            // The Keystore master key no longer matches the stored keyset (e.g. backup/restore to a
            // new install, or a Keystore reset) → AEADBadTagException. The encrypted data is
            // unrecoverable, so wipe the corrupt file and recreate a fresh keyset rather than
            // crashing at startup. The user is simply logged out once.
            recreateAfterCorruption(context, masterKey, e)
        } catch (e: IOException) {
            recreateAfterCorruption(context, masterKey, e)
        }
    }

    private fun recreateAfterCorruption(
        context: Context,
        masterKey: MasterKey,
        cause: Exception
    ): SharedPreferences {
        @Suppress("DEPRECATION")
        if (!context.deleteSharedPreferences(PREFS_NAME)) {
            // Fallback for very old behavior: clear in place if the file couldn't be deleted.
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        }
        return try {
            buildPrefs(context, masterKey)
        } catch (e: Exception) {
            e.addSuppressed(cause)
            throw e
        }
    }

    private fun buildPrefs(context: Context, masterKey: MasterKey): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var biometricPrompted: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_PROMPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_PROMPTED, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_BIOMETRIC_PROMPTED = "biometric_prompted"
    }
}
