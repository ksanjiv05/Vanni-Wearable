package com.vaani.app.audio

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Drives the audio-import action from the UI and surfaces a transient result message. */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importer: AudioImporter,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun import(uri: Uri) {
        _message.value = "Importing audio…"
        viewModelScope.launch {
            runCatching { importer.import(uri) }
                .onSuccess { _message.value = "Imported \"${it.displayName}\" — transcription queued." }
                .onFailure { _message.value = "Import failed: ${it.message}" }
        }
    }

    fun consumeMessage() { _message.value = null }
}
