package com.vaani.data.ai

import com.vaani.domain.ai.HfCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore-encrypted [HfCredentials]. The token is stored via [EncryptedSecrets]
 * (EncryptedSharedPreferences, master key in the Android Keystore) so it is
 * encrypted at rest, and used ONLY as the Authorization header on huggingface.co
 * downloads. `hasToken()` is backed by an in-memory StateFlow (seeded from the
 * encrypted store) since EncryptedSharedPreferences is not itself reactive.
 */
@Singleton
class DataStoreHfCredentials @Inject constructor(
    private val secrets: EncryptedSecrets,
) : HfCredentials {

    private val present = MutableStateFlow(secrets.has(KEY))

    override suspend fun token(): String? = secrets.get(KEY)

    override fun hasToken(): Flow<Boolean> = present.asStateFlow()

    override suspend fun setToken(token: String) {
        secrets.set(KEY, token)
        present.value = secrets.has(KEY)
    }

    private companion object {
        const val KEY = "hf_token"
    }
}
