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

    // Lazy para que o lento EncryptedSharedPreferences.create() (apoiado pelo Keystore) não rode na
    // thread que faz a injeção (tipicamente a main thread). O modo SYNCHRONIZED garante que o primeiro
    // acesso o construa exatamente uma vez; os chamadores devem aquecê-lo fora da main thread via
    // awaitReady() no startup.
    private val prefs: SharedPreferences by lazy { createEncryptedPrefs(context) }

    /**
     * Força [prefs] a ser construído fora da main thread. Chame uma vez no startup do app (antes de
     * ler qualquer flag de auth) para que o trabalho de Keystore/cripto/disco nunca bloqueie a UI
     * thread nem corra risco de ANR.
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
            // A master key do Keystore não casa mais com o keyset armazenado (ex.: backup/restore
            // para uma nova instalação, ou reset do Keystore) → AEADBadTagException. Os dados
            // criptografados são irrecuperáveis, então apagamos o arquivo corrompido e recriamos um
            // keyset novo em vez de quebrar no startup. O usuário simplesmente é deslogado uma vez.
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
