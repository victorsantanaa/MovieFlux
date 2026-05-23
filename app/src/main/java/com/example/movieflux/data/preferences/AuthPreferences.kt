package com.example.movieflux.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    // Lazy so the slow, Keystore-backed EncryptedSharedPreferences.create() doesn't run on whatever
    // thread injects this (typically the main thread). SYNCHRONIZED mode makes the first access build
    // it exactly once; callers should warm it off the main thread via awaitReady() at startup.
    private val prefs: SharedPreferences by lazy { createEncryptedPrefs(context) }

    /**
     * Forces [prefs] to be built off the main thread. Call once at app startup (before reading any
     * auth flag) so the Keystore/crypto/disk work never blocks the UI thread and risks an ANR.
     */
    suspend fun awaitReady() {
        withContext(Dispatchers.IO) { prefs }
    }

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
