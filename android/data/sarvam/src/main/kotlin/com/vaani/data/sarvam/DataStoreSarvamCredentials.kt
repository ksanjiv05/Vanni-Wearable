package com.vaani.data.sarvam

import com.vaani.data.ai.EncryptedSecrets
import com.vaani.domain.ai.SarvamCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore-encrypted [SarvamCredentials]. The API key is stored via
 * [EncryptedSecrets] (EncryptedSharedPreferences, master key in the Android
 * Keystore) so a live paid key is encrypted at rest rather than sitting in
 * plaintext DataStore. Returns null when unset so engines fail with KeyMissing.
 * `hasKey()` is backed by an in-memory StateFlow seeded from the encrypted store.
 */
@Singleton
class DataStoreSarvamCredentials @Inject constructor(
    private val secrets: EncryptedSecrets,
) : SarvamCredentials {

    private val present = MutableStateFlow(secrets.has(KEY_API))

    override suspend fun apiKey(): String? = secrets.get(KEY_API)

    override fun hasKey(): Flow<Boolean> = present.asStateFlow()

    override suspend fun setApiKey(key: String) {
        secrets.set(KEY_API, key)
        present.value = secrets.has(KEY_API)
    }

    private companion object {
        const val KEY_API = "sarvam_api_key"
    }
}
