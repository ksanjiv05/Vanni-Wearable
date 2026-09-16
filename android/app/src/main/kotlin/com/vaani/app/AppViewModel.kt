package com.vaani.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "vaani_app")
private val KEY_ONBOARDED = booleanPreferencesKey("onboarding_complete")

/** Onboarding state: null=loading, true=done, false=show onboarding. Persisted so
 * "I'll set up later" sticks across launches instead of reappearing every time. */
@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val onboarded: StateFlow<Boolean?> =
        context.appDataStore.data
            .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
            .map { it[KEY_ONBOARDED] ?: false }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun completeOnboarding() {
        viewModelScope.launch {
            context.appDataStore.edit { it[KEY_ONBOARDED] = true }
        }
    }
}
