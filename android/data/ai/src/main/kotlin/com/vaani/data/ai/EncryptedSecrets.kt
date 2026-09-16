package com.vaani.data.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore-backed encrypted store for the app's secrets (Sarvam API key, Hugging
 * Face token). Uses [EncryptedSharedPreferences] whose master key lives in the
 * Android Keystore (hardware-backed where available), so values are encrypted at
 * rest — a rooted device / filesystem dump / adb-backup no longer yields the
 * plaintext key that the old DataStore stored in the clear.
 *
 * If the encrypted store can't be opened (rare keystore corruption after a
 * restore), it self-heals by clearing the file and recreating it — the user
 * re-enters the key rather than the app crash-looping.
 */
@Singleton
class EncryptedSecrets @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy { open() }

    private fun open(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return runCatching { create(masterKey) }.getOrElse {
            // Corrupt keyset (e.g. after a device-transfer restore): reset and retry once.
            context.deleteSharedPreferences(FILE)
            create(masterKey)
        }
    }

    private fun create(masterKey: MasterKey): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    fun get(key: String): String? = prefs.getString(key, null)?.takeIf { it.isNotBlank() }

    fun set(key: String, value: String?) {
        prefs.edit().apply {
            val v = value?.trim()
            if (v.isNullOrBlank()) remove(key) else putString(key, v)
        }.apply()
    }

    fun has(key: String): Boolean = !prefs.getString(key, null).isNullOrBlank()

    private companion object {
        const val FILE = "vaani_secrets"
    }
}
